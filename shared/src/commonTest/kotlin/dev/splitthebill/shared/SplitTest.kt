package dev.splitthebill.shared

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Instant

class SplitTest {
    @Test
    fun tenDollarsAmongThreePeopleUsesLargestRemainder() {
        val ada = person("ada", "Ada")
        val ben = person("ben", "Ben")
        val cy = person("cy", "Cy")
        val bill = bill(
            people = listOf(ada, ben, cy),
            items = listOf(item("entree", cents = 1_000, sharedBy = setOf(ada, ben, cy))),
        )

        val shares = assertSuccess(bill)

        // 1000 / 3 = 333 remainder 1. The extra cent goes to the lowest id.
        assertEquals(
            listOf(
                share(ada, items = 334, tax = 0, tip = 0),
                share(ben, items = 333, tax = 0, tip = 0),
                share(cy, items = 333, tax = 0, tip = 0),
            ),
            shares,
        )
        assertSumsToBill(bill, shares)
    }

    @Test
    fun extraCentFollowsPersonIdRatherThanListOrder() {
        val ada = person("ada")
        val ben = person("ben")
        val cy = person("cy")
        val bill = bill(
            people = listOf(cy, ben, ada),
            items = listOf(item("entree", cents = 1_000, sharedBy = setOf(cy, ben, ada))),
        )

        val shares = assertSuccess(bill)

        assertEquals(listOf(cy, ben, ada), shares.map { it.person })
        assertEquals(Cents(333), shares.first { it.person == cy }.total)
        assertEquals(Cents(333), shares.first { it.person == ben }.total)
        assertEquals(Cents(334), shares.first { it.person == ada }.total)
    }

    @Test
    fun oneCentAmongThreePeopleGoesToTheLowestId() {
        val ada = person("ada")
        val ben = person("ben")
        val cy = person("cy")
        val bill = bill(
            people = listOf(ada, ben, cy),
            items = listOf(item("mint", cents = 1, sharedBy = setOf(ada, ben, cy))),
        )

        val shares = assertSuccess(bill)

        assertEquals(Cents(1), shares.single { it.person == ada }.itemsSubtotal)
        assertEquals(Cents.Zero, shares.single { it.person == ben }.itemsSubtotal)
        assertEquals(Cents.Zero, shares.single { it.person == cy }.itemsSubtotal)
        assertSumsToBill(bill, shares)
    }

    @Test
    fun taxIsProportionalAndLeftoverCentsFollowTheLargestRemainder() {
        val ada = person("ada")
        val ben = person("ben")
        val cy = person("cy")
        // Subtotals 100, 200, 300. Tax of 100 is ideally 16.6, 33.3, and 50.
        val bill = bill(
            people = listOf(ada, ben, cy),
            items = listOf(
                item("a", cents = 100, sharedBy = setOf(ada)),
                item("b", cents = 200, sharedBy = setOf(ben)),
                item("c", cents = 300, sharedBy = setOf(cy)),
            ),
            tax = Cents(100),
        )

        val shares = assertSuccess(bill)

        assertEquals(
            listOf(
                share(ada, items = 100, tax = 17, tip = 0),
                share(ben, items = 200, tax = 33, tip = 0),
                share(cy, items = 300, tax = 50, tip = 0),
            ),
            shares,
        )
        assertSumsToBill(bill, shares)
    }

    @Test
    fun tiedTaxFractionsBreakTowardTheLowestPersonId() {
        val ada = person("ada")
        val ben = person("ben")
        val cy = person("cy")
        // Each pair splits 100, so subtotals are ada 50, ben 100, cy 50.
        // Tax of 10 is ideally 2.5, 5, 2.5. Ada and Cy tie; Ada wins the cent.
        val bill = bill(
            people = listOf(cy, ben, ada),
            items = listOf(
                item("shared-ab", cents = 100, sharedBy = setOf(ada, ben)),
                item("shared-bc", cents = 100, sharedBy = setOf(ben, cy)),
            ),
            tax = Cents(10),
        )

        val shares = assertSuccess(bill)

        assertEquals(Cents(3), shares.single { it.person == ada }.tax)
        assertEquals(Cents(5), shares.single { it.person == ben }.tax)
        assertEquals(Cents(2), shares.single { it.person == cy }.tax)
        assertSumsToBill(bill, shares)
    }

