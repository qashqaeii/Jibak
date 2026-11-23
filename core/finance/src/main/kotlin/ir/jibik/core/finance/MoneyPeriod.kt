package ir.jibik.core.finance

/**
 * Income and expenses for a half-open period [startMillis, endExclusiveMillis).
 *
 * Period boundaries come from the user's local calendar (Jalali in Jibik).
 * Keeping them as instants prevents UTC midnight from misclassifying entries.
 * Transfers only move funds between accounts and are never counted as spend.
 */
data class PeriodTotals(
    val incomeToman: Long,
    val expenseToman: Long,
)

fun periodTotals(
    records: List<LedgerRecord>,
    startMillis: Long,
    endExclusiveMillis: Long,
): PeriodTotals {
    require(startMillis < endExclusiveMillis) { "Invalid period boundaries" }
    var income = 0L
    var expenses = 0L
    records.forEach { entry ->
        if (entry.timestampMillis >= startMillis && entry.timestampMillis < endExclusiveMillis) {
            require(entry.amountToman > 0L) { "Invalid amount" }
            when (entry.kind) {
                LedgerKind.INCOME -> income = Math.addExact(income, entry.amountToman)
                LedgerKind.EXPENSE -> expenses = Math.addExact(expenses, entry.amountToman)
                LedgerKind.TRANSFER -> Unit
            }
        }
    }
    return PeriodTotals(income, expenses)
}

/** Categories of real expenses in a period, sorted by total spend. */
data class CategoryExpense(val category: String, val amountToman: Long)

fun categoryExpenses(
    records: List<LedgerRecord>,
    startMillis: Long,
    endExclusiveMillis: Long,
): List<CategoryExpense> {
    require(startMillis < endExclusiveMillis) { "Invalid period boundaries" }
    val amounts = mutableMapOf<String, Long>()
    records.forEach { record ->
        if (
            record.kind == LedgerKind.EXPENSE &&
            record.timestampMillis >= startMillis &&
            record.timestampMillis < endExclusiveMillis
        ) {
            require(record.amountToman > 0) { "Invalid expense amount" }
            val category = record.category.trim().ifBlank { "سایر هزینه‌ها" }
            amounts[category] = Math.addExact(amounts[category] ?: 0L, record.amountToman)
        }
    }
    return amounts.map { (category, amount) -> CategoryExpense(category, amount) }
        .sortedWith(compareByDescending<CategoryExpense> { it.amountToman }.thenBy { it.category })
}
