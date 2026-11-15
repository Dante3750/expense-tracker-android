package com.example.expensetracker.data.ocr

import kotlin.math.abs
import kotlin.math.min

/** One recognised line of text with its bounding box in image pixels. */
data class OcrLine(
    val text: String,
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int,
) {
    val centerY: Float get() = (top + bottom) / 2f
    val height: Int get() = bottom - top
}

/** What we could pull out of a receipt. Everything is a suggestion for the user to confirm. */
data class ReceiptScan(
    /** Best guess for the receipt total, in minor units; null when no amount was found. */
    val amountMinor: Long?,
    /** Ranked alternatives (best first, includes [amountMinor]). */
    val candidatesMinor: List<Long>,
    val merchant: String?,
    val rawText: String,
)

/**
 * Turns OCR output into a suggested total. Pure Kotlin (no Android or ML Kit types), so it is
 * covered by plain JVM unit tests.
 *
 * Approach:
 * 1. Rebuild visual rows. OCR usually returns "TOTAL" and "450.00" as separate lines because
 *    they sit at opposite edges of the paper, so lines are regrouped by vertical position.
 * 2. Pull money-looking numbers out of each row, skipping dates, times, phone numbers and
 *    percentages.
 * 3. Score each number by the words on its row ("grand total" beats "total" beats nothing;
 *    "subtotal", "tax", "cash", "change" are pushed down), then by size.
 */
object ReceiptParser {

    private const val MAX_CANDIDATES = 5
    private const val MAX_INTEGER_DIGITS = 7

    private val numberToken = Regex("""\d+(?:[.,]\d+)*""")
    private val totalWord = Regex("""\btotal\b""")

    private val strongKeywords = listOf(
        "grand total", "amount due", "balance due", "total due", "net payable", "amount payable",
        "total payable", "net amount", "bill amount", "total amount", "to pay",
    )
    private val weakeningKeywords = listOf(
        "subtotal", "sub total", "sub-total", "discount", "change", "tendered", "cash",
        "round", "saving", "items", "qty", "quantity",
    )
    private val taxKeywords = listOf("tax", "gst", "vat", "cess")
    private val notAMerchant = listOf("invoice", "receipt", "bill", "tax", "gst", "tel", "phone", "www", "date")

    private data class Candidate(val minor: Long, val score: Int)
    internal data class ParsedAmount(val minor: Long, val hasDecimals: Boolean)

    fun parse(lines: List<OcrLine>): ReceiptScan {
        val rows = groupIntoRows(lines)
        val candidates = ArrayList<Candidate>()
        var carriedScore = 0 // keyword score from a label-only row, applied to the row below it

        for (row in rows) {
            val lower = row.lowercase()
            val keywordScore = keywordScore(lower)
            val amounts = extractAmounts(row)

            if (amounts.isEmpty()) {
                carriedScore = if (keywordScore > 0) keywordScore else 0
                continue
            }
            val hasLetters = row.any { it.isLetter() }
            val rowScore = if (keywordScore == 0 && !hasLetters) carriedScore else keywordScore
            carriedScore = 0

            // On a "label ... amount" row the amount that matters is the right-most one.
            val rightMost = amounts.last()
            for (amount in amounts) {
                var score = if (amount === rightMost) rowScore else min(rowScore, 0)
                score += if (amount.hasDecimals) 2 else -3
                candidates += Candidate(amount.minor, score)
            }
        }

        val ranked = candidates
            .sortedWith(compareByDescending<Candidate> { it.score }.thenByDescending { it.minor })
            .map { it.minor }
            .distinct()
            .take(MAX_CANDIDATES)

        return ReceiptScan(
            amountMinor = ranked.firstOrNull(),
            candidatesMinor = ranked,
            merchant = guessMerchant(rows),
            rawText = rows.joinToString("\n"),
        )
    }

