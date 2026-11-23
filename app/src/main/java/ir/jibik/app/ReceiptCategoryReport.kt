package ir.jibik.app

import ir.jibik.core.finance.CategoryExpense
import ir.jibik.core.finance.LedgerKind
import ir.jibik.core.finance.LedgerRecord

/** A receipt item contributes part of one expense, not another ledger deduction. */
internal data class ClassifiedExpenseLine(
    val record: LedgerRecord,
    val category: String,
    val amountToman: Long,
)

/**
 * Preserve exact ledger conservation. Invalid, missing or mismatching receipts
 * fall back to their single saved transaction, so analytics can never lose money.
 */
internal fun classifiedExpenseLines(
    records: List<LedgerRecord>,
    receiptLines: Map<String, List<PurchaseLine>>,
    startMillis: Long,
    endExclusiveMillis: Long,
): List<ClassifiedExpenseLine> {
    require(startMillis < endExclusiveMillis)
    return records.flatMap { record ->
        if (record.kind != LedgerKind.EXPENSE ||
            record.timestampMillis < startMillis ||
            record.timestampMillis >= endExclusiveMillis
        ) return@flatMap emptyList()
        require(record.amountToman > 0L)
        val lines = receiptLines[record.id].orEmpty()
        val classified = runCatching {
            require(lines.isNotEmpty())
            val valid = lines.map { item ->
                require(item.title.isNotBlank() && item.category.isNotBlank())
                require(item.quantity > 0 && item.unitPriceToman >= 0L)
                ClassifiedExpenseLine(
                    record = record,
                    category = item.category.trim(),
                    amountToman = Math.multiplyExact(
                        item.quantity.toLong(), item.unitPriceToman
                    ),
                )
            }
            check(valid.fold(0L) { sum, part ->
                Math.addExact(sum, part.amountToman)
            } == record.amountToman)
            valid
        }.getOrNull()
        classified ?: listOf(
            ClassifiedExpenseLine(
                record = record,
                category = record.category.trim().ifBlank { "سایر هزینه‌ها" },
                amountToman = record.amountToman,
            )
        )
    }
}

internal fun detailedCategoryExpenses(
    records: List<LedgerRecord>,
    receiptLines: Map<String, List<PurchaseLine>>,
    startMillis: Long,
    endExclusiveMillis: Long,
): List<CategoryExpense> {
    val totals = mutableMapOf<String, Long>()
    classifiedExpenseLines(records, receiptLines, startMillis, endExclusiveMillis)
        .forEach { line ->
            val category = line.category.ifBlank { "سایر هزینه‌ها" }
            totals[category] = Math.addExact(totals[category] ?: 0L, line.amountToman)
        }
    return totals.map { (category, amount) -> CategoryExpense(category, amount) }
        .sortedWith(compareByDescending<CategoryExpense> { it.amountToman }.thenBy { it.category })
}

/** Income has no receipt rows: each received amount is accounted for once. */
internal fun classifiedIncomeLines(
    records: List<LedgerRecord>,
    startMillis: Long,
    endExclusiveMillis: Long,
): List<ClassifiedExpenseLine> {
    require(startMillis < endExclusiveMillis)
    return records.asSequence().filter { record ->
        record.kind == LedgerKind.INCOME &&
            record.timestampMillis >= startMillis &&
            record.timestampMillis < endExclusiveMillis
    }.map { record ->
        require(record.amountToman > 0L)
        ClassifiedExpenseLine(
            record = record,
            category = record.category.trim().ifBlank { "سایر درآمدها" },
            amountToman = record.amountToman,
        )
    }.toList()
}

internal fun detailedIncomeCategories(
    records: List<LedgerRecord>,
    startMillis: Long,
    endExclusiveMillis: Long,
): List<CategoryExpense> {
    val totals = mutableMapOf<String, Long>()
    classifiedIncomeLines(records, startMillis, endExclusiveMillis).forEach { line ->
        totals[line.category] = Math.addExact(
            totals[line.category] ?: 0L, line.amountToman,
        )
    }
    return totals.map { (key, value) -> CategoryExpense(key, value) }
        .sortedWith(compareByDescending<CategoryExpense> { it.amountToman }.thenBy { it.category })
}

/**
 * A drilldown level groups by exactly one additional hierarchy segment.
 * Direct postings on a parent are preserved as a separate "direct" line.
 */
internal data class CategoryReportRow(
    val path: String,
    val title: String,
    val amountToman: Long,
    val direct: Boolean,
    val drillable: Boolean,
)

internal fun categoryReportRows(
    categories: List<CategoryExpense>,
    parentPath: String = "",
): List<CategoryReportRow> {
    val prefix = parentPath.trim()
    val depth = if (prefix.isEmpty()) 0 else prefix.split(" / ").size
    val sums = mutableMapOf<Pair<String, Boolean>, Long>()
    val hasDescendants = mutableSetOf<String>()
    categories.forEach { item ->
        if (item.amountToman < 0L) throw IllegalArgumentException("Negative expense category")
        val full = item.category.trim().ifBlank { "سایر هزینه‌ها" }
        if (prefix.isNotEmpty() && full != prefix && !full.startsWith("$prefix / ")) {
            return@forEach
        }
        val parts = full.split(" / ")
        val direct = parts.size <= depth
        val childPath = if (direct) prefix else parts.take(depth + 1).joinToString(" / ")
        if (parts.size > depth + 1) hasDescendants.add(childPath)
        val key = childPath to direct
        sums[key] = Math.addExact(sums[key] ?: 0L, item.amountToman)
    }
    return sums.map { (key, amount) ->
        val (path, direct) = key
        CategoryReportRow(
            path = path,
            title = if (direct) "ثبت مستقیم در این دسته"
                else path.substringAfterLast(" / "),
            amountToman = amount,
            direct = direct,
            drillable = !direct && hasDescendants.contains(path),
        )
    }.sortedWith(
        compareByDescending<CategoryReportRow> { it.amountToman }
            .thenBy { it.title }
    )
}

internal fun categoryReportContributions(
    classified: List<ClassifiedExpenseLine>,
    categoryPath: String,
    directOnly: Boolean,
): List<ClassifiedExpenseLine> {
    return classified.filter {
        val path = it.category.trim().ifBlank { "سایر هزینه‌ها" }
        if (directOnly) path == categoryPath
        else path == categoryPath || path.startsWith("$categoryPath / ")
    }.sortedWith(compareByDescending<ClassifiedExpenseLine> {
        it.record.timestampMillis
    }.thenBy { it.record.id })
}
