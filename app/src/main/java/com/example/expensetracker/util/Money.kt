package com.example.expensetracker.util

import java.math.BigDecimal
import java.math.RoundingMode
import java.text.NumberFormat
import java.util.Locale

/**
 * Money helpers. Amounts are Long minor units with two decimal places (paise, cents).
 * Currencies with 0 or 3 decimals (JPY, KWD) would need the scale made configurable.
 */
object Money {

    private const val SCALE = 2
    private val MAX = BigDecimal("9999999.99")

    /** "₹1,234.50" / "$1,234.50", using the device locale's currency. */
    fun format(minor: Long, locale: Locale = Locale.getDefault()): String =
        NumberFormat.getCurrencyInstance(locale).format(BigDecimal.valueOf(minor, SCALE))

    /** Plain text for an editable field: 45050 -> "450.50". */
    fun toInputText(minor: Long): String = BigDecimal.valueOf(minor, SCALE).toPlainString()

    /**
     * Parses what a person types ("450", "450.5", "450,50", "1,234.50") into minor units.
     * Returns null for anything that is not a positive amount.
     */
    fun parseToMinor(text: String): Long? {
        val compact = text.trim().replace(" ", "")
        if (compact.isEmpty()) return null
        val normalised = when {
            '.' in compact && ',' in compact -> compact.replace(",", "") // 1,234.50
            ',' in compact -> compact.replace(',', '.')                  // 450,50
            else -> compact
        }
        val value = normalised.toBigDecimalOrNull() ?: return null
        if (value <= BigDecimal.ZERO || value > MAX) return null
        return value.setScale(SCALE, RoundingMode.HALF_UP).movePointRight(SCALE).longValueExact()
    }
}
