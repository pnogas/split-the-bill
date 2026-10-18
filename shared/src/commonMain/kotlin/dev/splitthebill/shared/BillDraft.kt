package dev.splitthebill.shared

import kotlin.time.Instant

/** The person who paid. They are on every new bill and cannot be removed. */
object BillOwner {
    val id = PersonId("me")
    const val name = "Me"
    val person = Person(id, name)
}

/**
 * How the tip is chosen after review.
 *
 * [Unset] means the receipt had no tip and the user has not picked one yet.
 * [None] is an explicit zero. [Percent] is a whole-number percent of the
 * item subtotal, rounded half up. [Fixed] is a dollar amount.
 */
sealed interface TipChoice {
    data object Unset : TipChoice

    data object None : TipChoice

    data class Percent(val percent: Int) : TipChoice

    data class Fixed(val amount: Cents) : TipChoice
}

/**
 * The in-progress bill from review through totals.
 *
 * [items] keep the review ids so assignments survive a trip back to edit a price.
 */
data class BillDraft(
    val id: BillId,
    val createdAt: Instant,
    val merchant: String?,
    val people: List<Person>,
    val items: List<LineItem>,
    val tax: Cents,
    val tipChoice: TipChoice,
    val parsedTip: Cents?,
    val nextPersonNumber: Int,
) {
    val itemsSubtotal: Cents
        get() = items.fold(Cents.Zero) { acc, item -> acc + item.lineTotal }

    val tip: Cents
        get() = tipAmount(itemsSubtotal, tipChoice)

    fun hasEnoughPeople(): Boolean = people.size >= 2 && people.all { it.name.isNotBlank() }
}

fun initialTipChoice(parsedTip: Cents?, editedTip: Cents?): TipChoice = when {
    editedTip != null && editedTip.amount > 0 -> TipChoice.Fixed(editedTip)
    parsedTip != null && parsedTip.amount > 0 -> TipChoice.Fixed(parsedTip)
    else -> TipChoice.Unset
}

fun tipAmount(subtotal: Cents, choice: TipChoice): Cents = when (choice) {
    TipChoice.Unset, TipChoice.None -> Cents.Zero
    is TipChoice.Fixed -> if (choice.amount.amount < 0) Cents.Zero else choice.amount
    is TipChoice.Percent -> Cents(percentOf(subtotal.amount, choice.percent))
}

/** Half-up percent. 15% of 1000 cents is 150. 20% of 333 cents is 67. */
fun percentOf(cents: Long, percent: Int): Long {
    if (cents <= 0L || percent <= 0) return 0L
    return (cents * percent.toLong() + 50L) / 100L
}

fun BillDraft.toBill(): Bill = Bill(
    id = id,
    createdAt = createdAt,
    merchant = merchant,
    people = people,
    items = items,
    tax = tax,
    tip = tip,
)

fun BillDraft.toRecorded(): RecordedBill = RecordedBill(
    bill = toBill(),
    paidBy = BillOwner.id,
    settled = false,
)

fun addPerson(draft: BillDraft, rawName: String): BillDraft {
    val name = rawName.trim().take(MaxNameLength)
    if (name.isEmpty()) return draft
    val person = Person(PersonId("person-${draft.nextPersonNumber}"), name)
    return draft.copy(
        people = draft.people + person,
        nextPersonNumber = draft.nextPersonNumber + 1,
    )
}

fun renamePerson(draft: BillDraft, id: PersonId, rawName: String): BillDraft {
    val name = rawName.trim().take(MaxNameLength)
    if (name.isEmpty()) return draft
    return draft.copy(people = draft.people.map { if (it.id == id) it.copy(name = name) else it })
}

fun removePerson(draft: BillDraft, id: PersonId): BillDraft {
    if (id == BillOwner.id) return draft
    return draft.copy(
        people = draft.people.filterNot { it.id == id },
        items = draft.items.map { item -> item.copy(sharedBy = item.sharedBy - id) },
    )
}

