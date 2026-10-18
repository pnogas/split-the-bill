package dev.splitthebill.android.review

import dev.splitthebill.shared.Cents
import org.junit.Assert.assertEquals
import org.junit.Test

class ReviewStateTest {
    @Test
    fun matchingSubtotalIsCalledOut() {
        val check = subtotalCheck(sampleReview())

        assertEquals(SubtotalCheck.Matches(Cents(3_650)), check)
    }

    @Test
    fun aDifferentSubtotalKeepsBothAmounts() {
        val check = subtotalCheck(sampleReview().copy(receiptSubtotal = Cents(4_000)))

        assertEquals(SubtotalCheck.Differs(Cents(3_650), Cents(4_000)), check)
    }

    @Test
    fun missingReceiptSubtotalStaysUnknown() {
        val check = subtotalCheck(sampleReview().copy(receiptSubtotal = null))

        assertEquals(SubtotalCheck.Unknown(Cents(3_650)), check)
    }

    @Test
    fun blankPriceAsksForAPrice() {
        val state = sampleReview().copy(
            items = sampleReview().items.mapIndexed { index, item ->
                if (index == 0) item.copy(price = "") else item
            },
        )

        assertEquals(SubtotalCheck.NeedsPrices, subtotalCheck(state))
    }

    @Test
    fun parseDollarsAcceptsADollarSignAndCents() {
        assertEquals(Cents(1_250), parseDollars("$12.50"))
        assertEquals(Cents(-200), parseDollars("-2"))
        assertEquals(null, parseDollars("abc"))
    }
}
