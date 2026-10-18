package dev.splitthebill.shared

/**
 * Splits [bill] into one [PersonShare] per person.
 *
 * Each item's line total (`price * quantity`) is divided evenly across
 * [LineItem.sharedBy]. Tax and tip are then allocated in proportion to each
 * person's item subtotal. When the bill subtotal is zero, tax and tip are
 * split evenly across everyone on the bill.
 *
 * Every division uses the largest-remainder method: each person gets the
 * floor of their exact share, and leftover cents go to the largest fractional
 * parts. Equal fractions break toward the lowest [PersonId]. Item shares, tax
 * shares, and tip shares each add up exactly, so the returned totals add up
 * to [Bill.total].
 *
 * People are returned in [Bill.people] order. Someone who shares no items
 * still appears, with a zero item subtotal.
 */
fun split(bill: Bill): SplitResult {
    validate(bill)?.let { return SplitResult.Failure(it) }

    val itemShares = bill.people.associate { it.id to Cents.Zero }.toMutableMap()
    for (item in bill.items) {
        val allocation = allocate(
            total = item.lineTotal.amount,
            weights = item.sharedBy.map { it to 1L },
        )
        for ((personId, cents) in allocation) {
            itemShares[personId] = itemShares.getValue(personId) + Cents(cents)
        }
    }

    val weights = bill.people.map { person -> person.id to itemShares.getValue(person.id).amount }
    val taxShares = allocate(bill.tax.amount, weights)
    val tipShares = allocate(bill.tip.amount, weights)

    val shares = bill.people.map { person ->
        val itemsSubtotal = itemShares.getValue(person.id)
        val tax = Cents(taxShares.getValue(person.id))
        val tip = Cents(tipShares.getValue(person.id))
        PersonShare(
            person = person,
            itemsSubtotal = itemsSubtotal,
            tax = tax,
            tip = tip,
            total = itemsSubtotal + tax + tip,
        )
    }
    return SplitResult.Success(shares)
}

sealed interface SplitResult {
    data class Success(val shares: List<PersonShare>) : SplitResult

    data class Failure(val error: SplitError) : SplitResult
}

/** A problem the UI can show instead of a partial split. */
sealed interface SplitError {
    val message: String

    data class UnassignedItems(val itemIds: List<LineItemId>) : SplitError {
        override val message: String =
            "Assign every item to at least one person. Unassigned: ${itemIds.joinToString { it.value }}."
    }

    data class UnknownSharers(
        val itemId: LineItemId,
        val personIds: Set<PersonId>,
    ) : SplitError {
        override val message: String =
            "Item ${itemId.value} is shared by people who are not on the bill: ${personIds.joinToString { it.value }}."
    }

    data class DuplicatePeople(val personIds: List<PersonId>) : SplitError {
        override val message: String =
            "Each person needs a unique id. Duplicates: ${personIds.joinToString { it.value }}."
    }

    data class InvalidItems(val itemIds: List<LineItemId>) : SplitError {
        override val message: String =
            "Each item needs a positive quantity and a non-negative price. Check: ${itemIds.joinToString { it.value }}."
    }

    data object NegativeTaxOrTip : SplitError {
        override val message: String = "Tax and tip must be zero or positive."
    }

    data object NobodyToCharge : SplitError {
        override val message: String = "Add at least one person before splitting tax or tip."
    }
}

private fun validate(bill: Bill): SplitError? {
    val duplicateIds = bill.people
        .groupBy { it.id }
        .filterValues { it.size > 1 }
        .keys
        .toList()
    if (duplicateIds.isNotEmpty()) {
        return SplitError.DuplicatePeople(duplicateIds)
    }

    val invalidItems = bill.items
        .filter { it.quantity < 1 || it.price.amount < 0 }
        .map { it.id }
    if (invalidItems.isNotEmpty()) {
        return SplitError.InvalidItems(invalidItems)
    }

    if (bill.tax.amount < 0 || bill.tip.amount < 0) {
        return SplitError.NegativeTaxOrTip
    }

    val unassigned = bill.items.filter { it.sharedBy.isEmpty() }.map { it.id }
    if (unassigned.isNotEmpty()) {
        return SplitError.UnassignedItems(unassigned)
    }

    val knownIds = bill.people.map { it.id }.toSet()
    for (item in bill.items) {
        val unknown = item.sharedBy - knownIds
        if (unknown.isNotEmpty()) {
            return SplitError.UnknownSharers(item.id, unknown)
        }
    }

    if (bill.people.isEmpty() && (bill.tax.amount > 0 || bill.tip.amount > 0)) {
        return SplitError.NobodyToCharge
    }

    return null
}

/**
 * Largest-remainder (Hamilton) allocation.
 *
 * [weights] may be zero. A zero total weight with a positive [total] falls
 * back to an even split, which is the same method with every weight set to 1.
 * The returned amounts sum to [total].
 */
private fun allocate(total: Long, weights: List<Pair<PersonId, Long>>): Map<PersonId, Long> {
    require(total >= 0L) { "allocate expects a non-negative total, was $total" }
    if (weights.isEmpty() || total == 0L) {
        return weights.associate { (id, _) -> id to 0L }
    }

    val weightSum = weights.sumOf { it.second }
    val effective = if (weightSum == 0L) weights.map { (id, _) -> id to 1L } else weights
    val divisor = effective.sumOf { it.second }

    val portions = effective.map { (id, weight) ->
        val numerator = total * weight
        Portion(id = id, floor = numerator / divisor, remainder = numerator % divisor)
    }
    val leftover = total - portions.sumOf { it.floor }
    val bonusIds = portions
        .sortedWith(compareByDescending<Portion> { it.remainder }.thenBy { it.id.value })
        .take(leftover.toInt())
        .map { it.id }
        .toSet()

    return portions.associate { portion ->
        portion.id to portion.floor + if (portion.id in bonusIds) 1L else 0L
    }
}

private data class Portion(
    val id: PersonId,
    val floor: Long,
    val remainder: Long,
)
