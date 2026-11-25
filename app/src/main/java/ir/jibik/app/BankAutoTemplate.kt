package ir.jibik.app

import ir.jibik.core.finance.LedgerKind
import ir.jibik.core.finance.LedgerRecord

/** Initial allowlisted format only. No general "contains withdrawal" automation.
 * A sample explicitly confirms the label/direction/unit. All transfers remain review-only.
 */
internal fun strictBankAutoTemplate(body: String, match: BankSmsMatch): String? {
    if (match.amountToman == null) return null
    if (match.bankId == "blu" && match.accountLast4.isEmpty()) return bluAutoTemplate(body, match)
    if (match.accountLast4.length != 4) return null
    explicitBankAutoTemplate(body, match)?.let { return it }
    learnedBankTemplate(body, match)?.let { return it }
    if (match.bankId != "melli") return null
    val lines = BankSmsParser.normalise(body).lines().map { it.trim() }.filter { it.isNotBlank() }
    if (lines.size !in 4..5 || lines[0] != "بانک ملی ایران") return null
    val event = Regex("^(خرید ?اینترنتی|خرید|برداشت|واریز)\\s*[:：]\\s*([0-9][0-9,]*)\\s*([+-])$").matchEntire(lines[1]) ?: return null
    val label = event.groupValues[1].replace(" ", "")
    val credit = event.groupValues[3] == "+"
    if (credit != (label == "واریز") || credit != match.income) return null
    val number = event.groupValues[2]
    if (!Regex("[0-9]+|[0-9]{1,3}(,[0-9]{3})+").matches(number)) return null
    if (number.replace(",", "").toLongOrNull() != match.amountRial) return null
    val account = Regex("^حساب\\s*[:：]\\s*([0-9]{4,24})$").matchEntire(lines[2]) ?: return null
    if (account.groupValues[1].takeLast(4) != match.accountLast4) return null
    if (!Regex("^مانده\\s*[:：]\\s*(?:[0-9]+|[0-9]{1,3}(?:,[0-9]{3})+)$").matches(lines[3])) return null
    if (lines.size == 5 && !Regex("^[0-9]{4}-[0-9]{1,2}:[0-9]{2}$").matches(lines[4])) return null
    return "melli-signed-rial-v1:" + label
}

internal const val BANK_AUTO_PREFIX = "bank-auto-"
internal fun isBankAutoId(id: String): Boolean = id.startsWith(BANK_AUTO_PREFIX) &&
    Regex("[0-9a-f]{64}").matches(id.removePrefix(BANK_AUTO_PREFIX))
internal fun isEditableBankAuto(record: LedgerRecord) =
    isBankAutoId(record.id) && record.kind != LedgerKind.TRANSFER
internal fun bankFallbackCategory(income: Boolean) = if (income) "سایر درآمدها" else "سایر هزینه‌ها"
internal fun needsBankAutoDetails(record: LedgerRecord) =
    isEditableBankAuto(record) && record.note.isBlank() && record.category == bankFallbackCategory(record.kind == LedgerKind.INCOME)

/** Similar existing cash movements (including voids) require explicit review.
 * Do not silently link or merge by amount; opposite direction on another account
 * might be an internal transfer. Identical legitimate payments remain reviewable.
 */
internal fun conflictsWithBankAuto(record: LedgerRecord, existing: List<LedgerRecord>): Boolean =
    existing.any { old ->
        old.amountToman == record.amountToman &&
            kotlin.math.abs(old.timestampMillis - record.timestampMillis) <= 15 * 60_000L &&
            (old.kind == LedgerKind.TRANSFER || old.fromAccountId == record.fromAccountId && record.fromAccountId != null ||
                old.toAccountId == record.toAccountId && record.toAccountId != null || old.kind != record.kind)
    }

/** Additional known-bank format: fixed financial label and explicit unit,
 * account and balance lines. Never treat a free-form message as a template.
 */
