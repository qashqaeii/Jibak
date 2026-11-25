package ir.jibik.app

import ir.jibik.core.finance.Account
import ir.jibik.core.finance.LedgerKind
import ir.jibik.core.finance.LedgerRecord

internal data class BankTransferPair(
    val debit: BankSmsCandidateEntity,
    val credit: BankSmsCandidateEntity,
    val fromId: String,
    val toId: String,
) {
    val amountToman get() = debit.amountRial / 10
}

/** Legacy candidates lack a persisted sender key: suggest only when the
 * bank + last4 map to exactly one enabled profile and one active account.
 * Same amount/time is a suggestion, NEVER proof of an internal transfer.
 */
internal fun candidateMappedAccount(candidate: BankSmsCandidateEntity,
    profiles: List<BankSenderProfile>, accounts: List<Account>): String? {
    if (candidate.accountLast4.length != 4) return null
    val profile = profiles.filter { it.enabled && it.bankId == candidate.bankId &&
        it.accountLast4 == candidate.accountLast4 }.singleOrNull() ?: return null
    return profile.accountId.takeIf { id -> accounts.any { it.id == id && !it.archived } }
}

internal fun bankTransferPairs(candidates: List<BankSmsCandidateEntity>, profiles: List<BankSenderProfile>,
    accounts: List<Account>, records: List<LedgerRecord>): List<BankTransferPair> {
    val eligible = candidates.filter { candidate ->
        candidate.amountRial > 0 && candidate.amountRial % 10L == 0L && candidate.receivedAtMillis > 0 &&
            (candidate.state == "PENDING" && candidate.linkedTransactionId == null ||
                candidate.state == "REGISTERED" && records.any { row ->
                    row.id == candidate.linkedTransactionId && row.id.startsWith(BANK_AUTO_PREFIX) &&
                        row.kind == if (candidate.income) LedgerKind.INCOME else LedgerKind.EXPENSE
                })
    }.mapNotNull { row -> candidateMappedAccount(row, profiles, accounts)?.let { row to it } }
    val choices = mutableListOf<BankTransferPair>()
    val credits = eligible.filter { it.first.income }.groupBy { it.first.amountRial }
    for ((debit, from) in eligible.filterNot { it.first.income }) {
        for ((credit, to) in credits[debit.amountRial].orEmpty()) {
            if (from == to || kotlin.math.abs(debit.receivedAtMillis - credit.receivedAtMillis) > 5 * 60_000L ||
                (debit.state != "PENDING" && credit.state != "PENDING")) continue
            choices.add(BankTransferPair(debit, credit, from, to))
        }
    }
    val counts = choices.flatMap { listOf(it.debit.id, it.credit.id) }.groupingBy { it }.eachCount()
    return choices.filter { counts[it.debit.id] == 1 && counts[it.credit.id] == 1 }
}
