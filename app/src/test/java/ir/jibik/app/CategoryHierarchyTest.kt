package ir.jibik.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CategoryHierarchyTest {
    private fun custom(
        id: String, title: String, parent: String = "",
        kind: String = "EXPENSE", archived: Boolean = false,
    ) = CustomCategoryEntity(
        id = id, title = title, parentPath = parent, kind = kind,
        archived = archived, createdAtMillis = 10L,
    )

    @Test fun customHierarchyCanExtendBuiltinsToThreeLevels() {
        val branch = custom("a", "غذای مهمانی", parent = "خوراک")
        assertNull(customCategoryValidation(branch, emptyList()))
        val leaf = custom("b", "شیرینی دست‌ساز", parent = branch.displayName)
        assertNull(customCategoryValidation(leaf, listOf(branch)))
        val tooDeep = custom("c", "چهارمین سطح", parent = leaf.displayName)
        assertTrue(customCategoryValidation(tooDeep, listOf(branch, leaf)) != null)
    }

    @Test fun validationRejectsDuplicatesInvalidNamesAndOrphans() {
        val a = custom("a", "شام خانوادگی", parent = "خوراک")
        assertTrue(customCategoryValidation(a.copy(id = "b"), listOf(a)) != null)
        assertTrue(customCategoryValidation(custom("a", "دسته/نامعتبر"), emptyList()) != null)
        assertTrue(customCategoryValidation(custom("a", "بدون والد", "دسته ناموجود"), emptyList()) != null)
        assertTrue(customCategoryValidation(custom("a", "خوراک"), emptyList()) != null)
        assertTrue(customCategoryValidation(custom("a", "نمونه", kind = "TRANSFER"), emptyList()) != null)
    }

    @Test fun categoryListsIncludeCustomButNeverArchivedInSelection() {
        val active = custom("a", "غذای مهمانی", parent = "خوراک")
        val archived = custom("b", "قدیمی", parent = "خوراک", archived = true)
        val options = categoryOptions(false, listOf(active, archived), "خوراک")
        assertTrue(options.any { it.path == active.displayName && it.custom })
        assertFalse(options.any { it.path == archived.displayName })
        assertTrue(categoryOptions(false, listOf(active), search = "مهمانی")
            .any { it.path == active.displayName })
        assertFalse(isSelectableCategory(archived.displayName, false, listOf(archived)))
        assertTrue(isSelectableCategory("خوراک", false, listOf(archived)))
    }

    @Test fun incomeAndExpenseRemainSeparate() {
        val income = custom("income", "دریافتی پروژه", "درآمد جانبی", "INCOME")
        assertTrue(customCategoryValidation(income, emptyList()) != null)
        val incomeRoot = custom("income", "دریافتی پروژه", kind = "INCOME")
        assertNull(customCategoryValidation(incomeRoot, emptyList()))
        assertTrue(categoryOptions(true, listOf(incomeRoot))
            .any { it.path == incomeRoot.displayName })
        assertFalse(categoryOptions(false, listOf(incomeRoot))
            .any { it.path == incomeRoot.displayName })
    }

    @Test fun builtinLevelsRemainNavigableWithCustomSiblings() {
        val categories = categoryOptions(false, emptyList())
        assertTrue(categories.any { it.path == "خوراک" && it.hasChildren })
        assertTrue(categoryOptions(false, emptyList(), "خوراک")
            .any { it.path == "خوراک / خرید مواد غذایی" })
        assertEquals(emptyList<CategoryOption>(),
            categoryOptions(false, emptyList(), "والد ناموجود"))
    }
}
