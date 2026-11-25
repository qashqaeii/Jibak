package ir.jibik.app

import java.math.BigInteger

internal data class CashReserve(val title: String, val amount: Long)
internal data class SpendableBalance(
    val available: BigInteger,
    val goals: List<CashReserve>,
    val dues: List<CashReserve>,
)

internal fun spendableBalance(
    balances: Map<String, Long>,
    goals: List<SavingsGoalEntity>,
    obligations: List<FinancialObligationEntity>,
    payments: List<ObligationPaymentEntity>,
    endExclusive: Long,
): SpendableBalance {
    val reserved = goals.mapNotNull { goal ->
        val amount = balances.getOrDefault(goal.accountId, 0L).coerceIn(0L, goal.targetToman)
        if (amount > 0) CashReserve(goal.title, amount) else null
    }
    val byObligation = payments.groupBy { it.obligationId }
    val dues = obligations.filter { it.kind != "RECEIVABLE" && it.dueAtMillis < endExclusive }
        .sortedBy { it.dueAtMillis }.mapNotNull { item ->
            val paid = obligationProgress(item, byObligation[item.id].orEmpty()).paidToman
            val count = item.installmentCount
            // Find how many monthly installments fall before the exclusive boundary.
            var low = 0
            var high = count
            while (low < high) {
                val mid = (low + high) / 2
                if (jalaliInstallmentDueDate(item.dueAtMillis, mid) < endExclusive) low = mid + 1
                else high = mid
            }
            val scheduled = (item.totalToman / count) * low + minOf(item.totalToman % count, low.toLong())
            val due = scheduled - paid
            if (due > 0) CashReserve(item.title, due) else null
        }
    val cash = balances.values.fold(BigInteger.ZERO) { sum, value -> sum + value.toBigInteger() }
    return SpendableBalance((reserved + dues).fold(cash) { value, reserve ->
        value - reserve.amount.toBigInteger()
    }, reserved, dues)
}