    @Test
    fun taxAndTipRemaindersAreAllocatedSeparatelyAndStillSumToTheTotal() {
        val ada = person("ada")
        val ben = person("ben")
        val cy = person("cy")
        val bill = bill(
            people = listOf(ada, ben, cy),
            items = listOf(item("snack", cents = 6, sharedBy = setOf(ada, ben, cy))),
            tax = Cents(1),
            tip = Cents(5),
        )

        val shares = assertSuccess(bill)

        // Item splits evenly into 2, 2, 2.
        // Tax of 1 has equal weights, so the lowest id (ada) receives it.
        // Tip of 5: floors are 1, 1, 1 and the two leftover cents go to ada then ben.
        assertEquals(
            listOf(
                share(ada, items = 2, tax = 1, tip = 2),
                share(ben, items = 2, tax = 0, tip = 2),
                share(cy, items = 2, tax = 0, tip = 1),
            ),
            shares,
        )
        assertSumsToBill(bill, shares)
    }

    @Test
    fun zeroTaxAndTipLeaveSharesEqualToItemSubtotals() {
        val ada = person("ada")
        val ben = person("ben")
        val bill = bill(
            people = listOf(ada, ben),
            items = listOf(item("salad", cents = 1_250, sharedBy = setOf(ada, ben))),
            tax = Cents.Zero,
            tip = Cents.Zero,
        )

        val shares = assertSuccess(bill)

        assertEquals(
            listOf(
                share(ada, items = 625, tax = 0, tip = 0),
                share(ben, items = 625, tax = 0, tip = 0),
            ),
            shares,
        )
        assertTrue(shares.all { it.tax == Cents.Zero && it.tip == Cents.Zero })
        assertSumsToBill(bill, shares)
    }

    @Test
    fun someoneWhoOrderedNothingPaysNothingWhenOthersCoverTheItems() {
        val ada = person("ada")
        val ben = person("ben")
        val bill = bill(
            people = listOf(ada, ben),
            items = listOf(item("steak", cents = 4_000, sharedBy = setOf(ada))),
            tax = Cents(400),
            tip = Cents(800),
        )

        val shares = assertSuccess(bill)

        assertEquals(share(ada, items = 4_000, tax = 400, tip = 800), shares.single { it.person == ada })
        assertEquals(share(ben, items = 0, tax = 0, tip = 0), shares.single { it.person == ben })
        assertSumsToBill(bill, shares)
    }

    @Test
    fun quantityIsIncludedBeforeTheItemIsSplit() {
        val ada = person("ada")
        val ben = person("ben")
        val line = LineItem(
            id = LineItemId("fries"),
            name = "Fries",
            price = Cents(333),
            quantity = 3,
            sharedBy = setOf(ada.id, ben.id),
        )
        val bill = bill(people = listOf(ada, ben), items = listOf(line))

        assertEquals(Cents(999), bill.subtotal)

        val shares = assertSuccess(bill)

        // 999 / 2 leaves one cent for the lowest id.
        assertEquals(Cents(500), shares.single { it.person == ada }.itemsSubtotal)
        assertEquals(Cents(499), shares.single { it.person == ben }.itemsSubtotal)
        assertSumsToBill(bill, shares)
    }

