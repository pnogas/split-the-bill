package dev.splitthebill.shared

/**
 * A priced row read off a receipt. [price] is the unit price, matching [LineItem].
 * The charged amount is `price * quantity`.
 */
data class ParsedItem(
    val name: String,
    val price: Cents,
    val quantity: Int,
)

/**
 * What [parseReceipt] could find. Summary amounts stay null when the receipt
 * did not print them. [tax] sums every tax line (GST and PST, for example).
 */
data class ParsedReceipt(
    val merchant: String?,
    val items: List<ParsedItem>,
    val subtotal: Cents?,
    val tax: Cents?,
    val tip: Cents?,
    val total: Cents?,
)

fun ParsedReceipt.lineItems(): List<LineItem> = items.mapIndexed { index, item ->
    LineItem(
        id = LineItemId("parsed-$index"),
        name = item.name,
        price = item.price,
        quantity = item.quantity,
        sharedBy = emptySet(),
    )
}

/**
 * Turns OCR lines into items plus subtotal, tax, tip, and total.
 *
 * Lines are grouped into visual rows, then walked from top to bottom. A price
 * may sit at the end of the item's own line or on the following row, which is
 * how a right-hand price column often comes back from OCR. Phone numbers,
 * dates, card digits, and suggested-tip blocks are skipped.
 */
fun parseReceipt(lines: List<OcrLine>): ParsedReceipt {
    val rows = groupIntoRows(lines)
    val items = mutableListOf<ParsedItem>()
    val headers = mutableListOf<String>()
    var merchant: String? = null
    var startedItems = false
    var pendingName: String? = null
    var pendingLeft = 0.0
    var pendingKind: SummaryKind? = null
    var subtotal: Cents? = null
    var tax: Cents? = null
    var tip: Cents? = null
    var total: Cents? = null

    fun noteHeader(line: String?) {
        if (!startedItems && !line.isNullOrBlank()) headers += line
    }

    fun addItem(item: ParsedItem, header: String?) {
        if (!startedItems) {
            noteHeader(header)
            merchant = headers.firstOrNull { it.any(Char::isLetter) }
        }
        items += item
        startedItems = true
        pendingName = null
        pendingKind = null
    }

    fun applySummary(kind: SummaryKind, amount: Cents) {
        when (kind) {
            SummaryKind.Subtotal -> subtotal = amount
            SummaryKind.Tax -> tax = (tax ?: Cents.Zero) + amount
            SummaryKind.Tip -> tip = amount
            SummaryKind.Total -> total = amount
        }
        pendingKind = null
        pendingName = null
    }

    for (row in rows) {
        val text = row.text
        if (text.contains("suggest", ignoreCase = true)) continue
        if (isNoise(text)) continue
        if (percentOnly.matches(text)) continue

        val kind = summaryKind(text)
        val amounts = findMoney(text)
        if (kind != null && amounts.isNotEmpty()) {
            noteHeader(pendingName)
            applySummary(kind, amounts.last().cents)
            continue
        }
        if (kind != null) {
            noteHeader(pendingName)
            pendingKind = kind
            pendingName = null
            continue
        }
        if (isPriceOnly(text)) {
            val amount = amounts.single().cents
            val summary = pendingKind
            val name = pendingName
            if (summary != null) {
                applySummary(summary, amount)
            } else if (name != null && !percentOnly.matches(name)) {
                val item = parsePriced("$name $text")
                if (item != null) addItem(item, header = null) else pendingName = null
            }
            continue
        }
        if (amounts.isNotEmpty()) {
            val item = parsePriced(text)
            if (item != null) addItem(item, header = pendingName) else pendingName = null
            continue
        }

        val pending = pendingName
        if (pending != null && row.left > pendingLeft + indentSlack) continue
        noteHeader(pending)
        pendingName = text
        pendingLeft = row.left
        pendingKind = null
    }

    return ParsedReceipt(
        merchant = merchant,
        items = items,
        subtotal = subtotal,
        tax = tax,
        tip = tip,
        total = total,
    )
}

private enum class SummaryKind { Subtotal, Tax, Tip, Total }

private data class Row(val top: Double, val left: Double, val text: String)

private data class MoneyHit(val range: IntRange, val cents: Cents)

private const val indentSlack = 30.0

private val moneyCandidate = Regex(
    """(?i)(?:[-−(])?\s*(?:ca\$|c\$|\$)?\s*(\d{1,3}(?:,\d{3})+|\d{1,5})\.(\d{2})\)?""",
)

private val phone = Regex(
    """(?:\+?1[\s.-]?)?(?:\(\d{3}\)|\d{3})[\s.-]\d{3}[\s.-]\d{4}""",
)

private val email = Regex("""[\w.+-]+@[\w-]+\.[A-Za-z]{2,}""")

private val url = Regex("""(?i)(www\.|https?://|\.com\b|\.ca\b)""")

private val cardMask = Regex(
    """(?i)(?:[*xX]{2,}\s*\d{2,4})|(?:\b\d{4}(?:\s+\d{4}){3}\b)""",
)

