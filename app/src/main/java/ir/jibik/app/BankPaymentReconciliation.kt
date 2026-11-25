package ir.jibik.app

import ir.jibik.core.finance.LedgerRecord
import ir.jibik.core.finance.LedgerKind

internal sealed interface BankPaymentTarget {
    data class Obligation(val id: String) : BankPaymentTarget
    data class Recurring(val id: String, val day: Long) : BankPaymentTarget
}
internal data class BankPaymentOption(val target: BankPaymentTarget, val label: String)

/** Suggestions only, never automatic settlement. Store revalidates all links on confirmation. */
internal fun bankPaymentOptions(record: LedgerRecord, obligations: List<FinancialObligationEntity>,
    payments: List<ObligationPaymentEntity>, rules: List<RecurringRule>, decisions: List<RecurringDecision>,
    now: Long): List<BankPaymentOption> {
    val account = record.fromAccountId ?: record.toAccountId ?: return emptyList()
    if (payments.any { it.transactionId == record.id } || decisions.any { it.transactionId == record.id })
        return emptyList()
    if (record.kind == LedgerKind.TRANSFER || record.amountToman <= 0) return emptyList()
    val handled = decisions.map { it.ruleId to it.day }.toSet()
    val nearby = 36L * 60 * 60 * 1000
    return buildList {
        obligations.forEach { obligation ->
            val remaining = runCatching { obligationProgress(obligation, payments).remainingToman }.getOrNull()
                ?: return@forEach
            if (record.matchesSettlement(obligation, account, remaining)) {
                add(BankPaymentOption(BankPaymentTarget.Obligation(obligation.id),
                    obligation.title + " · مانده " + formatToman(remaining)))
            }
        }
        rules.forEach { rule ->
            if (rule.accountId != account || rule.income != (record.kind == LedgerKind.INCOME)) return@forEach
            val from = (record.timestampMillis - nearby + 1).coerceAtLeast(1L)
            val end = minOf(now + 1, record.timestampMillis + nearby)
            rule.dueBetween(from, end).filter { (rule.id to it) !in handled }.forEach { day ->
                add(BankPaymentOption(BankPaymentTarget.Recurring(rule.id, day),
                    rule.title + " · " + formatJalaliDueDate(day) + " · مبلغ معمول " + formatToman(rule.amount)))
            }
        }
    }
}