    @Test
    fun severalBillsAlwaysSumExactlyToTheTotal() {
        val ada = person("ada")
        val ben = person("ben")
        val cy = person("cy")
        val everyone = setOf(ada, ben, cy)
        val bills = listOf(
            bill(
                people = listOf(ada, ben, cy),
                items = listOf(item("a", 1, everyone), item("b", 2, setOf(ada, ben)), item("c", 10_000, setOf(cy))),
                tax = Cents(333),
                tip = Cents(1),
            ),
            bill(
                people = listOf(ada, ben),
                items = listOf(item("only", 1, setOf(ada))),
                tax = Cents(1),
                tip = Cents(1),
            ),
            bill(
                people = listOf(ada, ben, cy),
                items = emptyList(),
                tax = Cents.Zero,
                tip = Cents.Zero,
            ),
            bill(
                people = listOf(ada),
                items = listOf(item("solo", 99, setOf(ada))),
                tax = Cents(7),
                tip = Cents(13),
            ),
            bill(
                people = listOf(ada, ben, cy),
                items = listOf(item("free", 0, everyone)),
                tax = Cents(2),
                tip = Cents(2),
            ),
        )

        bills.forEach { sample ->
            assertSumsToBill(sample, assertSuccess(sample))
        }
    }

    @Test
    fun zeroSubtotalSplitsTaxAndTipEvenly() {
        val ada = person("ada")
        val ben = person("ben")
        val cy = person("cy")
        val bill = bill(
            people = listOf(ada, ben, cy),
            items = listOf(item("comp", cents = 0, sharedBy = setOf(ada, ben, cy))),
            tax = Cents(1),
            tip = Cents(4),
        )

        val shares = assertSuccess(bill)

        assertEquals(Cents(1), shares.single { it.person == ada }.tax)
        assertEquals(Cents.Zero, shares.single { it.person == ben }.tax)
        assertEquals(Cents.Zero, shares.single { it.person == cy }.tax)
        assertEquals(Cents(2), shares.single { it.person == ada }.tip)
        assertEquals(Cents(1), shares.single { it.person == ben }.tip)
        assertEquals(Cents(1), shares.single { it.person == cy }.tip)
        assertSumsToBill(bill, shares)
    }

    @Test
    fun billTotalAddsSubtotalTaxAndTip() {
        val ada = person("ada")
        val line = LineItem(
            id = LineItemId("coffee"),
            name = "Coffee",
            price = Cents(350),
            quantity = 2,
            sharedBy = setOf(ada.id),
        )
        val bill = bill(
            people = listOf(ada),
            items = listOf(line),
            tax = Cents(70),
            tip = Cents(140),
            merchant = "Counter",
        )

        assertEquals(Cents(700), bill.subtotal)
        assertEquals(Cents(910), bill.total)
        assertEquals("Counter", bill.merchant)
    }

    @Test
    fun unassignedItemIsAnErrorTheUiCanShow() {
        val ada = person("ada")
        val bill = bill(
            people = listOf(ada),
            items = listOf(item("mystery", cents = 500, sharedBy = emptySet())),
        )

        val error = assertFailure(bill)

        assertIs<SplitError.UnassignedItems>(error)
        assertEquals(listOf(LineItemId("mystery")), error.itemIds)
        assertTrue(error.message.isNotBlank())
    }

    @Test
    fun everyUnassignedItemIsReportedTogether() {
        val ada = person("ada")
        val bill = bill(
            people = listOf(ada),
            items = listOf(
                item("soup", cents = 400, sharedBy = setOf(ada)),
                item("wine", cents = 1_200, sharedBy = emptySet()),
                item("cake", cents = 800, sharedBy = emptySet()),
            ),
        )

        val error = assertFailure(bill)

        assertIs<SplitError.UnassignedItems>(error)
        assertEquals(listOf(LineItemId("wine"), LineItemId("cake")), error.itemIds)
    }

    @Test
    fun unknownSharerIsAnError() {
        val ada = person("ada")
        val stranger = person("not-at-the-table")
        val bill = bill(
            people = listOf(ada),
            items = listOf(item("nachos", cents = 900, sharedBy = setOf(ada, stranger))),
        )

        val error = assertFailure(bill)

        assertIs<SplitError.UnknownSharers>(error)
        assertEquals(LineItemId("nachos"), error.itemId)
        assertEquals(setOf(stranger.id), error.personIds)
        assertTrue(error.message.isNotBlank())
    }

