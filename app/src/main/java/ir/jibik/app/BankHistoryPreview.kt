package ir.jibik.app

import ir.jibik.core.finance.Account
import java.math.BigInteger

internal data class BankBalancePreview(val accountTitle: String, val reported: Long,
    val local: Long, val atMillis: Long) {
    val difference: BigInteger get() = bankBalanceDifference(local, reported)
}
internal data class BankHistoryPreview(val novel: List<BankSmsMatch>, val alreadyHandled: Int,
    val comparisons: List<BankBalancePreview>)

internal fun latestBankComparisons(matches: List<BankSmsMatch>, profiles: List<BankSenderProfile>,
    accounts: List<Account>, balances: Map<String, Long>): List<BankBalancePreview> =
    matches.mapNotNull { match ->
        if (match.reportedBalanceToman == null) return@mapNotNull null
        val profile = resolveBankSender(profiles, match.sender, match.bankId, match.accountLast4) ?: return@mapNotNull null
        val account = accounts.firstOrNull { it.id == profile.accountId && !it.archived } ?: return@mapNotNull null
        val local = balances[account.id] ?: return@mapNotNull null
        account.id to BankBalancePreview(account.title, match.reportedBalanceToman, local, match.receivedAtMillis)
    }.groupBy({ it.first }, { it.second }).values.mapNotNull { rows ->
        val latest = rows.maxOf { it.atMillis }
        // Conflicting balances at the exact same timestamp are not resolved by input order.
        rows.filter { it.atMillis == latest }.distinct().singleOrNull()
    }.sortedByDescending { it.atMillis }
