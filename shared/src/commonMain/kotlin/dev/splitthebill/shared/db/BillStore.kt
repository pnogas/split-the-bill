package dev.splitthebill.shared.db

import dev.splitthebill.shared.Bill
import dev.splitthebill.shared.BillId
import dev.splitthebill.shared.Cents
import dev.splitthebill.shared.LineItem
import dev.splitthebill.shared.LineItemId
import dev.splitthebill.shared.Person
import dev.splitthebill.shared.PersonId
import dev.splitthebill.shared.RecordedBill
import kotlin.time.Instant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext

interface BillStore {
    val bills: StateFlow<List<RecordedBill>>

    suspend fun save(recorded: RecordedBill)
}

class SqlBillStore(private val database: BillDatabase) : BillStore {
    private val state = MutableStateFlow(readAll())
    private val writes = Dispatchers.Default.limitedParallelism(1)

    override val bills: StateFlow<List<RecordedBill>> = state

    override suspend fun save(recorded: RecordedBill) {
        withContext(writes) {
            database.transaction { write(recorded) }
            state.value = readAll()
        }
    }

    private fun write(recorded: RecordedBill) {
        val bill = recorded.bill
        val billId = bill.id.value
        database.billQueries.deleteShares(billId)
        database.billQueries.deleteItems(billId)
        database.billQueries.deletePeople(billId)
        database.billQueries.insertBill(
            billId,
            bill.createdAt.toEpochMilliseconds(),
            bill.merchant,
            bill.tax.amount,
            bill.tip.amount,
            recorded.paidBy.value,
            if (recorded.settled) 1L else 0L,
        )
        bill.people.forEachIndexed { index, person ->
            database.billQueries.insertPerson(billId, person.id.value, person.name, index.toLong())
        }
        bill.items.forEachIndexed { index, item ->
            database.billQueries.insertItem(
                billId,
                item.id.value,
                item.name,
                item.price.amount,
                item.quantity.toLong(),
                index.toLong(),
            )
            item.sharedBy.forEach { personId ->
                database.billQueries.insertShare(billId, item.id.value, personId.value)
            }
        }
    }

    private fun readAll(): List<RecordedBill> {
        val people = database.billQueries.selectPeople().executeAsList().groupBy { it.billId }
        val items = database.billQueries.selectItems().executeAsList().groupBy { it.billId }
        val shares = database.billQueries.selectShares().executeAsList().groupBy { it.billId to it.itemId }
        return database.billQueries.selectAll().executeAsList().map { row ->
            val billPeople = people[row.id].orEmpty().map { Person(PersonId(it.personId), it.name) }
            val billItems = items[row.id].orEmpty().map { item ->
                val shared = shares[row.id to item.itemId].orEmpty().map { PersonId(it.personId) }.toSet()
                LineItem(
                    id = LineItemId(item.itemId),
                    name = item.name,
                    price = Cents(item.priceCents),
                    quantity = item.quantity.toInt(),
                    sharedBy = shared,
                )
            }
            RecordedBill(
                bill = Bill(
                    id = BillId(row.id),
                    createdAt = Instant.fromEpochMilliseconds(row.createdAtMillis),
                    merchant = row.merchant,
                    people = billPeople,
                    items = billItems,
                    tax = Cents(row.taxCents),
                    tip = Cents(row.tipCents),
                ),
                paidBy = PersonId(row.paidBy),
                settled = row.settled != 0L,
            )
        }
    }
}
