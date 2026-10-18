package dev.splitthebill.android.review

import dev.splitthebill.shared.BillDraft
import dev.splitthebill.shared.BillId
import dev.splitthebill.shared.BillOwner
import dev.splitthebill.shared.Cents
import dev.splitthebill.shared.LineItem
import dev.splitthebill.shared.LineItemId
import dev.splitthebill.shared.initialTipChoice
import kotlin.time.Instant

sealed interface DraftOpen {
    data class Ready(val draft: BillDraft) : DraftOpen

    data class Blocked(val message: String) : DraftOpen
}

/**
 * Turns the review form into a [BillDraft].
 *
 * Blank names are dropped. A blank tax is zero. When [keepTip] is true the
 * tip already chosen later in the flow is left alone; otherwise the edited
 * review tip wins, then the tip parsed off the receipt.
 */
fun openDraft(
    existing: BillDraft?,
    review: ReviewUiState,
    now: Instant,
    keepTip: Boolean,
): DraftOpen {
    val named = review.items.filter { it.name.trim().isNotEmpty() }
    if (named.isEmpty()) {
        return DraftOpen.Blocked("Add at least one item with a name and price.")
    }
    val items = mutableListOf<LineItem>()
    for (item in named) {
        val price = parseDollars(item.price)
        val quantity = item.quantity.toIntOrNull()
        if (price == null || quantity == null || quantity < 1) {
            return DraftOpen.Blocked("Enter a price and quantity for each item.")
        }
        items += LineItem(
            id = LineItemId(item.key),
            name = item.name.trim().take(MaxItemName),
            price = price,
            quantity = quantity,
            sharedBy = emptySet(),
        )
    }
    val tax = when {
        review.tax.isBlank() -> Cents.Zero
        else -> parseDollars(review.tax) ?: return DraftOpen.Blocked("Enter the tax as a dollar amount.")
    }
    if (review.tip.isNotBlank() && parseDollars(review.tip) == null) {
        return DraftOpen.Blocked("Enter the tip as a dollar amount.")
    }
    val editedTip = if (review.tip.isBlank()) null else parseDollars(review.tip)
    val people = (existing?.people ?: listOf(BillOwner.person)).let { current ->
        if (current.none { it.id == BillOwner.id }) listOf(BillOwner.person) + current else current
    }
    val known = people.map { it.id }.toSet()
    val previousShares = existing?.items?.associate { it.id to it.sharedBy }.orEmpty()
    return DraftOpen.Ready(
        BillDraft(
            id = existing?.id ?: BillId("bill-${now.toEpochMilliseconds()}"),
            createdAt = existing?.createdAt ?: now,
            merchant = review.merchant?.trim()?.ifEmpty { null },
            people = people,
            items = items.map { item ->
                item.copy(sharedBy = previousShares[item.id].orEmpty().intersect(known))
            },
            tax = tax,
            tipChoice = if (keepTip && existing != null) existing.tipChoice else initialTipChoice(review.parsedTip, editedTip),
            parsedTip = review.parsedTip,
            nextPersonNumber = existing?.nextPersonNumber ?: 1,
        ),
    )
}

private const val MaxItemName = 80
