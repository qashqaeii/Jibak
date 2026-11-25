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
    val month = currentJalaliMonth(now)
    val handled = decisions.map { it.ruleId to it.day }.toHashSet()
    return rules.flatMap { rule ->
        val first = currentJalaliMonth(rule.firstDay)
        val count = (month.year - first.year) * 12 + month.month - first.month
        if (count < 0) emptyList() else (0..count).mapNotNull { offset ->
            val target = currentJalaliMonth(offsetJalaliMonth(first.startMillis, offset))
            val day = rule.dueIn(target) ?: return@mapNotNull null
            if (day <= now && (rule.id to day) !in handled) rule to day else null
        }
    }.sortedWith(compareBy<Pair<RecurringRule, Long>> { it.second }.thenBy { it.first.id })
}
