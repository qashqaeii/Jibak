package ir.jibik.app

import ir.jibik.core.finance.LedgerKind
import ir.jibik.core.finance.LedgerRecord

internal data class BankCategorySuggestion(val path: String, val learned: Boolean)

private fun bankNoteKey(text: String): String = BankSmsParser.normalise(text)
    .replace(Regex("[^\\p{L}\\p{N}]+"), " ").trim()

/** No extra note storage, external models, or network. Explicitly categorized history
 * is the learning source. Conflicting history never wins by recency or amount.
 */
internal fun suggestBankCategory(note: String, income: Boolean, records: List<LedgerRecord>,
    custom: List<CustomCategoryEntity>, excludeId: String = ""): BankCategorySuggestion? {
    val key = bankNoteKey(note)
    if (key.length < 2 || key.length > 250) return null
    val fallback = if (income) "سایر درآمدها" else "سایر هزینه‌ها"
    val kind = if (income) LedgerKind.INCOME else LedgerKind.EXPENSE
    val history = records.filter { it.id != excludeId && it.kind == kind && bankNoteKey(it.note) == key &&
        it.category != fallback && isSelectableCategory(it.category, income, custom) }.map { it.category }
    if (history.distinct().size > 1) return null
    if (history.size >= 2) return BankCategorySuggestion(history.first(), true)
    val rules = if (income) listOf(
        listOf("حقوق ثابت", "حقوق ماهانه", "حقوق این ماه", "حقوق") to "حقوق ثابت",
        listOf("پورسانت") to "پورسانت",
        listOf("اضافه کاری") to "اضافه‌کاری"
    ) else listOf(
        listOf("بنزین", "گازوئیل") to "سوخت",
        listOf("اسنپ", "تپسی") to "تاکسی اینترنتی",
        listOf("نان", "نانوایی") to "نان",
        listOf("میوه") to "میوه",
        listOf("شارژ ساختمان") to "شارژ ساختمان",
        listOf("اجاره خانه", "اجاره منزل") to "اجاره",
        listOf("دارو", "داروخانه") to "دارو",
        listOf("کارواش") to "کارواش",
        listOf("کارمزد بانک", "کارمزد بانکی") to "کارمزد بانک"
    )
    // Negation, multiple purposes, transfers, loans and assets require a human choice.
    if (Regex("(?:^| )(?:نه|نیست|نبود|برگشت|انتقال|قسط|وام|طلا|قرض|طلب)(?: |$)").containsMatchIn(key)) return null
    val padded = " $key "
    val leaves = rules.filter { (words, _) -> words.any { padded.contains(" " + bankNoteKey(it) + " ") } }
        .map { it.second }.distinct()
    val leaf = leaves.singleOrNull() ?: return null
    val path = builtinCategoryPaths(income).filter { it.substringAfterLast(" / ") == leaf }.singleOrNull() ?: return null
    return BankCategorySuggestion(path, false)
}
