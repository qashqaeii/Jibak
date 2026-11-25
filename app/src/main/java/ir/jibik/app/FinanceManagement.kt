package ir.jibik.app

import androidx.room.*
import ir.jibik.core.finance.*
import java.math.BigInteger

internal val MANAGEMENT_TABLES = setOf("account_archives", "account_edit_events", "weekly_budgets", "loan_terms", "loan_payment_splits")
@Entity(tableName = "account_archives", foreignKeys = [ForeignKey(entity = FinancialAccountEntity::class,
    parentColumns = ["id"], childColumns = ["accountId"], onDelete = ForeignKey.RESTRICT)])
internal data class AccountArchive(@PrimaryKey val accountId: String, val atMillis: Long)
@Entity(tableName = "account_edit_events", foreignKeys = [ForeignKey(entity = FinancialAccountEntity::class,
    parentColumns = ["id"], childColumns = ["accountId"], onDelete = ForeignKey.RESTRICT)], indices = [Index("accountId")])
internal data class AccountEditEvent(@PrimaryKey(autoGenerate = true) val id: Long = 0,
    val accountId: String, val atMillis: Long, val action: String, val details: String)
@Entity(tableName = "weekly_budgets")
internal data class WeeklyBudget(@PrimaryKey val weekStart: Long, val limitToman: Long, val carryPrevious: Boolean)
@Entity(tableName = "loan_terms", foreignKeys = [ForeignKey(entity = FinancialObligationEntity::class,
    parentColumns = ["id"], childColumns = ["obligationId"], onDelete = ForeignKey.RESTRICT)])
internal data class LoanTerms(@PrimaryKey val obligationId: String, val principalToman: Long)
@Entity(tableName = "loan_payment_splits", foreignKeys = [ForeignKey(entity = ObligationPaymentEntity::class,
    parentColumns = ["transactionId"], childColumns = ["transactionId"], onDelete = ForeignKey.CASCADE)])
internal data class LoanPaymentSplit(@PrimaryKey val transactionId: String, val principalToman: Long, val interestToman: Long)

@Dao
internal interface FinanceManagementDao {
    @Query("SELECT * FROM account_archives") suspend fun archives(): List<AccountArchive>
    @Insert suspend fun archive(item: AccountArchive)
    @Query("DELETE FROM account_archives WHERE accountId = :id") suspend fun unarchive(id: String): Int
    @Insert suspend fun log(item: AccountEditEvent)
    @Query("SELECT * FROM account_edit_events WHERE accountId = :id ORDER BY id DESC") suspend fun accountHistory(id: String): List<AccountEditEvent>
    @Query("SELECT * FROM weekly_budgets ORDER BY weekStart DESC") suspend fun weeklyBudgets(): List<WeeklyBudget>
    @Upsert suspend fun saveWeeklyBudget(item: WeeklyBudget)
    @Query("SELECT * FROM loan_terms WHERE obligationId = :id") suspend fun loanTerms(id: String): LoanTerms?
    @Query("SELECT * FROM loan_terms") suspend fun allLoanTerms(): List<LoanTerms>
    @Upsert suspend fun saveLoanTerms(item: LoanTerms)
    @Query("SELECT * FROM loan_payment_splits") suspend fun splits(): List<LoanPaymentSplit>
    @Upsert suspend fun saveSplit(item: LoanPaymentSplit)
}

internal fun validateWeeklyBudget(item: WeeklyBudget) {
    require(item.weekStart > 0 && item.limitToman >= 0)
    val day = requireNotNull(parseJalaliDueDate(formatJalaliDueDate(item.weekStart)))
    require(day == item.weekStart && reportRange("WEEK", day).start == day)
}
internal data class WeeklyBudgetUsage(val base: BigInteger, val carried: BigInteger, val spent: BigInteger) {
    val remaining get() = base + carried - spent
}
internal fun weeklyBudgetUsage(start: Long, plans: List<WeeklyBudget>, records: List<LedgerRecord>): WeeklyBudgetUsage {
    val current = plans.firstOrNull { it.weekStart == start }
    val previousStart = offsetJalaliDay(start, -7)
    val previous = plans.firstOrNull { it.weekStart == previousStart }
    val priorSpent = reportTotals(records, ReportRange(previousStart, start)).expense
    val carry = if (current?.carryPrevious == true && previous != null)
        (previous.limitToman.toBigInteger() - priorSpent).max(BigInteger.ZERO) else BigInteger.ZERO
    return WeeklyBudgetUsage((current?.limitToman ?: 0).toBigInteger(), carry,
        reportTotals(records, ReportRange(start, offsetJalaliDay(start, 7))).expense)
}
internal data class LoanDetails(val terms: LoanTerms?, val payments: List<ObligationPaymentEntity>,
    val splits: Map<String, LoanPaymentSplit>, val paidPrincipal: BigInteger, val paidInterest: BigInteger,
    val unclassified: BigInteger, val total: Long)
