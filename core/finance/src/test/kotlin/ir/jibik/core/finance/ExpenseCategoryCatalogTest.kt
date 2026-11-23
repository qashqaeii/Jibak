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

    @Test fun entireExpenseTreeHasUniqueIDsAndThreeLevelDepth() {
        val all = ExpenseCategoryCatalog.all()
        val ids = all.map { it.ids.last() }
        assertEquals(ids.size, ids.distinct().size)
        assertTrue("At least 90 practical expense leaves", ExpenseCategoryCatalog.leaves().size >= 90)
        assertTrue(all.any { it.ids.size == 3 && it.ids.last() == "pet_vet" })
        assertTrue(all.any { it.ids.size == 3 && it.ids.last() == "internet" })
        assertEquals(listOf("food", "groceries", "fruits"), ExpenseCategoryCatalog.find("fruits")!!.ids)
        assertEquals("مسکن / اجاره", ExpenseCategoryCatalog.find("rent")!!.displayName)
    }

    @Test fun searchableHierarchyUnderstandsPersianAndNavigation() {
        val result = ExpenseCategoryCatalog.search("ماكارونی")
        assertTrue(result.any { it.ids.last() == "carbs" })
        assertTrue(ExpenseCategoryCatalog.search("دندان").any { it.ids.last() == "dental" })
        val family = ExpenseCategoryCatalog.children(listOf("family"))
        assertTrue(family.any { it.id == "children" })
        assertTrue(ExpenseCategoryCatalog.children(listOf("food", "groceries")).size > 10)
        assertTrue(ExpenseCategoryCatalog.children(listOf("food", "not-a-category")).isEmpty())
        assertEquals("خوراک / خرید مواد غذایی / حبوبات",
            ExpenseCategoryCatalog.byDisplayName("خوراک / خرید مواد غذایی / حبوبات")!!.displayName)
    }

    @Test fun incomeTaxonomyIsDistinctAndSearchable() {
        val all = IncomeCategoryCatalog.all()
        val ids = all.map { it.id }
        assertEquals(ids.size, ids.distinct().size)
        assertTrue(IncomeCategoryCatalog.leaves().size >= 15)
        assertEquals(3, IncomeCategoryCatalog.find("income_freelance")!!.depth)
        assertTrue(IncomeCategoryCatalog.search("پاداش").any { it.id == "income_bonus" })
        assertTrue(IncomeCategoryCatalog.children(listOf("income_business")).isNotEmpty())
    }

    @Test fun unknownCategoryIsNotSilentlyMapped() {
        assertEquals(null, ExpenseCategoryCatalog.find("unlisted-item"))
    }
}
