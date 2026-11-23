package ir.jibik.app

import ir.jibik.core.finance.ExpenseCategoryCatalog
import ir.jibik.core.finance.LedgerKind
import ir.jibik.core.finance.LedgerRecord

/** Only anonymous totals may leave this device, and only after explicit consent. */
internal data class JiboCategoryTotal(val code: String, val amountToman: Long)

internal data class JiboFinancialSnapshot(
    val incomeToman: Long,
    val expenseToman: Long,
    val incomeCount: Int,
    val expenseCount: Int,
    val categories: List<JiboCategoryTotal>,
) {
    val hasData: Boolean get() = incomeCount + expenseCount > 0
}

/** Persisted category labels are only matched locally, never uploaded verbatim. */
private val rootByDisplayName: Map<String, String> =
    ExpenseCategoryCatalog.leaves().associate { it.displayName to it.ids.first() }

private fun broadCategory(category: String): String {
    val root = rootByDisplayName[category] ?: when {
        category.startsWith("خوراک") || category.startsWith("خرید مواد غذایی") -> "food"
        category.startsWith("مسکن") || category.startsWith("اجاره") -> "home"
        category.startsWith("قبوض") -> "utilities"
        category.startsWith("حمل") || category.startsWith("تاکسی") -> "transport"
        category.startsWith("سلامت") -> "health"
        category.startsWith("آموزش") -> "education"
        category.startsWith("تفریح") -> "leisure"
        category.startsWith("پوشاک") -> "clothes"
        else -> "other"
    }
    return when (root) {
        "food" -> "food"
        "home" -> "housing"
        "utilities" -> "bills"
        "transport" -> "transport"
        "health" -> "health"
        "education" -> "education"
        "leisure" -> "entertainment"
        "clothes" -> "shopping"
        else -> "other"
    }
}

/** The server expects a rolling 30-day period, not a Jalali calendar month. */
internal fun buildJiboSnapshot(
    records: List<LedgerRecord>,
    nowMillis: Long = System.currentTimeMillis(),
): JiboFinancialSnapshot {
    val start = nowMillis - 30L * 24 * 60 * 60 * 1000
    var income = 0L
    var expense = 0L
    var incomes = 0
    var expenses = 0
    val categories = mutableMapOf<String, Long>()
    for (record in records) {
        if (record.timestampMillis < start || record.timestampMillis > nowMillis) continue
        when (record.kind) {
            LedgerKind.INCOME -> {
                incomes++
                income = Math.addExact(income, record.amountToman)
            }
            LedgerKind.EXPENSE -> {
                expenses++
                expense = Math.addExact(expense, record.amountToman)
                val bucket = broadCategory(record.category)
                categories[bucket] = Math.addExact(categories[bucket] ?: 0L, record.amountToman)
            }
            LedgerKind.TRANSFER -> Unit
        }
    }
    return JiboFinancialSnapshot(
        incomeToman = income,
        expenseToman = expense,
        incomeCount = incomes,
        expenseCount = expenses,
        categories = categories.entries.sortedWith(
            compareByDescending<Map.Entry<String, Long>> { it.value }.thenBy { it.key }
        ).map { JiboCategoryTotal(it.key, it.value) },
    )
}

internal enum class JiboAiFeature(val code: String, val title: String, val subtitle: String) {
    SPENDING("spending_review", "تحلیل هزینه‌ها", "ببین پول بیشتر کجا خرج شده"),
    BUDGET("budget_guidance", "راهنمای بودجه", "چند پیشنهاد برای مدیریت هزینه‌ها"),
    SAVINGS("savings_ideas", "ایده‌های پس‌انداز", "عادت‌های مالی کوچک و قابل اجرا"),
}
