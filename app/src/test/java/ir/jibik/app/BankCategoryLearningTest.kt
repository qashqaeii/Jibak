package ir.jibik.app

import ir.jibik.core.finance.LedgerKind
import ir.jibik.core.finance.LedgerRecord
import org.junit.Assert.*
import org.junit.Test

class BankCategoryLearningTest {
    @Test fun dictionaryUsesRealCatalogAndRejectsMultiplePurposes() {
        val fuel = requireNotNull(suggestBankCategory("بنزين زدم", false, emptyList(), emptyList()))
        assertTrue(fuel.path.endsWith(" / سوخت"))
        assertNull(suggestBankCategory("بنزین و میوه", false, emptyList(), emptyList()))
        assertNull(suggestBankCategory("قسط بنزین", false, emptyList(), emptyList()))
        assertNull(suggestBankCategory("بنزین", true, emptyList(), emptyList()))
    }
    @Test fun learnsRepeatedExactNoteAndRejectsConflict() {
        val path = builtinCategoryPaths(false).first { it.endsWith(" / میوه") }
        val other = builtinCategoryPaths(false).first { it.endsWith(" / سوخت") }
        fun row(id: String, category: String) = LedgerRecord(id, LedgerKind.EXPENSE, 100,
            fromAccountId = "wallet", category = category, note = "فروشگاه آفتاب", timestampMillis = 1000)
        val history = listOf(row("1", path), row("2", path))
        assertEquals(BankCategorySuggestion(path, true), suggestBankCategory("فروشگاه آفتاب", false, history, emptyList()))
        assertNull(suggestBankCategory("فروشگاه آفتاب", false, history + row("3", other), emptyList()))
        assertNull(suggestBankCategory("فروشگاه آفتاب", false, history, emptyList(), "1"))
    }
}