private val taxId = Regex(
    """(?i)\b(?:hst|gst|pst|qst|vat|bn)\s*(?:#|no\.?|number|:)?\s*\d{8,}""",
)

private val monthDate = Regex(
    """(?i)\b(?:jan|feb|mar|apr|may|jun|jul|aug|sep|sept|oct|nov|dec)[a-z]*\s+\d{1,2}(?:,?\s+\d{2,4})?\b""",
)

private val numericDate = Regex("""\b\d{1,4}[/-]\d{1,2}[/-]\d{1,4}\b""")

private val street = Regex(
    """(?i)\b(?:street|st|avenue|ave|road|rd|blvd|boulevard|drive|dr|lane|ln|way|crescent|cres)\b""",
)

private val postal = Regex(
    """(?i)\b[ABCEGHJ-NPRSTVXY]\d[ABCEGHJ-NPRSTV-Z][ -]?\d[ABCEGHJ-NPRSTV-Z]\d\b""",
)

private val paymentLine = Regex(
    """(?i)^(cash|change|visa|mastercard|master\s*card|amex|american\s+express|debit|credit|interac|gift\s*card|rounding|tender|payment)\b""",
)

private val chatterLine = Regex(
    """(?i)^(server|cashier|table|guest|guests|order|receipt|check|terminal|station|auth|approval|approved|reference|ref)\b""",
)

private val percentOnly = Regex("""\d{1,2}%""")

private fun groupIntoRows(lines: List<OcrLine>): List<Row> {
    val cleaned = lines
        .map { it.copy(text = it.text.trim()) }
        .filter { it.text.isNotEmpty() }
        .sortedWith(compareBy({ it.top }, { it.left }))
    if (cleaned.isEmpty()) return emptyList()

    val gaps = cleaned.zipWithNext { a, b -> b.top - a.top }.filter { it >= 8.0 }.sorted()
    val lineSpacing = if (gaps.isEmpty()) 24.0 else gaps[gaps.size / 2]
    val tolerance = (lineSpacing * 0.45).coerceAtLeast(8.0)

    val grouped = mutableListOf<MutableList<OcrLine>>()
    for (line in cleaned) {
        val current = grouped.lastOrNull()
        val rowTop = current?.minOf { it.top }
        if (current == null || rowTop == null || line.top - rowTop > tolerance) {
            grouped += mutableListOf(line)
        } else {
            current += line
        }
    }
    return grouped.map { parts ->
        val ordered = parts.sortedBy { it.left }
        Row(
            top = parts.minOf { it.top },
            left = parts.minOf { it.left },
            text = ordered.joinToString(" ") { it.text }.replace(Regex("""\s+"""), " ").trim(),
        )
    }
}

private fun summaryKind(text: String): SummaryKind? {
    if (listOf("subtotal", "sub-total", "sub total").any { hasPhrase(text, it) }) {
        return SummaryKind.Subtotal
    }
    if (taxId.containsMatchIn(text)) return null
    if (listOf("sales tax", "hst", "gst", "pst", "qst", "vat", "tax").any { hasPhrase(text, it) }) {
        return SummaryKind.Tax
    }
    if (listOf("gratuity", "tip").any { hasPhrase(text, it) }) return SummaryKind.Tip
    if (
        listOf("grand total", "amount due", "balance due", "total due", "amount paid", "total")
            .any { hasPhrase(text, it) }
    ) {
        return SummaryKind.Total
    }
    return null
}

private fun isNoise(text: String): Boolean {
    val trimmed = text.trim()
    if (trimmed.none(Char::isLetterOrDigit)) return true
    if (phone.containsMatchIn(trimmed)) return true
    if (email.containsMatchIn(trimmed) || url.containsMatchIn(trimmed)) return true
    if (cardMask.containsMatchIn(trimmed)) return true
    if (taxId.containsMatchIn(trimmed)) return true
    val priced = findMoney(trimmed).isNotEmpty()
    if (!priced && (monthDate.containsMatchIn(trimmed) || numericDate.containsMatchIn(trimmed))) return true
    if (!priced && (street.containsMatchIn(trimmed) || postal.containsMatchIn(trimmed))) return true
    if (!priced && chatterLine.containsMatchIn(trimmed)) return true
    if (trimmed.contains("thank you", ignoreCase = true)) return true
    if (paymentLine.containsMatchIn(trimmed)) return true
    return false
}

private fun isPriceOnly(text: String): Boolean {
    val amounts = findMoney(text)
    if (amounts.size != 1) return false
    val rest = stripMoney(text).replace(Regex("""(?i)^T$"""), "").trim()
    return rest.isEmpty() || percentOnly.matches(rest)
}

