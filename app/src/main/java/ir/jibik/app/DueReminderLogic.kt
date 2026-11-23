package ir.jibik.app

/** Small, privacy-first reminder summary. Titles, amounts and account IDs never leave Room. */
internal data class DueReminderSummary(
    val overdueCount: Int,
    val todayCount: Int,
    val upcomingCount: Int,
) {
    val totalCount: Int get() = overdueCount + todayCount + upcomingCount
}

/**
 * Evaluate *one* unpaid installment per obligation.
 *
 * All cutoffs are starts of Tehran calendar days, not arbitrary 24-hour shifts.
 * Pure comparison logic can be tested without Android ICU by injecting dueAt.
 */
internal fun summarizeDueReminders(
    obligations: List<FinancialObligationEntity>,
    payments: List<ObligationPaymentEntity>,
    todayStartMillis: Long,
    tomorrowStartMillis: Long,
    afterThreeDaysStartMillis: Long,
    dueAt: (FinancialObligationEntity, Int) -> Long = { item, index ->
        jalaliInstallmentDueDate(item.dueAtMillis, index)
    },
): DueReminderSummary {
    require(todayStartMillis < tomorrowStartMillis)
    require(tomorrowStartMillis < afterThreeDaysStartMillis)
    var overdue = 0
    var today = 0
    var upcoming = 0
    val paymentsByObligation = payments.groupBy { it.obligationId }
    for (obligation in obligations) {
        val pending = nextUnpaidInstallment(
            obligation, paymentsByObligation[obligation.id].orEmpty(),
        ) ?: continue
        val dueDay = dueAt(obligation, pending.index)
        when {
            dueDay < todayStartMillis -> overdue++
            dueDay < tomorrowStartMillis -> today++
            dueDay < afterThreeDaysStartMillis -> upcoming++
        }
    }
    return DueReminderSummary(overdue, today, upcoming)
}
