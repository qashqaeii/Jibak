package ir.jibik.app

import ir.jibik.core.finance.CategoryExpense
import ir.jibik.core.finance.LedgerKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class FinancialPlanningLogicTest {
    private val obligation = FinancialObligationEntity(
        id = "obligation-1", title = "قسط وام", kind = "LOAN",
        totalToman = 1_000_000L, dueAtMillis = 1_700_000_000_000L,
        installmentCount = 4, note = "", createdAtMillis = 1_600_000_000_000L,
    )

    @Test fun categoryCapIncludesDescendantsButNeverPrefixLookalikes() {
        val expenses = listOf(
            CategoryExpense("خوراک", 40_000L),
            CategoryExpense("خوراک / خرید مواد غذایی / میوه", 90_000L),
            CategoryExpense("خوراک / رستوران و غذای بیرون", 20_000L),
            CategoryExpense("خوراکی", 300_000L),
        )
        val parent = CategoryBudgetEntity("خوراک", 200_000L, 70)
        val leaf = CategoryBudgetEntity("خوراک / خرید مواد غذایی / میوه", 100_000L, 90)
        val results = monthlyCategoryBudgetUsage(listOf(parent, leaf), expenses)
        assertEquals(150_000L, results.first { it.categoryPath == "خوراک" }.spentToman)
        assertEquals("warning", results.first { it.categoryPath == "خوراک" }.alert)
        assertEquals(90_000L, results.first { it.categoryPath.endsWith("میوه") }.spentToman)
        assertEquals(2, results.size)
    }

    @Test fun exceedingBudgetIsFlaggedWithoutSyntheticTransactions() {
        val result = monthlyCategoryBudgetUsage(
            listOf(CategoryBudgetEntity("خوراک", 100L)),
            listOf(CategoryExpense("خوراک / رستوران", 130L)),
        ).single()
        assertEquals("over", result.alert)
        assertEquals(-30L, result.remainingToman)
    }

    @Test fun obligationNeverCreatesMoneyUntilConfirmedPayment() {
        validateObligation(obligation)
        val partial = ObligationPaymentEntity(
            transactionId = "txn-1", obligationId = obligation.id,
            amountToman = 450_000L, paidAtMillis = 1_700_000_010_000L,
        )
        val progress = obligationProgress(obligation, listOf(partial))
        assertEquals(550_000L, progress.remainingToman)
        assertEquals(false, progress.settled)
        val record = settlementRecord(obligation, "wallet", 450_000L,
            partial.transactionId, partial.paidAtMillis)
        assertEquals(LedgerKind.EXPENSE, record.kind)
        assertEquals("wallet", record.fromAccountId)
        assertEquals(null, record.toAccountId)
        assertEquals(450_000L, record.amountToman)
        assertEquals("پرداخت تعهد و اقساط", record.category)
    }

    @Test fun installmentScheduleAllocatesEveryTomanWithoutRoundingErrors() {
        val item = obligation.copy(totalToman = 10L, installmentCount = 3)
        val first = nextUnpaidInstallment(item, emptyList())!!
        assertEquals(0, first.index)
        assertEquals(4L, first.amountToman)
        val paidFirst = listOf(ObligationPaymentEntity(
            "first", item.id, 4L, 1_700_000_010_000L))
        val second = nextUnpaidInstallment(item, paidFirst)!!
        assertEquals(1, second.index)
        assertEquals(3L, second.amountToman)
        val paidPartial = paidFirst + ObligationPaymentEntity(
            "half-second", item.id, 2L, 1_700_000_011_000L)
        assertEquals(2L, nextUnpaidInstallment(item, paidPartial)!!.paidTowardToman)
        val settled = paidPartial + ObligationPaymentEntity(
            "rest", item.id, 4L, 1_700_000_012_000L)
        assertEquals(null, nextUnpaidInstallment(item, settled))
    }

    @Test fun incomingReceivableIsAnIncomeNotATransferOrExtraExpense() {
        val incoming = settlementRecord(obligation.copy(kind = "RECEIVABLE"),
            "wallet", 100_000L, "collect", 1_700_000_010_000L)
        assertEquals(LedgerKind.INCOME, incoming.kind)
        assertEquals("wallet", incoming.toAccountId)
        assertEquals(null, incoming.fromAccountId)
    }

    @Test fun overPaymentCannotSilentlySettleMoreThanThePrincipal() {
        val tooMuch = ObligationPaymentEntity(
            "over", obligation.id, 1_100_000L, 1_700_000_010_000L)
        assertThrows(IllegalArgumentException::class.java) {
            obligationProgress(obligation, listOf(tooMuch))
        }
        assertThrows(IllegalArgumentException::class.java) {
            validateObligation(obligation.copy(installmentCount = 0))
        }
    }

    @Test fun foreignObligationPaymentsAreNeverCounted() {
        val foreign = ObligationPaymentEntity(
            "foreign", "different-id", 900_000L, 1_700_000_010_000L)
        assertEquals(0L, obligationProgress(obligation, listOf(foreign)).paidToman)
    }
}