private fun parsePriced(text: String): ParsedItem? {
    val amounts = findMoney(text)
    if (amounts.isEmpty()) return null
    val working = stripMoney(text).replace(Regex("""(?i)\s+T$"""), "").trim()
    if (working.isBlank()) return null

    explicitQuantity(working)?.let { explicit ->
        val cleaned = cleanItemName(explicit.name)
        if (cleaned.isBlank() || percentOnly.matches(cleaned) || isSkippableName(cleaned)) return null
        val printed = amounts.last().cents
        if (explicit.atPrice) {
            return ParsedItem(cleaned, printed, explicit.quantity)
        }
        if (explicit.quantity > 1 && printed.amount % explicit.quantity == 0L) {
            return ParsedItem(cleaned, Cents(printed.amount / explicit.quantity), explicit.quantity)
        }
        val fallback = cleanItemName(working)
        if (fallback.isBlank() || isSkippableName(fallback)) return null
        return ParsedItem(fallback, printed, 1)
    }

    val trailingQuantity = Regex("""^(.+?)\s+(\d{1,2})$""").matchEntire(working)
    if (trailingQuantity != null && amounts.size >= 2) {
        val quantity = trailingQuantity.groupValues[2].toInt()
        val unit = amounts[amounts.size - 2].cents
        val total = amounts.last().cents
        val name = cleanItemName(trailingQuantity.groupValues[1])
        if (quantity in 2..50 && unit * quantity == total && name.isNotBlank() && !isSkippableName(name)) {
            return ParsedItem(name, unit, quantity)
        }
    }

    val name = cleanItemName(working)
    if (name.isBlank() || percentOnly.matches(name) || isSkippableName(name)) return null
    return ParsedItem(name, amounts.last().cents, 1)
}

private data class ExplicitQuantity(val quantity: Int, val name: String, val atPrice: Boolean)

private fun explicitQuantity(working: String): ExplicitQuantity? {
    Regex("""(?i)^(\d{1,2})\s*[x×]\s+(.+)$""").matchEntire(working)?.let {
        return ExplicitQuantity(it.groupValues[1].toInt(), it.groupValues[2], atPrice = false)
    }
    Regex("""(?i)^(.+?)\s+[x×]\s*(\d{1,2})$""").matchEntire(working)?.let {
        return ExplicitQuantity(it.groupValues[2].toInt(), it.groupValues[1], atPrice = false)
    }
    Regex("""(?i)^(?:qty|quantity)\.?\s*(\d{1,2})\s+(.+)$""").matchEntire(working)?.let {
        return ExplicitQuantity(it.groupValues[1].toInt(), it.groupValues[2], atPrice = false)
    }
    Regex("""(?i)^(\d{1,2})\s*@\s+(.+)$""").matchEntire(working)?.let {
        return ExplicitQuantity(it.groupValues[1].toInt(), it.groupValues[2], atPrice = true)
    }
    Regex("""(?i)^(.+?)\s+(\d{1,2})\s*@$""").matchEntire(working)?.let {
        return ExplicitQuantity(it.groupValues[2].toInt(), it.groupValues[1], atPrice = true)
    }
    Regex("""(?i)^(\d{1,2})\s*@$""").matchEntire(working)?.let {
        return ExplicitQuantity(it.groupValues[1].toInt(), "Item", atPrice = true)
    }
    return null
}

private fun cleanItemName(name: String): String = name
    .replace(Regex("""^\d{1,2}[.)]\s+"""), "")
    .replace(Regex("""(?i)ca\$|c\$|\$"""), " ")
    .replace(Regex("""\s+"""), " ")
    .trim(' ', '-', ':', '|', '.', ',')

private fun isSkippableName(name: String): Boolean {
    return paymentLine.containsMatchIn(name) || chatterLine.containsMatchIn(name)
}

private fun findMoney(text: String): List<MoneyHit> {
    return moneyCandidate.findAll(text).mapNotNull { match ->
        val end = match.range.last + 1
        var start = match.range.first
        while (start < end && text[start].isWhitespace()) start++
        if (start > 0 && text[start - 1].isLetterOrDigit()) return@mapNotNull null
        if (end < text.length && text[end].isDigit()) return@mapNotNull null
        val whole = match.groupValues[1].replace(",", "")
        val fraction = match.groupValues[2]
        val value = whole.toLong() * 100 + fraction.toLong()
        val negative = match.value.contains('-') || match.value.contains('−') || match.value.contains('(')
        MoneyHit(match.range, Cents(if (negative) -value else value))
    }.toList()
}

private fun stripMoney(text: String): String {
    var result = text
    for (hit in findMoney(text).asReversed()) {
        result = result.removeRange(hit.range)
    }
    return result.replace(Regex("""\s+"""), " ").trim()
}

private fun hasPhrase(text: String, phrase: String): Boolean {
    val haystack = text.lowercase()
    val needle = phrase.lowercase()
    var from = 0
    while (from < haystack.length) {
        val index = haystack.indexOf(needle, from)
        if (index < 0) return false
        val before = if (index == 0) ' ' else haystack[index - 1]
        val afterIndex = index + needle.length
        val after = if (afterIndex >= haystack.length) ' ' else haystack[afterIndex]
        if (!before.isLetter() && !after.isLetter()) return true
        from = index + 1
    }
    return false
}
