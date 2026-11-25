package ir.jibik.app

internal data class ReviewDue(val obligation: FinancialObligationEntity, val day: Long, val amount: Long)

internal fun reviewDueObligations(
    obligations: List<FinancialObligationEntity>, payments: List<ObligationPaymentEntity>, now: Long,
): List<ReviewDue> = obligations.mapNotNull { obligation ->
    val next = nextUnpaidInstallment(obligation, payments) ?: return@mapNotNull null
    val day = jalaliInstallmentDueDate(obligation.dueAtMillis, next.index)
    if (day > now) null else ReviewDue(obligation, day, next.amountToman - next.paidTowardToman)
}.sortedWith(compareBy<ReviewDue> { it.day }.thenBy { it.obligation.id })

internal fun reviewDueRecurring(
    rules: List<RecurringRule>, decisions: List<RecurringDecision>, now: Long,
): List<Pair<RecurringRule, Long>> {
    val handled = decisions.map { it.ruleId to it.day }.toHashSet()
    return rules.flatMap { rule ->
        rule.dueBetween(rule.firstDay, now + 1).filter { (rule.id to it) !in handled }.map { rule to it }
    }.sortedWith(compareBy<Pair<RecurringRule, Long>> { it.second }.thenBy { it.first.id })
}
