package ir.jibik.app

import android.icu.util.Calendar
import android.icu.util.TimeZone
import android.icu.util.ULocale
import java.math.BigInteger
import java.text.NumberFormat
import java.util.Locale

internal fun recurringCashReserves(rules: List<RecurringRule>, decisions: List<RecurringDecision>, end: Long) =
    rules.filter { !it.income }.mapNotNull { rule ->
        val count = pendingOccurrenceCount(rule, decisions, end)
        if (count > 0) rule.title to (rule.amount.toBigInteger() * count.toBigInteger()) else null
    }

internal fun forecastMoney(amount: BigInteger): String =
    NumberFormat.getIntegerInstance(Locale("fa", "IR")).format(amount) + " تومان"

private fun forecastJulianDay(day: Long): Int =
    Calendar.getInstance(TimeZone.getTimeZone("Asia/Tehran"), ULocale("fa_IR@calendar=persian"))
        .apply { timeInMillis = day }.get(Calendar.JULIAN_DAY)

internal data class CashForecast(
    val available: BigInteger, val days: Int, val dailyLimit: BigInteger,
    val firstDeficitDay: Long?, val today: Long, val endExclusive: Long,
)

/** Conservative projection: no unreceived income and no invented future purchases.
 * Cumulative commitments are monotonic, so the first deficit can be found without
 * enumerating every day or guessing month lengths.
 */
internal fun cashForecast(balances: Map<String, Long>, goals: List<SavingsGoalEntity>,
    obligations: List<FinancialObligationEntity>, payments: List<ObligationPaymentEntity>,
    rules: List<RecurringRule>, decisions: List<RecurringDecision>, envelopes: List<MoneyEnvelope>,
    today: Long, endExclusive: Long): CashForecast {
    require(endExclusive > today)
    val days = forecastJulianDay(endExclusive - 1) - forecastJulianDay(today) + 1
    require(days > 0)
    fun availableAt(end: Long): BigInteger {
        val base = spendableBalance(balances, goals, obligations, payments, end, envelopes).available
        return recurringCashReserves(rules, decisions, end).fold(base) { sum, reserve -> sum - reserve.second }
    }
    val available = availableAt(endExclusive)
    val firstDeficit = if (available.signum() >= 0) null else {
        var low = 0
        var high = days - 1
        while (low < high) {
            val mid = (low + high) / 2
            val day = offsetJalaliDay(today, mid)
            if (availableAt(jalaliDueDayEndExclusive(day)).signum() < 0) high = mid else low = mid + 1
        }
        offsetJalaliDay(today, low)
    }
    return CashForecast(available, days, available.max(BigInteger.ZERO) / days.toBigInteger(),
        firstDeficit, today, endExclusive)
}

internal data class PurchaseImpact(val after: BigInteger, val dailyAfter: BigInteger,
    val accountFree: Long, val accountShortfall: BigInteger, val planShortfall: BigInteger) {
    val fits: Boolean get() = accountShortfall.signum() == 0 && planShortfall.signum() == 0
}

/** Do not release savings implicitly in a what-if calculation. */
internal fun simulatePurchase(forecast: CashForecast, accountId: String, amount: Long,
    balances: Map<String, Long>, goals: List<SavingsGoalEntity>, envelopes: List<MoneyEnvelope>): PurchaseImpact {
    require(amount > 0 && balances.containsKey(accountId))
    val balance = balances.getValue(accountId).coerceAtLeast(0L)
    val dedicated = goals.firstOrNull { it.accountId == accountId }
    val accountFree = if (dedicated != null) balance - minOf(balance, dedicated.targetToman)
        else envelopeFreeCapacity(accountId, balances, envelopes)
    val after = forecast.available - amount.toBigInteger()
    return PurchaseImpact(after, after.max(BigInteger.ZERO) / forecast.days.toBigInteger(), accountFree,
        (amount.toBigInteger() - accountFree.toBigInteger()).max(BigInteger.ZERO),
        after.negate().max(BigInteger.ZERO))
}
