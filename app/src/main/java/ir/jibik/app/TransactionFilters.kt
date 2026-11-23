package ir.jibik.app

import ir.jibik.core.finance.LedgerKind
import ir.jibik.core.finance.LedgerRecord

internal fun categoryUnderPath(full: String, selected: String): Boolean {
    val prefix = selected.trim()
    return prefix.isBlank() || full.trim() == prefix ||
        full.trim().startsWith("$prefix / ")
}

/**
 * Search receipt items without double-counting: one transaction remains one row.
 * Corrupt or partial receipts are not used as sources of category truth.
 */
internal fun filterLocalTransactions(
    records: List<LedgerRecord>,
    receiptLines: Map<String, List<PurchaseLine>>,
    kindFilter: String,
    categoryPath: String,
    query: String,
): List<LedgerRecord> {
    val search = query.trim()
    val numericSearch = if (isAllowedTomanDraft(search)) normalizeTomanDraft(search) else ""
    return records.filter { record ->
        val kindOk = when (kindFilter) {
            "درآمد" -> record.kind == LedgerKind.INCOME
            "هزینه" -> record.kind == LedgerKind.EXPENSE
            "انتقال" -> record.kind == LedgerKind.TRANSFER
            else -> true
        }
        if (!kindOk) return@filter false
        val items = receiptLines[record.id].orEmpty()
        val receiptValid = items.isNotEmpty() &&
            runCatching { validateItemizedPurchase(record, items) }.isSuccess
        val categoryOk = categoryPath.isBlank() ||
            categoryUnderPath(record.category, categoryPath) ||
            (receiptValid && items.any { categoryUnderPath(it.category, categoryPath) })
        if (!categoryOk) return@filter false
        search.isBlank() ||
            record.note.contains(search, ignoreCase = true) ||
            record.category.contains(search, ignoreCase = true) ||
            (numericSearch.isNotBlank() &&
                record.amountToman.toString().contains(numericSearch)) ||
            (receiptValid && items.any {
                it.title.contains(search, ignoreCase = true) ||
                    it.category.contains(search, ignoreCase = true)
            })
    }
}