private fun explicitBankAutoTemplate(body: String, match: BankSmsMatch): String? {
    val lines = BankSmsParser.normalise(body).lines().map { it.trim() }.filter { it.isNotBlank() }
    if (lines.size != 5) return null
    val bank = BankSmsParser.banks.firstOrNull { it.id == match.bankId } ?: return null
    if (bank.aliases.none { BankSmsParser.normalise(it) == lines[0] }) return null
    val label = lines[1]
    if (label !in setOf("واریز", "برداشت", "خرید", "خرید اینترنتی")) return null
    if ((label == "واریز") != match.income) return null
    val amount = Regex("^مبلغ\\s*[:：]\\s*([0-9]+|[0-9]{1,3}(?:,[0-9]{3})+)\\s*(ریال|تومان)$")
        .matchEntire(lines[2]) ?: return null
    val raw = amount.groupValues[1].replace(",", "").toLongOrNull() ?: return null
    val unit = amount.groupValues[2]
    val rial = if (unit == "ریال") raw else runCatching { Math.multiplyExact(raw, 10L) }.getOrNull()
    if (rial != match.amountRial) return null
    val account = Regex("^حساب\\s*[:：]\\s*([0-9]{4,24})$").matchEntire(lines[3]) ?: return null
    if (account.groupValues[1].takeLast(4) != match.accountLast4) return null
    if (!Regex("^(مانده|موجودی)\\s*[:：]\\s*(?:[0-9]+|[0-9]{1,3}(?:,[0-9]{3})+)\\s*" + unit + "$").matches(lines[4])) return null
    return "explicit-v1:" + match.bankId + ":" + label.replace(" ", "") + ":" + unit
}

internal fun isSupportedAutoTemplate(bankId: String, template: String): Boolean {
    val labels = setOf("خریداینترنتی", "خرید", "برداشت", "واریز")
    return isLearnedBankTemplate(bankId, template) ||
        (bankId == "blu" && template in setOf("blu-rial-v1:credit", "blu-rial-v1:debit")) ||
        (bankId == "melli" && labels.any { template == "melli-signed-rial-v1:" + it }) ||
        labels.any { label -> listOf("ریال", "تومان").any { unit -> template == "explicit-v1:$bankId:$label:$unit" } }
}

/** Blu's confirmed no-account-number format. The greeting is constrained to
 * letters/spaces; only the exact financial sentence and one balance are allowed.
 */
private fun bluAutoTemplate(body: String, match: BankSmsMatch): String? {
    val lines = BankSmsParser.normalise(body).lines().map { it.trim() }.filter { it.isNotBlank() }
    if (lines.size != 6 || lines[0] !in setOf("بلو", "بلوبانک")) return null
    val income = when (lines[1]) { "واریز پول" -> true; "برداشت پول" -> false; else -> return null }
    if (income != match.income) return null
    val phrase = if (income) "به حساب شما نشست" else "از حساب شما پرید"
    val event = Regex("^[\\p{L} ]{1,70} عزیز,\\s*([0-9]+|[0-9]{1,3}(?:,[0-9]{3})+)\\s*ریال\\s+" + phrase + "\\.?$")
        .matchEntire(lines[2]) ?: return null
    if (event.groupValues[1].replace(",", "").toLongOrNull() != match.amountRial) return null
    if (!Regex("^موجودی\\s*:\\s*(?:[0-9]+|[0-9]{1,3}(?:,[0-9]{3})+)\\s*ریال$").matches(lines[3])) return null
    if (!Regex("^(?:[01]?[0-9]|2[0-3]):[0-5][0-9]$").matches(lines[4])) return null
    if (!Regex("^[0-9]{4}[./][0-9]{1,2}[./][0-9]{1,2}$").matches(lines[5])) return null
    return if (income) "blu-rial-v1:credit" else "blu-rial-v1:debit"
}
