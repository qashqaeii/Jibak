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
        val classified = runCatching {
            require(lines.isNotEmpty())
            val itemAmounts = lines.map { line ->
                require(line.title.isNotBlank() && line.category.isNotBlank())
                require(line.quantity > 0 && line.unitPriceToman >= 0)
                line.category to Math.multiplyExact(
                    line.quantity.toLong(), line.unitPriceToman
                )
            }
            val sum = itemAmounts.fold(0L) { acc, row ->
                Math.addExact(acc, row.second)
            }
            check(sum == record.amountToman)
            itemAmounts
        }.getOrNull()
        if (classified == null) {
            add(record.category, record.amountToman)
        } else {
            classified.forEach { (category, amount) -> add(category, amount) }
        }
    }
    return totals.map { (category, amount) -> CategoryExpense(category, amount) }
        .sortedWith(compareByDescending<CategoryExpense> { it.amountToman }.thenBy { it.category })
}
