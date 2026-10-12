package dev.splitthebill.shared

import kotlin.jvm.JvmInline
import kotlin.time.Instant

/**
 * An amount of money in minor units. One dollar is [Cents] of `100`.
 * Integer cents keep the split exact; nothing here uses floating point.
 */
@JvmInline
value class Cents(val amount: Long) {
    operator fun plus(other: Cents): Cents = Cents(amount + other.amount)

    operator fun minus(other: Cents): Cents = Cents(amount - other.amount)

    operator fun times(quantity: Int): Cents = Cents(amount * quantity.toLong())

    override fun toString(): String = "Cents($amount)"

    companion object {
        val Zero = Cents(0)
    }
}

@JvmInline
value class PersonId(val value: String)

@JvmInline
value class LineItemId(val value: String)

@JvmInline
value class BillId(val value: String)

data class Person(
    val id: PersonId,
    val name: String,
)

/**
 * One receipt row.
 *
 * [price] is the unit price. The amount that gets split is [lineTotal]
 * (`price * quantity`). [sharedBy] is the set of people who divide that
 * amount; an empty set is an unassigned item and [split] rejects the bill.
 */
data class LineItem(
    val id: LineItemId,
    val name: String,
    val price: Cents,
    val quantity: Int,
    val sharedBy: Set<PersonId>,
) {
    val lineTotal: Cents get() = price * quantity
}

data class Bill(
    val id: BillId,
    val createdAt: Instant,
    val merchant: String?,
    val people: List<Person>,
    val items: List<LineItem>,
    val tax: Cents,
    val tip: Cents,
) {
    val subtotal: Cents
        get() = items.fold(Cents.Zero) { acc, item -> acc + item.lineTotal }

    val total: Cents
        get() = subtotal + tax + tip
}

/** One person's portion of a [Bill] after [split]. */
data class PersonShare(
    val person: Person,
    val itemsSubtotal: Cents,
    val tax: Cents,
    val tip: Cents,
    val total: Cents,
)
