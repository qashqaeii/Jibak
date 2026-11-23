package ir.jibik.app

import ir.jibik.core.finance.LedgerKind
import ir.jibik.core.finance.LedgerRecord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class JiboAiSnapshotTest {
    private val now = 1_900_000_000_000L
    private fun row(
        kind: LedgerKind,
        amount: Long,
        category: String = "",
        at: Long = now - 1_000L,
    ) = LedgerRecord(
        id = (kind.name + amount.toString() + category + at.toString()),
        kind = kind,
        amountToman = amount,
        fromAccountId = if (kind != LedgerKind.INCOME) "wallet" else null,
        toAccountId = if (kind != LedgerKind.EXPENSE) "wallet2" else null,
        category = category,
        note = "این توضیحات محرمانه نباید از دستگاه خارج شوند",
        timestampMillis = at,
    )

    @Test fun ignoresTransfersAndOldRows() {
        val result = buildJiboSnapshot(listOf(
            row(LedgerKind.INCOME, 100000),
            row(LedgerKind.EXPENSE, 30000, "خوراک"),
            row(LedgerKind.TRANSFER, 4000000),
            row(LedgerKind.EXPENSE, 70000, "اجاره", now - 31L*24*60*60*1000),
        ), now)
        assertEquals(100000L, result.incomeToman)
        assertEquals(30000L, result.expenseToman)
        assertEquals(1, result.incomeCount)
        assertEquals(1, result.expenseCount)
        assertEquals(listOf(JiboCategoryTotal("food", 30000L)), result.categories)
    }

    @Test fun unknownCategoriesStayAnonymous() {
        val snapshot = buildJiboSnapshot(listOf(
            row(LedgerKind.EXPENSE, 12000, "خصوصی: نام پزشک من"),
            row(LedgerKind.EXPENSE, 8000, "خوراک / خرید مواد غذایی / برنج، نان و ماکارونی"),
        ), now)
        assertEquals(20000L, snapshot.expenseToman)
        assertEquals(2, snapshot.expenseCount)
        assertTrue(snapshot.categories.any { it.code == "other" && it.amountToman == 12000L })
        assertTrue(snapshot.categories.any { it.code == "food" && it.amountToman == 8000L })
        assertFalse(snapshot.toString().contains("پزشک"))
    }

    @Test fun emptyPeriodRequiresData() {
        val snapshot = buildJiboSnapshot(emptyList(), now)
        assertFalse(snapshot.hasData)
        assertEquals(0L, snapshot.expenseToman)
    }
}
