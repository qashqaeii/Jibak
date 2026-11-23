package ir.jibik.app

import ir.jibik.core.finance.ExpenseCategoryCatalog
import ir.jibik.core.finance.IncomeCategoryCatalog

/** Keep default and private user categories in one offline hierarchy. */
internal data class CategoryOption(
    val path: String,
    val title: String,
    val custom: Boolean,
    val hasChildren: Boolean,
) {
    val depth: Int get() = path.split(" / ").size
}

private fun canonical(value: String) = value.trim().lowercase()
    .replace('ي', 'ی').replace('ك', 'ک')
    .replace("‌", " ").replace(Regex("\\s+"), " ")

internal fun builtinCategoryPaths(income: Boolean): List<String> =
    if (income) IncomeCategoryCatalog.all().map { it.displayName }
    else ExpenseCategoryCatalog.all().map { it.displayName }

internal fun isSelectableCategory(
    path: String,
    income: Boolean,
    custom: List<CustomCategoryEntity>,
): Boolean = builtinCategoryPaths(income).contains(path) ||
    custom.any {
        !it.archived && it.kind == (if (income) "INCOME" else "EXPENSE") &&
            it.displayName == path
    }

internal fun categoryOptions(
    income: Boolean,
    custom: List<CustomCategoryEntity>,
    parentPath: String = "",
    search: String = "",
): List<CategoryOption> {
    val kind = if (income) "INCOME" else "EXPENSE"
    val builtins = builtinCategoryPaths(income)
    val customs = custom.filter { !it.archived && it.kind == kind }
    val all = builtins.map { it to false } + customs.map { it.displayName to true }
    val options = all.filter { (path, _) ->
        if (search.isNotBlank()) canonical(path).contains(canonical(search))
        else path.substringBeforeLast(" / ", "") == parentPath
    }
    return options.map { (path, isCustom) ->
        CategoryOption(
            path = path,
            title = path.substringAfterLast(" / "),
            custom = isCustom,
            hasChildren = all.any { (candidate, _) ->
                candidate.startsWith("$path / ") &&
                    candidate.substringBeforeLast(" / ") == path
            },
        )
    }.distinctBy { it.path }.sortedWith(
        compareBy<CategoryOption> { if (search.isNotBlank() &&
            canonical(it.title).startsWith(canonical(search))) 0 else 1 }
            .thenBy { it.custom }.thenBy { it.title }
    )
}

internal fun customCategoryValidation(
    proposed: CustomCategoryEntity,
    existing: List<CustomCategoryEntity>,
): String? {
    val title = proposed.title.trim()
    if (proposed.kind !in listOf("EXPENSE", "INCOME")) return "نوع دسته نامعتبر است."
    if (title.isBlank() || title.length > 40 ||
        title.any { it == '/' || it == '\n' || it == '\r' || it.isISOControl() }
    ) return "نام دسته باید کوتاه و بدون / یا خط جدید باشد."
    if (proposed.id.isBlank()) return "شناسه دسته معتبر نیست."
    val income = proposed.kind == "INCOME"
    val parent = proposed.parentPath
    if (parent.isNotBlank()) {
        if (parent.split(" / ").size >= 3 ||
            !isSelectableCategory(parent, income, existing)
        ) return "دسته والد نامعتبر یا بایگانی‌شده است."
    }
    val full = if (parent.isBlank()) title else "$parent / $title"
    if (builtinCategoryPaths(income).any { canonical(it) == canonical(full) }) {
        return "این دسته از قبل در دسته‌های اصلی وجود دارد."
    }
    if (existing.any {
        it.kind == proposed.kind && canonical(it.displayName) == canonical(full)
    }) return "این دسته از قبل ساخته شده؛ می‌توانی آن را بازیابی کنی."
    if (existing.any { it.id == proposed.id }) return "شناسه تکراری است."
    return null
}
