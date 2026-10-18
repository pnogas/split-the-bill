package dev.splitthebill.shared

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ReceiptParserTest {
    @Test
    fun canadianRestaurantReceiptWithHstTipAndEndOfLinePrices() {
        val parsed = parseReceipt(
            stacked(
                "PHO LIEN",
                "123 Spadina Ave",
                "Toronto ON M5V 2H1",
                "(416) 555-0199",
                "Oct 9, 2026  7:42 PM",
                "Server: Maya",
                "HST # 123456789RT0001",
                "1. Rare Beef Pho          16.50",
                "2x Spring Rolls           11.00",
                "Banh Mi                    9.00",
                "Iced Coffee                4.50 T",
                "Subtotal                  41.00",
                "HST (13%)                  5.33",
                "Tip                        6.00",
                "Total                     52.33",
                "Auth code 839271",
                "VISA **** 4242",
                "Thank you!",
            ),
        )

        assertEquals("PHO LIEN", parsed.merchant)
        assertEquals(
            listOf(
                ParsedItem("Rare Beef Pho", Cents(1_650), 1),
                ParsedItem("Spring Rolls", Cents(550), 2),
                ParsedItem("Banh Mi", Cents(900), 1),
                ParsedItem("Iced Coffee", Cents(450), 1),
            ),
            parsed.items,
        )
        assertEquals(Cents(4_100), parsed.subtotal)
        assertEquals(Cents(533), parsed.tax)
        assertEquals(Cents(600), parsed.tip)
        assertEquals(Cents(5_233), parsed.total)
        assertEquals(parsed.subtotal, itemsSum(parsed))
        assertTrue(parsed.lineItems().all { it.sharedBy.isEmpty() })
        assertEquals(Cents(4_100), parsed.lineItems().fold(Cents.Zero) { acc, item -> acc + item.lineTotal })
    }

    @Test
    fun priceColumnOnTheFollowingLineAndSplitGstPst() {
        val parsed = parseReceipt(
            listOf(
                line("SUSHI MASA", top = 10.0, left = 20.0),
                line("45 Queen St W", top = 50.0, left = 20.0),
                line("Toronto ON M5H 2N2", top = 90.0, left = 20.0),
                line("416-555-0148", top = 130.0, left = 20.0),
                line("Sep 27, 2026", top = 170.0, left = 20.0),
                line("Salmon Roll", top = 250.0, left = 20.0),
                line("12.00", top = 292.0, left = 420.0),
                line("Miso Soup", top = 340.0, left = 20.0),
                line("\$3.50", top = 382.0, left = 410.0),
                line("2 @", top = 430.0, left = 20.0),
                line("Green Tea", top = 432.0, left = 90.0),
                line("8.00", top = 434.0, left = 420.0),
                line("Subtotal", top = 500.0, left = 20.0),
                line("31.50", top = 502.0, left = 420.0),
                line("GST 5%", top = 540.0, left = 20.0),
                line("1.58", top = 542.0, left = 420.0),
                line("PST 8%", top = 580.0, left = 20.0),
                line("2.52", top = 582.0, left = 420.0),
                line("Total", top = 640.0, left = 20.0),
                line("35.60", top = 642.0, left = 420.0),
            ),
        )

        assertEquals("SUSHI MASA", parsed.merchant)
        assertEquals(
            listOf(
                ParsedItem("Salmon Roll", Cents(1_200), 1),
                ParsedItem("Miso Soup", Cents(350), 1),
                ParsedItem("Green Tea", Cents(800), 2),
            ),
            parsed.items,
        )
        assertEquals(Cents(3_150), itemsSum(parsed))
        assertEquals(Cents(3_150), parsed.subtotal)
        assertEquals(Cents(410), parsed.tax)
        assertNull(parsed.tip)
        assertEquals(Cents(3_560), parsed.total)
    }

    @Test
    fun sameVisualRowJoinsANameAndARightAlignedPrice() {
        val parsed = parseReceipt(
            listOf(
                line("HARBOUR DINER", top = 10.0),
                line("Caesar Salad", top = 80.0, left = 16.0),
                line("13.00", top = 83.0, left = 360.0),
                line("Soup", top = 130.0, left = 16.0),
                line("\$4.00", top = 132.0, left = 370.0),
            ),
        )

        assertEquals("HARBOUR DINER", parsed.merchant)
        assertEquals(
            listOf(
                ParsedItem("Caesar Salad", Cents(1_300), 1),
                ParsedItem("Soup", Cents(400), 1),
            ),
            parsed.items,
        )
    }

    @Test
    fun quantityMarkersAndUnitTimesQuantityColumn() {
        val parsed = parseReceipt(
            stacked(
                "Qty 3 Samosa   9.00",
                "Pad Thai x2    18.00",
                "Spring Roll  2  4.25  8.50",
                "2 @ Iced Tea  3.00",
            ),
        )

        assertEquals(
            listOf(
                ParsedItem("Samosa", Cents(300), 3),
                ParsedItem("Pad Thai", Cents(900), 2),
                ParsedItem("Spring Roll", Cents(425), 2),
                ParsedItem("Iced Tea", Cents(300), 2),
            ),
            parsed.items,
        )
        assertEquals(Cents(9_00 + 1_800 + 850 + 600), itemsSum(parsed))
    }

    @Test
    fun unevenQuantityKeepsThePrintedLineAmount() {
        val parsed = parseReceipt(stacked("3x Cookie  5.00"))

        assertEquals(listOf(ParsedItem("3x Cookie", Cents(500), 1)), parsed.items)
    }

    @Test
    fun dollarSignsSpacesAndThousandsSeparators() {
        val parsed = parseReceipt(
            stacked(
                "BANQUET HALL",
                "Catering Package    \$ 1,234.50",
                "CA\$ Room fee              75.00",
                "Subtotal             \$1,309.50",
                "GST                   \$65.48",
                "Amount due         \$1,374.98",
            ),
        )

        assertEquals(
            listOf(
                ParsedItem("Catering Package", Cents(123_450), 1),
                ParsedItem("Room fee", Cents(7_500), 1),
            ),
            parsed.items,
        )
        assertEquals(Cents(130_950), parsed.subtotal)
        assertEquals(Cents(6_548), parsed.tax)
        assertEquals(Cents(137_498), parsed.total)
    }

    @Test
    fun discountIsANegativeItemAndSubtotalStillMatches() {
        val parsed = parseReceipt(
            stacked(
                "NOODLE BAR",
                "Pad Thai        14.00",
                "Discount       -\$2.00",
                "Subtotal        12.00",
                "HST              1.56",
                "Total           13.56",
            ),
        )

        assertEquals(
            listOf(
                ParsedItem("Pad Thai", Cents(1_400), 1),
                ParsedItem("Discount", Cents(-200), 1),
            ),
            parsed.items,
        )
        assertEquals(Cents(1_200), itemsSum(parsed))
        assertEquals(parsed.subtotal, itemsSum(parsed))
        assertEquals(Cents(156), parsed.tax)
        assertEquals(Cents(1_356), parsed.total)
    }

    @Test
    fun suggestedTipBlockIsIgnoredAndTheEnteredTipIsKept() {
        val parsed = parseReceipt(
            stacked(
                "LUNCH STOP",
                "Soup and sandwich   20.00",
                "Subtotal            20.00",
                "HST                  2.60",
                "Total               22.60",
                "Suggested Tip",
                "15%                  3.39",
                "18%                  4.07",
                "20%                  4.52",
                "Tip                  4.00",
                "Grand Total         26.60",
            ),
        )

        assertEquals(listOf(ParsedItem("Soup and sandwich", Cents(2_000), 1)), parsed.items)
        assertEquals(Cents(2_000), parsed.subtotal)
        assertEquals(Cents(260), parsed.tax)
        assertEquals(Cents(400), parsed.tip)
        assertEquals(Cents(2_660), parsed.total)
    }

    @Test
    fun noiseLinesDoNotBecomeItems() {
        val parsed = parseReceipt(
            stacked(
                "Call us at 416.555.0199",
                "10/09/2026",
                "hello@pholien.ca",
                "www.pholien.ca",
                "4242 4242 4242 4242",
                "**** 4242",
                "Cash                60.00",
                "Change               7.67",
                "Pho                 16.00",
                "Total               16.00",
            ),
        )

        assertEquals(listOf(ParsedItem("Pho", Cents(1_600), 1)), parsed.items)
        assertEquals(Cents(1_600), parsed.total)
        assertNull(parsed.merchant)
    }

    @Test
    fun parentheticalDiscountAndIndentedModifier() {
        val parsed = parseReceipt(
            listOf(
                line("Rare Beef Pho", top = 40.0, left = 20.0),
                line("no onion", top = 78.0, left = 70.0),
                line("16.50", top = 120.0, left = 400.0),
                line("Promo", top = 170.0, left = 20.0),
                line("(\$2.00)", top = 210.0, left = 400.0),
            ),
        )

        assertEquals(
            listOf(
                ParsedItem("Rare Beef Pho", Cents(1_650), 1),
                ParsedItem("Promo", Cents(-200), 1),
            ),
            parsed.items,
        )
    }

    @Test
    fun subtotalWordDoesNotCountAsTheTotal() {
        val parsed = parseReceipt(stacked("Subtotal  10.00"))

        assertEquals(Cents(1_000), parsed.subtotal)
        assertNull(parsed.total)
        assertTrue(parsed.items.isEmpty())
    }

    @Test
    fun hstIncludedWithoutAnAmountDoesNotInventTax() {
        val parsed = parseReceipt(
            stacked(
                "Coffee   3.50",
                "Muffin   2.50",
                "Subtotal 6.00",
                "HST Included",
                "Total    6.00",
            ),
        )

        assertEquals(Cents(600), itemsSum(parsed))
        assertEquals(Cents(600), parsed.subtotal)
        assertNull(parsed.tax)
        assertEquals(Cents(600), parsed.total)
    }

    @Test
    fun linesAreReadFromTopToBottomEvenIfOcrReturnsThemBackwards() {
        val parsed = parseReceipt(
            listOf(
                line("Total 13.00", top = 200.0),
                line("HST 1.00", top = 160.0),
                line("Tea 12.00", top = 80.0),
                line("CAFE", top = 20.0),
            ),
        )

        assertEquals("CAFE", parsed.merchant)
        assertEquals(listOf(ParsedItem("Tea", Cents(1_200), 1)), parsed.items)
        assertEquals(Cents(100), parsed.tax)
        assertEquals(Cents(1_300), parsed.total)
    }

    @Test
    fun emptyAndBlankLinesProduceAnEmptyReceipt() {
        val parsed = parseReceipt(
            listOf(
                OcrLine("   ", top = 0.0, left = 0.0),
                OcrLine("", top = 10.0, left = 0.0),
            ),
        )

        assertEquals(ParsedReceipt(null, emptyList(), null, null, null, null), parsed)
    }

    @Test
    fun mismatchedSubtotalIsPreservedForTheReviewer() {
        val parsed = parseReceipt(
            stacked(
                "Soup   8.00",
                "Subtotal  10.00",
            ),
        )

        assertEquals(Cents(800), itemsSum(parsed))
        assertEquals(Cents(1_000), parsed.subtotal)
    }
}

private fun stacked(vararg rows: String): List<OcrLine> =
    rows.mapIndexed { index, text -> OcrLine(text, top = index * 40.0, left = 12.0) }

private fun line(text: String, top: Double, left: Double = 12.0) = OcrLine(text, top, left)

private fun itemsSum(parsed: ParsedReceipt): Cents =
    parsed.items.fold(Cents.Zero) { acc, item -> acc + item.price * item.quantity }
