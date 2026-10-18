package dev.splitthebill.shared

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.time.Instant

class BillStandingTest {
    private val you = Person(PersonId("you"), "You")
    private val maya = Person(PersonId("maya"), "Maya")
    private val raj = Person(PersonId("raj"), "Raj")
    private val sofia = Person(PersonId("sofia"), "Sofia")
    private val liam = Person(PersonId("liam"), "Liam")

    @Test
    fun payerIsOwedEveryoneElsesShare() {
        val recorded = evenBill(
            id = "pho",
            total = 4_160,
            people = listOf(you, maya, raj, sofia),
            paidBy = you,
        )

        val standing = viewerStanding(recorded, you.id)

        assertEquals(ViewerStanding.Owed(Cents(3_120)), standing)
    }

    @Test
    fun someoneWhoDidNotPayOwesTheirShare() {
        val owen = Person(PersonId("owen"), "Owen")
        val recorded = evenBill(
            id = "ossington",
            total = 3_750,
            people = listOf(you, owen),
            paidBy = owen,
        )

        val standing = viewerStanding(recorded, you.id)

        assertEquals(ViewerStanding.Owes(Cents(1_875)), standing)
    }

    @Test
    fun settledBillIgnoresTheUnderlyingSplit() {
        val recorded = evenBill(
            id = "banh-mi",
            total = 3_000,
            people = listOf(you, maya, raj),
            paidBy = you,
            settled = true,
        )

        assertEquals(ViewerStanding.Settled, viewerStanding(recorded, you.id))
    }

    @Test
    fun fiveWaySplitOwesThePayerTheOtherFourShares() {
        val recorded = evenBill(
            id = "sushi",
            total = 6_900,
            people = listOf(you, maya, raj, sofia, liam),
            paidBy = you,
        )

        assertEquals(ViewerStanding.Owed(Cents(5_520)), viewerStanding(recorded, you.id))
    }

    @Test
    fun balanceNetsWhatYouAreOwedAgainstWhatYouOwe() {
        val balance = balanceOf(
            listOf(
                ViewerStanding.Owed(Cents(3_120)),
                ViewerStanding.Settled,
                ViewerStanding.Owed(Cents(5_520)),
                ViewerStanding.Owes(Cents(1_875)),
            ),
        )

        assertEquals(Cents(6_765), balance.net)
        assertEquals(3, balance.openBillCount)
    }

    @Test
    fun unassignedBillIsUnresolvedInsteadOfAPartialBalance() {
        val bill = Bill(
            id = BillId("open"),
            createdAt = Instant.fromEpochSeconds(0),
            merchant = "Cafe",
            people = listOf(you),
            items = listOf(
                LineItem(
                    id = LineItemId("item"),
                    name = "Soup",
                    price = Cents(500),
                    quantity = 1,
                    sharedBy = emptySet(),
                ),
            ),
            tax = Cents.Zero,
            tip = Cents.Zero,
        )
        val standing = viewerStanding(
            RecordedBill(bill = bill, paidBy = you.id, settled = false),
            you.id,
        )

        assertIs<ViewerStanding.Unresolved>(standing)
        assertEquals(0, balanceOf(listOf(standing)).openBillCount)
    }
}

private fun evenBill(
    id: String,
    total: Long,
    people: List<Person>,
    paidBy: Person,
    settled: Boolean = false,
) = RecordedBill(
    bill = Bill(
        id = BillId(id),
        createdAt = Instant.fromEpochSeconds(0),
        merchant = id,
        people = people,
        items = listOf(
            LineItem(
                id = LineItemId(id),
                name = id,
                price = Cents(total),
                quantity = 1,
                sharedBy = people.map { it.id }.toSet(),
            ),
        ),
        tax = Cents.Zero,
        tip = Cents.Zero,
    ),
    paidBy = paidBy.id,
    settled = settled,
)
