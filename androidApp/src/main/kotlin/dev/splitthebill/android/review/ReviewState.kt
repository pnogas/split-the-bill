package dev.splitthebill.android.review

import dev.splitthebill.android.home.formatDollars
import dev.splitthebill.shared.Cents
import dev.splitthebill.shared.ParsedReceipt

data class ReviewItemUi(
    val key: String,
    val name: String,
    val price: String,
    val quantity: String,
)

data class ReviewUiState(
    val merchant: String?,
    val items: List<ReviewItemUi>,
    val tax: String,
    val tip: String,
    val receiptSubtotal: Cents?,
    val receiptTotal: Cents?,
)

sealed interface SubtotalCheck {
    data class Matches(val sum: Cents) : SubtotalCheck

    data class Differs(val sum: Cents, val receipt: Cents) : SubtotalCheck

    data class Unknown(val sum: Cents) : SubtotalCheck

    data object NeedsPrices : SubtotalCheck
}

fun reviewFromParsed(parsed: ParsedReceipt): ReviewUiState {
    return ReviewUiState(
        merchant = parsed.merchant,
        items = parsed.items.mapIndexed { index, item ->
            ReviewItemUi(
                key = "item-$index",
                name = item.name,
                price = item.price.formatPlain(),
                quantity = item.quantity.toString(),
            )
        },
        tax = parsed.tax?.formatPlain().orEmpty(),
        tip = parsed.tip?.formatPlain().orEmpty(),
        receiptSubtotal = parsed.subtotal,
        receiptTotal = parsed.total,
    )
}

fun subtotalCheck(state: ReviewUiState): SubtotalCheck {
    if (state.items.any { parseDollars(it.price) == null || it.quantity.toIntOrNull()?.let { qty -> qty > 0 } != true }) {
        return SubtotalCheck.NeedsPrices
    }
    val sum = itemsSum(state.items)
    val receipt = state.receiptSubtotal ?: return SubtotalCheck.Unknown(sum)
    return if (receipt == sum) SubtotalCheck.Matches(sum) else SubtotalCheck.Differs(sum, receipt)
}

fun itemsSum(items: List<ReviewItemUi>): Cents = items.fold(Cents.Zero) { acc, item ->
    val price = parseDollars(item.price) ?: return@fold acc
    val quantity = item.quantity.toIntOrNull()?.takeIf { it > 0 } ?: return@fold acc
    acc + price * quantity
}

fun parseDollars(text: String): Cents? {
    val cleaned = text.trim().removePrefix("$").replace(",", "").replace(" ", "")
    if (cleaned.isEmpty()) return null
    val negative = cleaned.startsWith("-")
    val body = cleaned.removePrefix("-")
    val match = Regex("""^(\d+)(?:\.(\d{1,2}))?$""").matchEntire(body) ?: return null
    val dollars = match.groupValues[1].toLong()
    val fraction = match.groupValues[2].padEnd(2, '0').ifEmpty { "0" }.toLong()
    val amount = dollars * 100 + fraction
    return Cents(if (negative) -amount else amount)
}

internal fun Cents.formatPlain(): String {
    val negative = amount < 0
    val abs = kotlin.math.abs(amount)
    val body = "${abs / 100}.${(abs % 100).toString().padStart(2, '0')}"
    return if (negative) "-$body" else body
}

internal fun Cents.signedDollars(): String {
    val negative = amount < 0
    val body = Cents(kotlin.math.abs(amount)).formatDollars()
    return if (negative) "-$body" else body
}
