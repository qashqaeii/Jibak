package ir.jibik.core.finance

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ExpenseCategoryCatalogTest {
    @Test fun leafIdsRemainUniqueAndStable() {
        val leaves = ExpenseCategoryCatalog.leaves()
        val ids = leaves.map { it.ids.last() }
        assertEquals(ids.size, ids.distinct().size)
        assertTrue("groceries are represented", leaves.size > 25)
    }

    @Test fun granularGroceryCategoriesHaveThreeLevels() {
        val legumes = ExpenseCategoryCatalog.find("legumes")!!
        assertEquals(listOf("food", "groceries", "legumes"), legumes.ids)
        assertEquals("خوراک / خرید مواد غذایی / حبوبات", legumes.displayName)
        assertEquals("گوشت، مرغ و ماهی", ExpenseCategoryCatalog.find("protein")!!.labels.last())
    }

    @Test fun unknownCategoryIsNotSilentlyMapped() {
        assertEquals(null, ExpenseCategoryCatalog.find("unlisted-item"))
    }
}
