package ir.jibik.app

import ir.jibik.core.finance.Account
import ir.jibik.core.finance.LedgerKind
import ir.jibik.core.finance.LedgerRecord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream

class LedgerCsvExportTest {
    private fun export(records: List<LedgerRecord>): String {
        val output = ByteArrayOutputStream()
        LedgerCsvExport.write(
            output, listOf(Account("wallet", "کیف پول", 0L)), records,
        )
        return output.toString("UTF-8")
    }

    private fun record(
        id: String,
        note: String = "",
        kind: LedgerKind = LedgerKind.EXPENSE,
    ) = LedgerRecord(
        id = id,
        kind = kind,
        amountToman = 123_456L,
        fromAccountId = if (kind == LedgerKind.INCOME) null else "wallet",
        toAccountId = if (kind == LedgerKind.INCOME) "wallet" else null,
        category = "خرید",
        note = note,
        timestampMillis = 1_700_000_000_000L,
    )

    @Test fun exportsUtf8PersianAmountsAndUtcTimestamp() {
        val csv = export(listOf(record("id-1", "خرید نان")))
        assertTrue(csv.startsWith("\uFEFF"))
        assertTrue(csv.contains("\"123456\""))
        assertTrue(csv.contains("\"کیف پول\""))
        assertTrue(csv.contains("\"خرید نان\""))
        assertTrue(csv.contains("2023-11-14T"))
        assertEquals(2, csv.split("\r\n").filter { it.isNotEmpty() }.size)
    }

    @Test fun formulaInjectionIsEscapedWithoutLosingOriginalText() {
        val csv = export(listOf(record("id-2", "=HYPERLINK(\"evil\")")))
        assertTrue(csv.contains("\"'=HYPERLINK(\"\"evil\"\")\""))
        val withLeadingSpace = export(listOf(record("id-3", "  +SUM(1,2)")))
        assertTrue(withLeadingSpace.contains("\"'  +SUM(1,2)\""))
    }

    @Test fun invisibleUnicodeMarkersCannotMaskSpreadsheetFormula() {
        val cases = listOf(
            "\u200F=2+2", // Right-to-left mark
            "\u200B@SUM(1,2)", // Zero-width space
            "\uFEFF+2", // Hidden byte-order mark
            "\u202E-1", // Bidi override
        )
        cases.forEachIndexed { index, note ->
            val csv = export(listOf(record("unicode-$index", note)))
            assertTrue("Unescaped formula with invisible prefix: $index",
                csv.contains("\"'${note}\""))
        }
    }

    @Test fun controlCharacterCannotHideFormulaPrefix() {
        val note = "\t\n=1+1"
        val csv = export(listOf(record("control", note)))
        assertTrue(csv.contains("\"'\t\n=1+1\""))
    }

    @Test fun transferIsExportedOnceNotAsExpenseAndIncome() {
        val csv = export(listOf(record("id-4", kind = LedgerKind.TRANSFER)))
        assertTrue(csv.contains("\"transfer\""))
        assertFalse(csv.contains("\"expense\""))
        assertFalse(csv.contains("\"income\""))
        assertEquals(2, csv.split("\r\n").filter { it.isNotEmpty() }.size)
    }

    @Test fun emptyLedgerStillHasHeader() {
        assertEquals(1, export(emptyList()).split("\r\n").filter { it.isNotEmpty() }.size)
    }
}
