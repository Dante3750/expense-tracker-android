package com.example.expensetracker

import com.example.expensetracker.data.ocr.OcrLine
import com.example.expensetracker.data.ocr.ReceiptParser
import com.example.expensetracker.util.Money
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ReceiptParserTest {

    /** Builds lines the way OCR returns them: label on the left, amount as a separate line on the right. */
    private fun receipt(vararg rows: Pair<String, String?>): List<OcrLine> {
        val lines = ArrayList<OcrLine>()
        rows.forEachIndexed { index, (label, amount) ->
            val top = index * 40
            lines += OcrLine(label, left = 10, top = top, right = 300, bottom = top + 30)
            if (amount != null) lines += OcrLine(amount, left = 500, top = top + 3, right = 600, bottom = top + 32)
        }
        return lines.shuffled(java.util.Random(7)) // OCR order is not guaranteed
    }

    @Test
    fun picksGrandTotalOverSubtotalTaxAndCash() {
        val scan = ReceiptParser.parse(
            receipt(
                "FRESH MART SUPERSTORE" to null,
                "GSTIN 29ABCDE1234F1Z5" to null,
                "Date: 14/11/2026 22:24" to null,
                "Bill No #104233" to null,
                "Basmati Rice 5kg" to "612.00",
                "Milk 1L x2" to "112.00",
                "Subtotal" to "724.00",
                "CGST 2.5%" to "18.10",
                "SGST 2.5%" to "18.10",
                "GRAND TOTAL" to "760.20",
                "Cash tendered" to "1000.00",
                "Change" to "239.80",
                "Ph: 080-41234567" to null,
            ),
        )
        assertEquals(76020L, scan.amountMinor)
        assertEquals("FRESH MART SUPERSTORE", scan.merchant)
    }

    @Test
    fun handlesTotalInclusiveOfTaxAndCurrencySymbol() {
        val scan = ReceiptParser.parse(
            receipt(
                "Cafe Aroma" to null,
                "Cappuccino" to "₹180.00",
                "Total (incl. GST)" to "₹189.00",
            ),
        )
        assertEquals(18900L, scan.amountMinor)
    }

    @Test
    fun usesAmountOnRowBelowALabelOnlyTotalRow() {
        val lines = listOf(
            OcrLine("Corner Bakery", 10, 0, 300, 30),
            OcrLine("Croissant 3.50", 10, 40, 300, 70),
            OcrLine("Amount due", 10, 80, 300, 110),
            OcrLine("$3.50", 10, 120, 300, 150),
            OcrLine("Card 9.99", 10, 160, 300, 190),
        )
        assertEquals(350L, ReceiptParser.parse(lines).amountMinor)
    }

    @Test
    fun fallsBackToLargestDecimalAmountWithoutKeywords() {
        val scan = ReceiptParser.parse(
            receipt("Shop 42" to null, "Item A" to "12.50", "Item B" to "30.00", "Ref 884213" to null),
        )
        assertEquals(3000L, scan.amountMinor)
    }

    @Test
    fun returnsNullWhenThereIsNoAmount() {
        assertNull(ReceiptParser.parse(receipt("Thank you" to null, "Visit again" to null)).amountMinor)
    }

    @Test
    fun parsesNumberFormats() {
        assertEquals(45000L, ReceiptParser.parseAmountToken("450")?.minor)
        assertEquals(45050L, ReceiptParser.parseAmountToken("450.5")?.minor)
        assertEquals(123450L, ReceiptParser.parseAmountToken("1,234.50")?.minor)
        assertEquals(123450L, ReceiptParser.parseAmountToken("1.234,50")?.minor)
        assertEquals(12345678L, ReceiptParser.parseAmountToken("1,23,456.78")?.minor)
        assertEquals(123400L, ReceiptParser.parseAmountToken("1,234")?.minor)
        assertNull(ReceiptParser.parseAmountToken("14.11.2026"))
        assertNull(ReceiptParser.parseAmountToken("14.11.26"))
        assertNull(ReceiptParser.parseAmountToken("0.00"))
    }

    @Test
    fun moneyParsesTypedInput() {
        assertEquals(45000L, Money.parseToMinor("450"))
        assertEquals(45050L, Money.parseToMinor(" 450,5 "))
        assertEquals(123450L, Money.parseToMinor("1,234.50"))
        assertNull(Money.parseToMinor("0"))
        assertNull(Money.parseToMinor("abc"))
        assertEquals("450.50", Money.toInputText(45050L))
    }
}
