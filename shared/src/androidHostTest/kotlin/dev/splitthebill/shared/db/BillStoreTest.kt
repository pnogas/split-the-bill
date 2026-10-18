package dev.splitthebill.shared.db

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import dev.splitthebill.shared.BillId
import dev.splitthebill.shared.BillOwner
import dev.splitthebill.shared.Cents
import dev.splitthebill.shared.LineItem
import dev.splitthebill.shared.LineItemId
import dev.splitthebill.shared.Person
import dev.splitthebill.shared.PersonId
import dev.splitthebill.shared.RecordedBill
import dev.splitthebill.shared.SplitResult
import dev.splitthebill.shared.TipChoice
import dev.splitthebill.shared.addPerson
import dev.splitthebill.shared.assignEveryone
import dev.splitthebill.shared.sharesAddUp
import dev.splitthebill.shared.split
import dev.splitthebill.shared.toRecorded
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Instant
import kotlinx.coroutines.runBlocking

class BillStoreTest {
    @Test
    fun saveAndLoadRestoresPeopleItemsAndShares() = runBlocking {
        val store = newStore()
        val maya = Person(PersonId("person-1"), "Maya")
        val draft = assignEveryone(
            addPerson(dinner(), "Maya"),
            LineItemId("pho"),
        ).copy(tipChoice = TipChoice.Percent(15), tax = Cents(214))
        val recorded = draft.toRecorded()

        store.save(recorded)

        val loaded = store.bills.value.single()
        assertEquals(recorded, loaded)
        val shares = assertIs<SplitResult.Success>(split(loaded.bill)).shares
        assertTrue(sharesAddUp(loaded.bill, shares))
        assertEquals(setOf(BillOwner.id, maya.id), loaded.bill.items.single().sharedBy)
        assertEquals(loaded.bill.tip, Cents(percentTip))
    }

    @Test
    fun newerBillsComeFirstAndSavingAgainReplacesTheSameId() = runBlocking {
        val store = newStore()
        store.save(recorded("older", millis = 1_000, merchant = "Old"))
        store.save(recorded("newer", millis = 2_000, merchant = "New"))
        assertEquals(listOf("newer", "older"), store.bills.value.map { it.bill.id.value })

        store.save(recorded("older", millis = 1_000, merchant = "Renamed"))
        assertEquals("Renamed", store.bills.value.first { it.bill.id.value == "older" }.bill.merchant)
        assertEquals(2, store.bills.value.size)
    }
}

private const val percentTip = 248L // 15% of $16.50, half up: 1650 * 15 = 24750 + 50 = 24800 / 100 = 248

private fun newStore(): BillStore {
    val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
    BillDatabase.Schema.create(driver)
    return SqlBillStore(BillDatabase(driver))
}

private fun dinner() = dev.splitthebill.shared.BillDraft(
    id = BillId("dinner"),
    createdAt = Instant.fromEpochMilliseconds(1_700_000_000_000),
    merchant = "Pho Lien",
    people = listOf(BillOwner.person),
    items = listOf(LineItem(LineItemId("pho"), "Rare Beef Pho", Cents(1_650), 1, emptySet())),
    tax = Cents.Zero,
    tipChoice = TipChoice.Unset,
    parsedTip = Cents(600),
    nextPersonNumber = 1,
)

private fun recorded(id: String, millis: Long, merchant: String) = RecordedBill(
    bill = dev.splitthebill.shared.Bill(
        id = BillId(id),
        createdAt = Instant.fromEpochMilliseconds(millis),
        merchant = merchant,
        people = listOf(BillOwner.person, Person(PersonId("person-1"), "Maya")),
        items = listOf(
            LineItem(
                LineItemId("soup"),
                "Soup",
                Cents(800),
                1,
                setOf(BillOwner.id, PersonId("person-1")),
            ),
        ),
        tax = Cents(100),
        tip = Cents(200),
    ),
    paidBy = BillOwner.id,
    settled = false,
)