    @Test
    fun negativePriceAndNonPositiveQuantityAreErrors() {
        val ada = person("ada")
        val negative = item("refund", cents = -1, sharedBy = setOf(ada))
        val zeroQty = LineItem(
            id = LineItemId("empty"),
            name = "Empty",
            price = Cents(100),
            quantity = 0,
            sharedBy = setOf(ada.id),
        )

        assertIs<SplitError.InvalidItems>(
            assertFailure(bill(people = listOf(ada), items = listOf(negative, zeroQty))),
        ).also { error ->
            assertEquals(listOf(LineItemId("refund"), LineItemId("empty")), error.itemIds)
        }
        assertIs<SplitError.NegativeTaxOrTip>(
            assertFailure(bill(people = listOf(ada), items = emptyList(), tip = Cents(-1))),
        )
    }

    @Test
    fun duplicatePersonIdsAreAnError() {
        val ada = person("ada", "Ada")
        val alsoAda = person("ada", "Ada Two")
        val error = assertFailure(
            bill(
                people = listOf(ada, alsoAda),
                items = listOf(item("soup", cents = 100, sharedBy = setOf(ada))),
            ),
        )

        assertIs<SplitError.DuplicatePeople>(error)
        assertEquals(listOf(PersonId("ada")), error.personIds)
    }

    @Test
    fun emptyBillWithNoChargesSucceeds() {
        val bill = bill(people = emptyList(), items = emptyList())

        assertEquals(emptyList(), assertSuccess(bill))
        assertEquals(Cents.Zero, bill.total)
    }

    @Test
    fun taxWithNobodyOnTheBillIsAnError() {
        val error = assertFailure(bill(people = emptyList(), items = emptyList(), tax = Cents(100)))

        assertIs<SplitError.NobodyToCharge>(error)
    }
}

private val createdAt = Instant.fromEpochMilliseconds(1_700_000_000_000)

private fun person(id: String, name: String = id) = Person(PersonId(id), name)

private fun item(
    id: String,
    cents: Long,
    sharedBy: Set<Person>,
) = LineItem(
    id = LineItemId(id),
    name = id,
    price = Cents(cents),
    quantity = 1,
    sharedBy = sharedBy.map { it.id }.toSet(),
)

private fun bill(
    people: List<Person>,
    items: List<LineItem>,
    tax: Cents = Cents.Zero,
    tip: Cents = Cents.Zero,
    merchant: String? = "Cafe",
) = Bill(
    id = BillId("bill"),
    createdAt = createdAt,
    merchant = merchant,
    people = people,
    items = items,
    tax = tax,
    tip = tip,
)

private fun share(person: Person, items: Long, tax: Long, tip: Long) = PersonShare(
    person = person,
    itemsSubtotal = Cents(items),
    tax = Cents(tax),
    tip = Cents(tip),
    total = Cents(items + tax + tip),
)

private fun assertSuccess(bill: Bill): List<PersonShare> {
    val result = split(bill)
    return assertIs<SplitResult.Success>(result, result.toString()).shares
}

private fun assertFailure(bill: Bill): SplitError {
    val result = split(bill)
    return assertIs<SplitResult.Failure>(result, result.toString()).error
}

private fun assertSumsToBill(bill: Bill, shares: List<PersonShare>) {
    assertEquals(bill.people, shares.map { it.person })
    assertEquals(bill.subtotal, shares.fold(Cents.Zero) { acc, share -> acc + share.itemsSubtotal })
    assertEquals(bill.tax, shares.fold(Cents.Zero) { acc, share -> acc + share.tax })
    assertEquals(bill.tip, shares.fold(Cents.Zero) { acc, share -> acc + share.tip })
    assertEquals(bill.total, shares.fold(Cents.Zero) { acc, share -> acc + share.total })
    shares.forEach { share ->
        assertEquals(share.itemsSubtotal + share.tax + share.tip, share.total)
    }
}