/** Bounds derived from the contract and recorded payments, not a guessed allocation. */
internal data class PrincipalBalance(val minimum: BigInteger, val maximum: BigInteger) {
    init { require(minimum.signum() >= 0 && maximum >= minimum) }
    val exact get() = minimum == maximum
}
internal fun LoanDetails.remainingPrincipal(): PrincipalBalance? {
    val principal = terms?.principalToman?.toBigInteger() ?: return null
    val knownRemaining = principal - paidPrincipal
    val remainingInterestCapacity = total.toBigInteger() - principal - paidInterest
    // Unclassified payments must fit within remaining principal and interest capacity.
    val minPrincipalPaid = (unclassified - remainingInterestCapacity).max(BigInteger.ZERO)
    val maxPrincipalPaid = unclassified.min(knownRemaining)
    return PrincipalBalance(knownRemaining - maxPrincipalPaid, knownRemaining - minPrincipalPaid)
}
internal fun loanDetailsFor(obligation: FinancialObligationEntity, payments: List<ObligationPaymentEntity>,
    terms: LoanTerms?, allSplits: List<LoanPaymentSplit>): LoanDetails {
    require(obligation.kind == "LOAN")
    obligationProgress(obligation, payments)
    require(terms == null || (terms.obligationId == obligation.id && terms.principalToman in 1..obligation.totalToman))
    val paymentIds = payments.map { it.transactionId }.toSet()
    val splits = allSplits.filter { it.transactionId in paymentIds }.associateBy { it.transactionId }
    require(terms != null || splits.isEmpty())
    var principal = BigInteger.ZERO; var interest = BigInteger.ZERO; var unclassified = BigInteger.ZERO
    payments.forEach { payment ->
        val split = splits[payment.transactionId]
        if (split == null) unclassified += payment.amountToman.toBigInteger()
        else {
            require(split.principalToman >= 0 && split.interestToman >= 0 &&
                split.principalToman.toBigInteger() + split.interestToman.toBigInteger() == payment.amountToman.toBigInteger())
            principal += split.principalToman.toBigInteger(); interest += split.interestToman.toBigInteger()
        }
    }
    if (terms != null) {
        require(principal <= terms.principalToman.toBigInteger())
        require(interest <= (obligation.totalToman - terms.principalToman).toBigInteger())
    }
    return LoanDetails(terms, payments, splits, principal, interest, unclassified, obligation.totalToman)
}

internal suspend fun validateManagementData(room: JibikDatabase) {
    val dao = room.managementDao()
    val accounts = room.accountDao().list().map { Account(it.id, it.title, it.openingBalanceToman) }
    val records = room.ledgerDao().getAll().map { it.toRecord() }
    val balances = FinanceEngine.balances(accounts, records)
    val archived = dao.archives()
    require(archived.all { it.accountId != "wallet" && it.atMillis > 0 && balances[it.accountId] == 0L })
    val archivedIds = archived.map { it.accountId }.toSet()
    require(room.savingsGoalDao().list().none { it.accountId in archivedIds })
    require(room.envelopeDao().list().none { it.accountId in archivedIds })
    require(room.recurringDao().rules().none { it.accountId in archivedIds })
    dao.weeklyBudgets().forEach(::validateWeeklyBudget)
    val obligations = room.obligationDao().list()
    val payments = room.obligationPaymentDao().list()
    val terms = dao.allLoanTerms()
    val splits = dao.splits()
    require(terms.all { term -> obligations.any { it.id == term.obligationId && it.kind == "LOAN" } })
    require(splits.all { split -> payments.any { p -> p.transactionId == split.transactionId && terms.any { it.obligationId == p.obligationId } } })
    obligations.filter { it.kind == "LOAN" }.forEach { item ->
        loanDetailsFor(item, payments.filter { it.obligationId == item.id }, terms.firstOrNull { it.obligationId == item.id }, splits)
    }
}
