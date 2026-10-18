package dev.splitthebill.android.data

import dev.splitthebill.shared.Bill
import dev.splitthebill.shared.BillId
import dev.splitthebill.shared.Cents
import dev.splitthebill.shared.LineItem
import dev.splitthebill.shared.LineItemId
import dev.splitthebill.shared.Person
import dev.splitthebill.shared.PersonId
import dev.splitthebill.shared.RecordedBill
import kotlin.time.Instant

data class Viewer(
    val id: PersonId,
    val name: String,
    val initials: String,
)

interface BillRepository {
    val viewer: Viewer
    val isExampleData: Boolean
    fun bills(): List<RecordedBill>
}

/**
 * Sample history behind the home mockup. Every dollar amount is an even
 * [dev.splitthebill.shared.split] of the line, not a hand-written label.
 *
 * Pho Lien is $41.60 across 4 people and you paid, so you are owed $31.20.
 * Sushi Masa is $69.00 across 5 and you paid, so you are owed $55.20.
 * The Ossington is $37.50 across 2 and the other person paid, so you owe $18.75.
 * Banh Mi Boys is marked settled. The net of the three open bills is $67.65.
 */
class ExampleBillRepository : BillRepository {
    override val viewer = Viewer(
        id = PersonId("you"),
        name = "You",
        initials = "PN",
    )

    override val isExampleData = true

    override fun bills(): List<RecordedBill> = sampleBills

    private val you = Person(viewer.id, viewer.name)
    private val maya = Person(PersonId("maya"), "Maya Chen")
    private val raj = Person(PersonId("raj"), "Raj Patel")
    private val sofia = Person(PersonId("sofia"), "Sofia Rossi")
    private val liam = Person(PersonId("liam"), "Liam O'Brien")
    private val aisha = Person(PersonId("aisha"), "Aisha Khan")
    private val owen = Person(PersonId("owen"), "Owen Ng")

    private val sampleBills = listOf(
        evenBill(
            id = "pho-lien",
            merchant = "Pho Lien",
            epochSeconds = 1_791_547_200,
            people = listOf(you, maya, raj, sofia),
            totalCents = 4_160,
            paidBy = you,
        ),
        evenBill(
            id = "banh-mi-boys",
            merchant = "Banh Mi Boys",
            epochSeconds = 1_791_028_800,
            people = listOf(you, liam, aisha),
            totalCents = 2_400,
            paidBy = you,
            settled = true,
        ),
        evenBill(
            id = "sushi-masa",
            merchant = "Sushi Masa",
            epochSeconds = 1_790_510_400,
            people = listOf(you, maya, raj, sofia, liam),
            totalCents = 6_900,
            paidBy = you,
        ),
        evenBill(
            id = "the-ossington",
            merchant = "The Ossington",
            epochSeconds = 1_789_905_600,
            people = listOf(you, owen),
            totalCents = 3_750,
            paidBy = owen,
        ),
    )
}

private fun evenBill(
    id: String,
    merchant: String,
    epochSeconds: Long,
    people: List<Person>,
    totalCents: Long,
    paidBy: Person,
    settled: Boolean = false,
) = RecordedBill(
    bill = Bill(
        id = BillId(id),
        createdAt = Instant.fromEpochSeconds(epochSeconds),
        merchant = merchant,
        people = people,
        items = listOf(
            LineItem(
                id = LineItemId("$id-total"),
                name = merchant,
                price = Cents(totalCents),
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
