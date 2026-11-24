package ir.jibik.app

import ir.jibik.core.finance.CategoryExpense
import org.junit.Assert.assertTrue
import org.junit.Test

class JibikCategoryRingAccessibilityTest {
    @Test fun chartNamesCategoriesAndPercentagesForScreenReaders() {
        val text = jibikCategoryRingDescription(
            listOf(CategoryExpense("خوراک", 60L), CategoryExpense("حمل‌ونقل", 40L)),
            100L,
        )
        assertTrue(text.contains("خوراک: 60 درصد"))
        assertTrue(text.contains("حمل‌ونقل: 40 درصد"))
    }

    @Test fun chartSafelyExplainsEmptyOrZeroTotal() {
        assertTrue(jibikCategoryRingDescription(emptyList(), 0L).contains("هنوز"))
        assertTrue(jibikCategoryRingDescription(
            listOf(CategoryExpense("خوراک", 0L)), 0L,
        ).contains("0 درصد"))
    }
}