    /** Groups lines whose vertical centres overlap into one row, ordered left to right. */
    internal fun groupIntoRows(lines: List<OcrLine>): List<String> {
        val rows = ArrayList<MutableList<OcrLine>>()
        for (line in lines.filter { it.text.isNotBlank() }.sortedBy { it.centerY }) {
            val current = rows.lastOrNull()
            if (current != null) {
                val rowCenter = current.map { it.centerY }.average().toFloat()
                val rowHeight = current.map { it.height }.average().toFloat()
                if (abs(line.centerY - rowCenter) <= 0.5f * min(line.height.toFloat(), rowHeight)) {
                    current += line
                    continue
                }
            }
            rows += mutableListOf(line)
        }
        return rows.map { row -> row.sortedBy { it.left }.joinToString(" ") { it.text.trim() } }
    }

    private fun keywordScore(lowerRow: String): Int {
        var score = 0
        if (strongKeywords.any { it in lowerRow }) score += 10 else if (totalWord.containsMatchIn(lowerRow)) score += 6
        if (weakeningKeywords.any { it in lowerRow }) score -= 8
        // "Total (incl. GST)" is still the total; a bare "GST 18%" row is not.
        if ("incl" !in lowerRow && taxKeywords.any { Regex("""\b$it\b""").containsMatchIn(lowerRow) }) score -= 8
        return score
    }

    private fun extractAmounts(row: String): List<ParsedAmount> =
        numberToken.findAll(row).mapNotNull { match ->
            val before = row.getOrNull(match.range.first - 1)
            val after = row.getOrNull(match.range.last + 1)
            when {
                // 14/11/2026, 22:24, 080-1234, #1042, 18%
                before != null && before in "/:-#" -> null
                after != null && after in "/:-%" -> null
                // A1042, 500g, 2x
                before?.isLetter() == true || after?.isLetter() == true -> null
                else -> parseAmountToken(match.value)
            }
        }.toList()

    /**
     * Interprets one numeric token. Handles "450", "450.00", "1,234.50", "1.234,50" and Indian
     * grouping "1,23,456.78"; returns null for things shaped like dates ("14.11.2026").
     */
    internal fun parseAmountToken(token: String): ParsedAmount? {
        val lastSeparator = token.lastIndexOfAny(charArrayOf('.', ','))
        if (lastSeparator == -1) return toAmount(token, fraction = "")

        val separator = token[lastSeparator]
        val head = token.substring(0, lastSeparator)
        val tail = token.substring(lastSeparator + 1)
        val other = if (separator == '.') ',' else '.'

        return when (tail.length) {
            1, 2 -> {
                // tail is the decimal part; head may be plain digits or grouped with the OTHER separator
                val grouped = Regex("""\d{1,3}(?:\Q$other\E\d{2,3})+""")
                if (head.all { it.isDigit() } || grouped.matches(head)) {
                    toAmount(head.filter { it.isDigit() }, tail)
                } else {
                    null
                }
            }
            3 -> {
                // "1,234" / "12.345.678": thousands grouping with a single kind of separator
                val grouped = Regex("""\d{1,3}(?:\Q$separator\E\d{2,3})*\Q$separator\E\d{3}""")
                if (grouped.matches(token)) toAmount(token.filter { it.isDigit() }, fraction = "") else null
            }
            else -> null
        }
    }

    private fun toAmount(integerDigits: String, fraction: String): ParsedAmount? {
        if (integerDigits.isEmpty() || integerDigits.length > MAX_INTEGER_DIGITS) return null
        val minor = integerDigits.toLong() * 100 + fraction.padEnd(2, '0').toLong()
        return if (minor > 0) ParsedAmount(minor, hasDecimals = fraction.isNotEmpty()) else null
    }

    /** The shop name is nearly always the first mostly-alphabetic row at the top of the receipt. */
    private fun guessMerchant(rows: List<String>): String? =
        rows.take(6).firstOrNull { row ->
            val letters = row.count { it.isLetter() }
            val lower = row.lowercase()
            letters >= 3 && letters * 2 > row.length && notAMerchant.none { Regex("""\b$it\b""").containsMatchIn(lower) }
        }?.trim()?.take(40)
}