fun toggleAssignment(draft: BillDraft, itemId: LineItemId, personId: PersonId): BillDraft {
    if (draft.people.none { it.id == personId }) return draft
    return draft.copy(
        items = draft.items.map { item ->
            if (item.id != itemId) {
                item
            } else {
                val shared = if (personId in item.sharedBy) item.sharedBy - personId else item.sharedBy + personId
                item.copy(sharedBy = shared)
            }
        },
    )
}

fun assignEveryone(draft: BillDraft, itemId: LineItemId): BillDraft {
    val everyone = draft.people.map { it.id }.toSet()
    return draft.copy(
        items = draft.items.map { item ->
            if (item.id == itemId) item.copy(sharedBy = everyone) else item
        },
    )
}

/** Items nobody has claimed are split across the whole table. Claimed items stay as they are. */
fun splitRemainingEvenly(draft: BillDraft): BillDraft {
    val everyone = draft.people.map { it.id }.toSet()
    if (everyone.isEmpty()) return draft
    return draft.copy(
        items = draft.items.map { item ->
            if (item.sharedBy.isEmpty()) item.copy(sharedBy = everyone) else item
        },
    )
}

/** The [split] failure that should keep the user on Assign, or null when every item is assigned. */
fun assignmentError(draft: BillDraft): SplitError? {
    val result = split(draft.toBill())
    return (result as? SplitResult.Failure)?.error
}

fun unassignedItemNames(draft: BillDraft): List<String> {
    val error = assignmentError(draft) as? SplitError.UnassignedItems ?: return emptyList()
    return error.itemIds.map { id -> draft.items.find { it.id == id }?.name ?: id.value }
}

/** Copy for the Assign screen. Unassigned items are named; other [split] errors keep their message. */
fun assignmentMessage(draft: BillDraft): String? {
    val error = assignmentError(draft) ?: return null
    val names = unassignedItemNames(draft)
    return if (names.isNotEmpty()) {
        "Assign every item. Still open: ${names.joinToString(", ")}."
    } else {
        error.message
    }
}

fun sharesAddUp(bill: Bill, shares: List<PersonShare>): Boolean {
    val items = shares.fold(Cents.Zero) { acc, share -> acc + share.itemsSubtotal }
    val tax = shares.fold(Cents.Zero) { acc, share -> acc + share.tax }
    val tip = shares.fold(Cents.Zero) { acc, share -> acc + share.tip }
    val total = shares.fold(Cents.Zero) { acc, share -> acc + share.total }
    return items == bill.subtotal && tax == bill.tax && tip == bill.tip && total == bill.total
}

fun shareSummary(bill: Bill, shares: List<PersonShare>): String = buildString {
    appendLine(bill.merchant ?: "Receipt")
    appendLine()
    for (item in bill.items) {
        val names = bill.people.filter { it.id in item.sharedBy }.joinToString(", ") { it.name }
        appendLine("${item.name} · $names · ${item.lineTotal.asDollars()}")
    }
    appendLine()
    appendLine("Subtotal ${bill.subtotal.asDollars()}")
    appendLine("Tax ${bill.tax.asDollars()}")
    appendLine("Tip ${bill.tip.asDollars()}")
    appendLine("Total ${bill.total.asDollars()}")
    appendLine()
    for (share in shares) {
        appendLine(share.person.name)
        appendLine("Items ${share.itemsSubtotal.asDollars()}")
        appendLine("Tax ${share.tax.asDollars()}")
        appendLine("Tip ${share.tip.asDollars()}")
        appendLine("Total ${share.total.asDollars()}")
        appendLine()
    }
    if (sharesAddUp(bill, shares)) {
        append("These add up to ${bill.total.asDollars()}.")
    } else {
        append("These do not add up to ${bill.total.asDollars()}.")
    }
}

private const val MaxNameLength = 40

private fun Cents.asDollars(): String {
    val negative = amount < 0
    val abs = kotlin.math.abs(amount)
    val text = "$${abs / 100}.${(abs % 100).toString().padStart(2, '0')}"
    return if (negative) "-$text" else text
}
