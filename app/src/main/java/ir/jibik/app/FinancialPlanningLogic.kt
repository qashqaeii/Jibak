package ir.jibik.app

import ir.jibik.core.finance.CategoryExpense
import ir.jibik.core.finance.LedgerKind
import ir.jibik.core.finance.LedgerRecord

/** Aggregate monthly expense totals once, including valid receipt line classifications. */
internal data class CategoryBudgetUsage(
    val categoryPath: String,
    val limitToman: Long,
    val spentToman: Long,
    val warningPercent: Int,
) {
    val remainingToman: Long get() = Math.subtractExact(limitToman, spentToman)
    val usageRatio: Double get() = spentToman.toDouble() / limitToman.toDouble()
    val alert: String get() = when {
        spentToman >= limitToman -> "over"
        spentToman * 1.0 >= limitToman.toDouble() * warningPercent / 100.0 -> "warning"
        else -> "normal"
    }
}

internal fun monthlyCategoryBudgetUsage(
    budgets: List<CategoryBudgetEntity>,
    classifiedMonthlyExpenses: List<CategoryExpense>,
): List<CategoryBudgetUsage> = budgets.map { cap ->
    require(cap.categoryPath.isNotBlank() && cap.limitToman > 0L)
    require(cap.warningPercent in 1..100)
    val spent = classifiedMonthlyExpenses
        .asSequence()
        .filter { categoryUnderPath(it.category, cap.categoryPath) }
        .fold(0L) { sum, entry ->
            require(entry.amountToman >= 0L)
            Math.addExact(sum, entry.amountToman)
        }
    CategoryBudgetUsage(
        categoryPath = cap.categoryPath,
        limitToman = cap.limitToman,
        spentToman = spent,
        warningPercent = cap.warningPercent,
    )
}.sortedWith(
    compareByDescending<CategoryBudgetUsage> { it.alert == "over" }
        .thenByDescending { it.usageRatio }.thenBy { it.categoryPath }
)

/** Obligations are memoranda: creating one never changes account balances. */
internal fun validateObligation(item: FinancialObligationEntity) {
    require(item.id.isNotBlank())
    require(item.kind in listOf("DEBT", "LOAN", "BILL", "RECEIVABLE"))
    require(item.title.trim().length in 2..70)
    require(item.totalToman > 0L)
    require(item.installmentCount in 1..120)
    require(item.dueAtMillis > 0L && item.createdAtMillis > 0L)
    require(item.note.length <= 250)
}

internal data class ObligationProgress(
    val totalToman: Long,
    val paidToman: Long,
    val remainingToman: Long,
    val settled: Boolean,
)

internal fun obligationProgress(
    obligation: FinancialObligationEntity,
    payments: List<ObligationPaymentEntity>,
): ObligationProgress {
    validateObligation(obligation)
    val paid = payments.asSequence()
        .filter { it.obligationId == obligation.id }
        .fold(0L) { sum, row ->
            require(row.amountToman > 0L)
            Math.addExact(sum, row.amountToman)
        }
    require(paid <= obligation.totalToman)
    val remaining = Math.subtractExact(obligation.totalToman, paid)
    return ObligationProgress(obligation.totalToman, paid, remaining, remaining == 0L)
}

/** One confirmed settlement maps to one immutable ledger movement. */
internal fun settlementRecord(
    obligation: FinancialObligationEntity,
    accountId: String,
    amountToman: Long,
    transactionId: String,
    atMillis: Long,
): LedgerRecord {
    validateObligation(obligation)
    require(accountId.isNotBlank() && transactionId.isNotBlank() && amountToman > 0L)
    val receiving = obligation.kind == "RECEIVABLE"
    return LedgerRecord(
        id = transactionId,
        kind = if (receiving) LedgerKind.INCOME else LedgerKind.EXPENSE,
        amountToman = amountToman,
        fromAccountId = if (receiving) null else accountId,
        toAccountId = if (receiving) accountId else null,
        category = if (receiving) "دریافت طلب" else "پرداخت تعهد و اقساط",
        note = "تسویه: " + obligation.title.trim(),
        timestampMillis = atMillis,
    )
}
