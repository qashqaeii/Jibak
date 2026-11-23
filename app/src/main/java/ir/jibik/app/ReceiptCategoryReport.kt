package ir.jibik.app

import ir.jibik.core.finance.CategoryExpense
import ir.jibik.core.finance.LedgerKind
import ir.jibik.core.finance.LedgerRecord

/**
 * Receipt-aware expense breakdown: a multi-item shopping trip contributes its
 * real classified lines, not one fake category called "خرید منزل".
 *
 * A damaged/incomplete receipt NEVER changes the reported expense total:
 * fall back to the immutable ledger category and amount.
 */
internal fun detailedCategoryExpenses(
    records: List<LedgerRecord>,
    receiptLines: Map<String, List<PurchaseLine>>,
    startMillis: Long,
    endExclusiveMillis: Long,
): List<CategoryExpense> {
    require(startMillis < endExclusiveMillis)
    val totals = mutableMapOf<String, Long>()
    fun add(category: String, amount: Long) {
        val key = category.trim().ifBlank { "سایر هزینه‌ها" }
        totals[key] = Math.addExact(totals[key] ?: 0L, amount)
    }
    records.forEach { record ->
        if (record.kind != LedgerKind.EXPENSE ||
            record.timestampMillis < startMillis ||
            record.timestampMillis >= endExclusiveMillis
        ) return@forEach
        require(record.amountToman > 0L)
        val lines = receiptLines[record.id].orEmpty()
        val lineAmounts = runCatching {
            require(lines.isNotEmpty())
            lines.map { line ->
                require(line.title.isNotBlank() && line.category.isNotBlank())
                require(line.quantity > 0 && line.unitPriceToman >= 0)
                val amount = Math.multiplyExact(line.quantity.toLong(), line.unitPriceToman)
                line.category to amount
            }
        }.getOrNull()
        val classifiedTotal = lineAmounts?.fold(0L) { sum, row ->
            Math.addExact(sum, row.second)
        }
        if (classifiedTotal == record.amountToman && lineAmounts != null) {
            lineAmounts.forEach { (category, amount) -> add(category, amount) }
        } else {
            add(record.category, record.amountToman)
        }
    }
    return totals.map { (category, amount) -> CategoryExpense(category, amount) }
        .sortedWith(compareByDescending<CategoryExpense> { it.amountToman }.thenBy { it.category })
}
