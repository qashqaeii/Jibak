package ir.jibik.app

import android.content.Context
import androidx.room.withTransaction
import ir.jibik.core.finance.Account
import ir.jibik.core.finance.FinanceEngine
import ir.jibik.core.finance.LedgerKind
import ir.jibik.core.finance.LedgerRecord
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray

/**
 * Room is the source of truth for transactions. Legacy SharedPreferences data
 * stays untouched until migration is verified and explicitly marked complete.
 * Preferences still hold onboarding and the budget setting for this milestone.
 */
internal class LocalLedgerStore(context: Context, private val database: JibikDatabase = JibikDatabase.get(context)) {
    private val preferences = context.applicationContext.getSharedPreferences(
        "jibik_local_v1", Context.MODE_PRIVATE
    )
    private val dao = database.ledgerDao()
    private val accountDao = database.accountDao()
    private val customCategoryDao = database.customCategoryDao()
    private val categoryBudgetDao = database.categoryBudgetDao()
    private val obligationDao = database.obligationDao()
    private val obligationPaymentDao = database.obligationPaymentDao()
    private val bankSmsDao = database.bankSmsCandidateDao()

    fun hasFinishedWelcome(): Boolean = preferences.getBoolean("welcome_finished", false)

    fun finishWelcome() {
        check(preferences.edit().putBoolean("welcome_finished", true).commit()) {
            "Unable to persist welcome state"
        }
    }

    /** Home-screen privacy is user-controlled and survives app restarts. */
    fun areHomeAmountsVisible(): Boolean =
        preferences.getBoolean("home_amounts_visible", true)

    fun setHomeAmountsVisible(visible: Boolean) {
        check(preferences.edit().putBoolean("home_amounts_visible", visible).commit()) {
            "Unable to save home privacy preference"
        }
    }

    suspend fun budgetToman(): Long = dao.budget() ?: preferences.getLong("budget_toman", 0L)

    suspend fun updateBudgetToman(value: Long) {
        require(value >= 0L)
        dao.saveBudget(MonthlyBudget(amount = value))
    }

    suspend fun backup() = database.withTransaction {
        FinancialBackup.snapshot(database, budgetToman())
    }

    suspend fun restoreBackup(root: org.json.JSONObject) = database.withTransaction {
        check(budgetToman() == 0L)
        FinancialBackup.restore(database, root)
        updateBudgetToman(root.getLong("budget"))
    }

    private suspend fun ensureDefaultWallet() {
        accountDao.insertDefault(FinancialAccountEntity("wallet", "کیف پول جیبیک", 0L))
    }

    suspend fun loadAccounts(): List<Account> = withContext(Dispatchers.IO) {
        ensureDefaultWallet()
        val archived = database.managementDao().archives().map { it.accountId }.toSet()
        accountDao.list().map { it.toAccount().copy(archived = it.id in archived) }
    }

    suspend fun addAccount(account: Account) = withContext(Dispatchers.IO) {
        require(account.id.isNotBlank() && account.id != "wallet" && !account.archived)
        require(account.title.isNotBlank() && account.title.length <= 48)
        require(account.openingBalanceToman >= 0L)
        database.withTransaction {
            val current = accountDao.list()
            check(current.none { it.id == account.id || it.title == account.title.trim() }) {
                "Duplicate financial account"
            }
            // Validates the new opening balance against all existing transactions.
            val updated = current.map { it.toAccount() } + account
            FinanceEngine.balances(updated, dao.getAll().map { it.toRecord() })
            accountDao.insert(account.toEntity())
        }
    }

    private suspend fun requireActiveAccounts(vararg ids: String?) {
        val archived = database.managementDao().archives().map { it.accountId }.toSet()
        require(ids.filterNotNull().none { it in archived }) { "Account archived" }
    }

    suspend fun accountHistory(id: String) = database.managementDao().accountHistory(id)

    suspend fun renameAccount(expected: Account, title: String) = database.withTransaction {
        val clean = title.trim()
        require(clean.length in 1..48)
        val current = loadAccounts().first { it.id == expected.id }
        check(current == expected)
        require(loadAccounts().none { it.id != current.id && it.title == clean })
        if (clean != current.title) {
            check(accountDao.rename(current.id, clean) == 1)
            database.managementDao().log(AccountEditEvent(accountId = current.id, atMillis = System.currentTimeMillis(), action = "RENAME", details = current.title + " ← " + clean))
        }
    }

    suspend fun archiveAccount(expected: Account, archive: Boolean) = database.withTransaction {
        val current = loadAccounts().first { it.id == expected.id }
        check(current == expected && current.id != "wallet" && current.archived != archive)
        val management = database.managementDao()
        if (archive) {
            val balances = FinanceEngine.balances(loadAccounts(), dao.getAll().map { it.toRecord() })
            require(balances.getValue(current.id) == 0L) { "Only zero-balance accounts can be archived" }
            require(database.savingsGoalDao().list().none { it.accountId == current.id })
            require(database.envelopeDao().list().none { it.accountId == current.id })
            require(database.recurringDao().rules().none { it.accountId == current.id })
            management.archive(AccountArchive(current.id, System.currentTimeMillis()))
        } else check(management.unarchive(current.id) == 1)
        management.log(AccountEditEvent(accountId = current.id, atMillis = System.currentTimeMillis(), action = if (archive) "ARCHIVE" else "RESTORE", details = current.title))
    }

    suspend fun reconcileAccount(expected: Account, expectedBalance: Long, actualBalance: Long, reason: String, transactionId: String) = database.withTransaction {
        val clean = reason.trim(); require(clean.length in 3..200)
        val accounts = loadAccounts()
        val current = accounts.first { it.id == expected.id }
        check(current == expected && !current.archived)
        val before = FinanceEngine.balances(accounts, dao.getAll().map { it.toRecord() }).getValue(current.id)
        check(before == expectedBalance)
        val delta = actualBalance.toBigInteger() - before.toBigInteger()
        require(delta.signum() != 0)
        val credit = delta.signum() > 0
        val row = LedgerRecord(transactionId, if (credit) LedgerKind.INCOME else LedgerKind.EXPENSE, delta.abs().longValueExact(),
            fromAccountId = current.id.takeUnless { credit }, toAccountId = current.id.takeIf { credit }, category = "اصلاح موجودی", note = clean, timestampMillis = System.currentTimeMillis())
        append(row)
        database.managementDao().log(AccountEditEvent(accountId = current.id, atMillis = row.timestampMillis, action = "RECONCILE", details = formatToman(before) + " ← " + formatToman(actualBalance) + " · " + clean + " · " + row.id))
    }

    suspend fun weeklyBudgets() = database.managementDao().weeklyBudgets()
    suspend fun saveWeeklyBudget(item: WeeklyBudget) = database.withTransaction {
        validateWeeklyBudget(item)
        database.managementDao().saveWeeklyBudget(item)
    }

    suspend fun loanDetails(id: String): LoanDetails = database.withTransaction {
        val obligation = obligationDao.list().first { it.id == id && it.kind == "LOAN" }
        loanDetailsFor(obligation, obligationPaymentDao.forObligation(id), database.managementDao().loanTerms(id), database.managementDao().splits())
    }

    suspend fun saveLoanTerms(id: String, principal: Long) = database.withTransaction {
        val obligation = obligationDao.list().first { it.id == id && it.kind == "LOAN" }
        val terms = LoanTerms(id, principal)
        loanDetailsFor(obligation, obligationPaymentDao.forObligation(id), terms, database.managementDao().splits())
        database.managementDao().saveLoanTerms(terms)
    }

    suspend fun classifyLoanPayment(id: String, transactionId: String, principal: Long) = database.withTransaction {
        val obligation = obligationDao.list().first { it.id == id && it.kind == "LOAN" }
        val payments = obligationPaymentDao.forObligation(id)
        val payment = payments.first { it.transactionId == transactionId }
        require(principal in 0..payment.amountToman)
        val terms = requireNotNull(database.managementDao().loanTerms(id))
        val split = LoanPaymentSplit(transactionId, principal, payment.amountToman - principal)
        loanDetailsFor(obligation, payments, terms, database.managementDao().splits().filterNot { it.transactionId == transactionId } + split)
        val old = database.managementDao().splits().firstOrNull { it.transactionId == transactionId }
        if (old != split) {
            database.managementDao().saveSplit(split)
            dao.recordFinancialEdit(FinancialEditHistory(transactionId = transactionId, atMillis = System.currentTimeMillis(), action = "LOAN_SPLIT",
                details = "اصل و سود قبلی: " + (old?.let { formatToman(it.principalToman) + " / " + formatToman(it.interestToman) } ?: "نامشخص") +
                    "\nاصل و سود جدید: " + formatToman(split.principalToman) + " / " + formatToman(split.interestToman)))
        }
    }

    suspend fun load(): List<LedgerRecord> = withContext(Dispatchers.IO) {
        ensureDefaultWallet()
        migrateLegacyIfNeeded()
        dao.getAll().map { it.toRecord() }
    }

    suspend fun append(record: LedgerRecord) = withContext(Dispatchers.IO) {
        require(record.id.isNotBlank() && record.amountToman > 0L)
        ensureDefaultWallet()
        migrateLegacyIfNeeded()
        database.withTransaction {
            val accounts = accountDao.list().map { it.toAccount() }
            val allRecords = dao.getAll().map { it.toRecord() } + record
            FinanceEngine.balances(accounts, allRecords)
            requireActiveAccounts(record.fromAccountId, record.toAccountId)
            dao.insert(record.toEntity())
        }
    }

    /**
     * One ledger entry + many receipt lines in the SAME SQLite transaction.
     * Rejected/failed receipts cannot leave partial deductions behind.
     */
    suspend fun appendItemizedPurchase(record: LedgerRecord, lines: List<PurchaseLine>) =
        withContext(Dispatchers.IO) {
            validateItemizedPurchase(record, lines)
            ensureDefaultWallet()
            migrateLegacyIfNeeded()
            database.withTransaction {
                val accounts = accountDao.list().map { it.toAccount() }
                val existing = dao.getAll().map { it.toRecord() }
                FinanceEngine.balances(accounts, existing + record)
                requireActiveAccounts(record.fromAccountId, record.toAccountId)
            dao.insert(record.toEntity())
                database.purchaseLineDao().insertAll(lines.mapIndexed { index, line ->
                    PurchaseLineEntity(
                        transactionId = record.id, lineIndex = index,
                        title = line.title.trim(), category = line.category.trim(),
                        quantity = line.quantity, unitPriceToman = line.unitPriceToman,
                        unit = line.unit,
                    )
                })
            }
        }

    suspend fun voided() = dao.voided().map { it.toRecord() }

    suspend fun setVoided(record: LedgerRecord, void: Boolean) = database.withTransaction {
        requireActiveAccounts(record.fromAccountId, record.toAccountId)
        if (void) {
            check(dao.find(record.id)?.toRecord() == record)
            check(obligationPaymentDao.list().none { it.transactionId == record.id })
            check(!database.assetDao().isLinked(record.id)) { "Asset trade is immutable" }
            check(!bankSmsDao.isLinked(record.id))
            check(!database.recurringDao().isLinked(record.id)) { "Transaction is linked to a recurring occurrence" }
            dao.markVoid(LedgerVoid(record.id, System.currentTimeMillis()))
        } else {
            check(dao.voided().firstOrNull { it.id == record.id }?.toRecord() == record)
            check(dao.unvoid(record.id) == 1)
        }
        FinanceEngine.balances(accountDao.list().map { it.toAccount() }, dao.getAll().map { it.toRecord() })
    }

    suspend fun isBankLinked(id: String) = bankSmsDao.isLinked(id)

    suspend fun allRecurringDecisions() = database.recurringDao().decisions(0, Long.MAX_VALUE)
    suspend fun recurringRules() = database.recurringDao().rules()
    suspend fun recurringDecisions(month: JalaliMonthPeriod) =
        database.recurringDao().decisions(month.startMillis, month.endExclusiveMillis)

    suspend fun addRecurring(rule: RecurringRule) = database.withTransaction {
        require(rule.title.isNotBlank() && rule.title.length <= 70 && rule.amount > 0 && rule.firstDay > 0)
        require(rule.frequency in RECURRING_FREQUENCIES && rule.interval in 1..365)
        require(rule.endDay == null || rule.endDay >= rule.firstDay)
        require(rule.category.isBlank() || isSelectableCategory(rule.category, rule.income, customCategoryDao.list()))
        requireActiveAccounts(rule.accountId)
        require(accountDao.list().any { it.id == rule.accountId })
        database.recurringDao().add(rule)
    }

    suspend fun stopRecurring(id: String) = database.recurringDao().stop(id, System.currentTimeMillis())

    /** Historical occurrences keep their original rule and amount; edits create a successor. */
    suspend fun replaceRecurring(id: String, replacement: RecurringRule) = database.withTransaction {
        val recurring = database.recurringDao()
        val previous = recurring.rules().first { it.id == id }
        val now = System.currentTimeMillis()
        require(previous.stoppedAt == null && replacement.id != id && replacement.stoppedAt == null)
        require(replacement.firstDay >= jalaliDueDayEndExclusive(now))
        addRecurring(replacement)
        recurring.stop(id, now)
    }

    suspend fun decideRecurring(ruleId: String, day: Long, amount: Long?) = database.withTransaction {
        val recurring = database.recurringDao()
        val rule = recurring.rules().first { it.id == ruleId }
        require(rule.isDue(day) && day <= System.currentTimeMillis())
        val recordId = if (amount != null) {
            require(amount > 0)
            val record = LedgerRecord(id = java.util.UUID.randomUUID().toString(),
                kind = if (rule.income) LedgerKind.INCOME else LedgerKind.EXPENSE,
                amountToman = amount, fromAccountId = rule.accountId.takeUnless { rule.income },
                toAccountId = rule.accountId.takeIf { rule.income },
                category = rule.category.ifBlank { if (rule.income) "سایر درآمدها" else "سایر هزینه‌ها" },
                note = rule.title, timestampMillis = System.currentTimeMillis())
            FinanceEngine.balances(accountDao.list().map { it.toAccount() }, dao.getAll().map { it.toRecord() } + record)
            requireActiveAccounts(record.fromAccountId, record.toAccountId)
            dao.insert(record.toEntity())
            record.id
        } else null
        recurring.decide(RecurringDecision(ruleId, day, recordId, amount))
    }

    suspend fun linkRecurring(ruleId: String, day: Long, existing: LedgerRecord) = database.withTransaction {
        val recurring = database.recurringDao()
        val rule = recurring.rules().first { it.id == ruleId }
        require(rule.isDue(day) && day <= System.currentTimeMillis())
        require(existing.kind == if (rule.income) LedgerKind.INCOME else LedgerKind.EXPENSE)
        require(existing.amountToman > 0 && (existing.fromAccountId ?: existing.toAccountId) == rule.accountId)
        require(kotlin.math.abs(existing.timestampMillis - day) < 36L * 60 * 60 * 1000)
        require(dao.find(existing.id)?.toRecord() == existing)
        // Bank evidence may coexist with settlement; independent commitments cannot reuse a payment.
        check(!database.assetDao().isLinked(existing.id))
        check(!recurring.isLinked(existing.id)) { "Transaction already linked to a recurring occurrence" }
        check(obligationPaymentDao.list().none { it.transactionId == existing.id }) { "Transaction already settles an obligation" }
        require(recurring.decisions(0, Long.MAX_VALUE).none { it.ruleId == ruleId && it.day == day })
        recurring.decide(RecurringDecision(ruleId, day, existing.id, existing.amountToman))
    }

    suspend fun revisions(id: String) = withContext(Dispatchers.IO) { dao.revisions(id) }
    suspend fun financialEdits(id: String) = withContext(Dispatchers.IO) { dao.financialEdits(id) }
    suspend fun inspectIntegrity() = FinancialIntegrity.inspect(database)

    /** Reopen linked commitments and bank suggestions without moving money. */
    suspend fun detachPaymentLinks(record: LedgerRecord) = database.withTransaction {
        val saved = dao.find(record.id)?.toRecord()
            ?: dao.voided().firstOrNull { it.id == record.id }?.toRecord()
        check(saved == record) { "Transaction changed" }
        val obligations = obligationPaymentDao.list().filter { it.transactionId == record.id }
        val recurring = database.recurringDao().linked(record.id)
        val banks = bankSmsDao.linked(record.id)
        check(obligations.isNotEmpty() || recurring.isNotEmpty() || banks.isNotEmpty())
        val obligationTitles = obligationDao.list().associate { it.id to it.title }
        val ruleTitles = database.recurringDao().rules().associate { it.id to it.title }
        val details = buildString {
            val splits = database.managementDao().splits().associateBy { it.transactionId }
            obligations.forEach { row ->
                splits[row.transactionId]?.let { appendLine("اصل: " + it.principalToman + " · سود: " + it.interestToman) }
                appendLine("تعهد: " + obligationTitles[row.obligationId].orEmpty() + " · " + row.obligationId + " · " + row.amountToman + " تومان · " + row.paidAtMillis)
            }
            recurring.forEach { row ->
                appendLine("نوبت: " + ruleTitles[row.ruleId].orEmpty() + " · " + row.ruleId + " · " + formatJalaliDueDate(row.day) + " · " + row.day + " · " + row.actual)
            }
            banks.forEach { row ->
                appendLine("پیام بانکی: " + row.bankTitle + " · " + row.id + " · " + row.amountRial + " ریال · " + row.receivedAtMillis)
            }
        }
        dao.recordFinancialEdit(FinancialEditHistory(transactionId = record.id,
            atMillis = System.currentTimeMillis(), action = "DETACH", details = details))
        check(obligationPaymentDao.detach(record.id) == obligations.size)
        check(database.recurringDao().detach(record.id) == recurring.size)
        check(bankSmsDao.detach(record.id) == banks.size)
    }

    suspend fun correctReceipt(original: LedgerRecord, corrected: LedgerRecord,
        oldLines: List<PurchaseLine>, lines: List<PurchaseLine>) = database.withTransaction {
        requireActiveAccounts(original.fromAccountId, original.toAccountId, corrected.fromAccountId, corrected.toAccountId)
        require(original.id == corrected.id && original.kind == LedgerKind.EXPENSE)
        validateItemizedPurchase(corrected, lines)
        val previous = requireNotNull(dao.find(original.id))
        check(previous.toRecord() == original)
        check(!database.assetDao().isLinked(original.id))
        check(!bankSmsDao.isLinked(original.id) && !database.recurringDao().isLinked(original.id))
        check(obligationPaymentDao.list().none { it.transactionId == original.id })
        val purchaseDao = database.purchaseLineDao()
        val stored = purchaseDao.forTransaction(original.id).map {
            PurchaseLine(it.title, it.category, it.quantity, it.unitPriceToman, it.unit)
        }
        check(stored.isNotEmpty() && stored == oldLines) { "Receipt changed" }
        val normalized = lines.map { it.copy(title = it.title.trim(), category = it.category.trim()) }
        val categories = customCategoryDao.list()
        require(normalized.all { line -> stored.any { it.category == line.category } ||
            isSelectableCategory(line.category, false, categories) })
        if (original != corrected || stored != normalized) {
            FinanceEngine.balances(accountDao.list().map { it.toAccount() },
                dao.getAll().map { if (it.id == original.id) corrected else it.toRecord() })
            val now = System.currentTimeMillis()
            dao.recordRevision(LedgerRevision(transactionId = original.id, editedAt = now, previous = previous))
            val details = stored.joinToString("\n") {
                it.title + " · " + it.category + " · " + it.quantity + " " + it.unit + " × " + it.unitPriceToman + " تومان"
            }
            dao.recordFinancialEdit(FinancialEditHistory(transactionId = original.id,
                atMillis = now, action = "RECEIPT", details = details))
            check(dao.update(corrected.toEntity()) == 1)
            check(purchaseDao.removeForTransaction(original.id) == stored.size)
            purchaseDao.insertAll(normalized.mapIndexed { index, line ->
                PurchaseLineEntity(original.id, index, line.title, line.category, line.quantity,
                    line.unitPriceToman, line.unit)
            })
        }
    }

    suspend fun correct(original: LedgerRecord, corrected: LedgerRecord) = withContext(Dispatchers.IO) {
        require(original.id == corrected.id)
        database.withTransaction {
            requireActiveAccounts(original.fromAccountId, original.toAccountId, corrected.fromAccountId, corrected.toAccountId)
            val previous = requireNotNull(dao.find(original.id))
            check(previous.toRecord() == original && !bankSmsDao.isLinked(original.id))
            check(!database.assetDao().isLinked(original.id)) { "Asset trade is immutable" }
            check(database.purchaseLineDao().forTransaction(original.id).isEmpty())
            check(obligationPaymentDao.list().none { it.transactionId == original.id })
            check(!database.recurringDao().isLinked(original.id)) { "Transaction is linked to a recurring occurrence" }
            if (original != corrected) {
                val records = dao.getAll().map { if (it.id == original.id) corrected else it.toRecord() }
                FinanceEngine.balances(accountDao.list().map { it.toAccount() }, records)
                dao.recordRevision(LedgerRevision(transactionId = original.id,
                    editedAt = System.currentTimeMillis(), previous = previous))
                check(dao.update(corrected.toEntity()) == 1)
            }
        }
    }

    suspend fun loadPurchaseLines(transactionId: String): List<PurchaseLine> =
        withContext(Dispatchers.IO) {
            database.purchaseLineDao().forTransaction(transactionId).map {
                PurchaseLine(it.title, it.category, it.quantity, it.unitPriceToman, it.unit)
            }
        }

    suspend fun loadAllPurchaseLines(): Map<String, List<PurchaseLine>> =
        withContext(Dispatchers.IO) {
            database.purchaseLineDao().all()
                .groupBy { it.transactionId }
                .mapValues { (_, rows) ->
                    rows.map { PurchaseLine(it.title, it.category, it.quantity, it.unitPriceToman, it.unit) }
                }
        }

    suspend fun loadCustomCategories(): List<CustomCategoryEntity> =
        withContext(Dispatchers.IO) { customCategoryDao.list() }

    suspend fun addCustomCategory(item: CustomCategoryEntity) =
        withContext(Dispatchers.IO) {
            database.withTransaction {
                val existing = customCategoryDao.list()
                val cleaned = item.copy(
                    title = item.title.trim(), parentPath = item.parentPath.trim(),
                )
                val invalid = customCategoryValidation(cleaned, existing)
                require(invalid == null) { invalid ?: "Invalid category" }
                customCategoryDao.insert(cleaned)
            }
        }

    suspend fun setCustomCategoryArchived(id: String, archived: Boolean) =
        withContext(Dispatchers.IO) {
            database.withTransaction {
                val all = customCategoryDao.list()
                val row = all.firstOrNull { it.id == id }
                    ?: error("Category not found")
                if (archived) {
                    check(all.none {
                        !it.archived && it.parentPath == row.displayName
                    }) { "Archive child categories before their parent" }
                } else if (row.parentPath.isNotBlank()) {
                    val sameKindIncome = row.kind == "INCOME"
                    check(isSelectableCategory(row.parentPath, sameKindIncome, all)) {
                        "Restore the parent category first"
                    }
                }
                customCategoryDao.setArchived(id, archived)
            }
        }

    suspend fun loadCategoryBudgets(): List<CategoryBudgetEntity> =
        withContext(Dispatchers.IO) { categoryBudgetDao.list() }

    suspend fun saveCategoryBudget(
        cap: CategoryBudgetEntity,
    ) = withContext(Dispatchers.IO) {
        require(cap.limitToman > 0L && cap.warningPercent in 1..100)
        val categories = customCategoryDao.list()
        require(isSelectableCategory(cap.categoryPath, false, categories)) {
            "Choose an active expense category"
        }
        categoryBudgetDao.upsert(cap)
    }

    suspend fun removeCategoryBudget(path: String) = withContext(Dispatchers.IO) {
        categoryBudgetDao.remove(path)
    }

    suspend fun loadObligations(): List<FinancialObligationEntity> =
        withContext(Dispatchers.IO) { obligationDao.list() }

    suspend fun loadObligationPayments(): List<ObligationPaymentEntity> =
        withContext(Dispatchers.IO) { obligationPaymentDao.list() }

    suspend fun addObligation(item: FinancialObligationEntity) =
        withContext(Dispatchers.IO) {
            validateObligation(item)
            // No synthetic income/expense: creating a debt is only a promise/receivable.
            obligationDao.insert(item.copy(title = item.title.trim(), note = item.note.trim()))
        }

    /**
     * Money moves exactly once when user explicitly confirms an obligation settlement.
     * Ledger row and its attachment are inserted inside one SQLite transaction.
     * A failed/duplicate payment cannot leave either an orphan or partial charge.
     */
    suspend fun settleObligation(
        obligationId: String,
        accountId: String,
        amountToman: Long,
        transactionId: String,
        atMillis: Long = System.currentTimeMillis(),
    ): LedgerRecord = withContext(Dispatchers.IO) {
        require(amountToman > 0L && atMillis > 0L)
        ensureDefaultWallet()
        migrateLegacyIfNeeded()
        database.withTransaction {
            val item = obligationDao.list().firstOrNull { it.id == obligationId }
                ?: error("Obligation not found")
            val paid = obligationPaymentDao.forObligation(obligationId)
            val progress = obligationProgress(item, paid)
            require(amountToman <= progress.remainingToman) { "Payment exceeds remaining balance" }
            val accounts = accountDao.list().map { it.toAccount() }
            require(accounts.any { it.id == accountId }) { "Account not found" }
            val record = settlementRecord(item, accountId, amountToman, transactionId, atMillis)
            FinanceEngine.balances(accounts, dao.getAll().map { it.toRecord() } + record)
            requireActiveAccounts(record.fromAccountId, record.toAccountId)
            dao.insert(record.toEntity())
            obligationPaymentDao.insert(
                ObligationPaymentEntity(
                    transactionId = transactionId, obligationId = obligationId,
                    amountToman = amountToman, paidAtMillis = atMillis,
                )
            )
            record
        }
    }

    suspend fun linkObligationPayment(
        obligationId: String,
        accountId: String,
        transactionId: String,
    ): LedgerRecord = withContext(Dispatchers.IO) {
        database.withTransaction {
            val item = obligationDao.list().first { it.id == obligationId }
            val record = requireNotNull(dao.find(transactionId)).toRecord()
            val paid = obligationPaymentDao.forObligation(obligationId)
            if (paid.none { it.transactionId == transactionId }) {
                check(!database.assetDao().isLinked(transactionId))
                check(!database.recurringDao().isLinked(transactionId)) { "Transaction already linked to a recurring occurrence" }
                val remaining = obligationProgress(item, paid).remainingToman
                require(record.matchesSettlement(item, accountId, remaining))
                // The transaction primary key prevents reuse across obligations.
                obligationPaymentDao.insert(ObligationPaymentEntity(
                    transactionId, obligationId, record.amountToman, record.timestampMillis,
                ))
            }
            record
        }
    }

    suspend fun pendingBankSmsSuggestions() = withContext(Dispatchers.IO) { bankSmsDao.pending() }
    suspend fun bankLinkedTransactionIds() = withContext(Dispatchers.IO) { bankSmsDao.linkedTransactionIds().toSet() }

    suspend fun bankSmsSuggestions(): List<BankSmsCandidateEntity> =
        withContext(Dispatchers.IO) { bankSmsDao.recent() }

    /**
     * Intake is idempotent: matching duplicate messages from broadcast and inbox
     * become exactly one candidate. Only minimal metadata is persisted, no SMS body.
     */
    suspend fun importBankSms(matches: List<BankSmsMatch>): Int =
        withContext(Dispatchers.IO) {
            require(matches.size <= 500)
            database.withTransaction {
                var inserted = 0
                for (match in matches) {
                    if (match.amountToman == null || match.fingerprint.length != 64) continue
                    if (bankSmsDao.insertIgnore(
                        BankSmsCandidateEntity(
                            id = match.fingerprint,
                            bankId = match.bankId,
                            bankTitle = match.bankTitle,
                            income = match.income,
                            amountRial = match.amountRial,
                            accountLast4 = match.accountLast4,
                            receivedAtMillis = match.receivedAtMillis,
                        )
                    ) != -1L) inserted++
                }
                inserted
            }
        }

    suspend fun ignoreBankSms(id: String): Boolean = withContext(Dispatchers.IO) {
        bankSmsDao.ignore(id) == 1
    }

    /**
     * Do not call ordinary append(): approval must link the candidate and ledger
     * atomically. A repeated tap, import or process crash cannot create two entries.
     */
    suspend fun registerBankSms(id: String, record: LedgerRecord): LedgerRecord =
        withContext(Dispatchers.IO) {
            ensureDefaultWallet()
            migrateLegacyIfNeeded()
            database.withTransaction {
                val candidate = bankSmsDao.find(id) ?: error("Bank suggestion not found")
                check(candidate.state == "PENDING") { "Already handled" }
                val amount = candidate.amountRial.takeIf { it > 0 && it % 10L == 0L }
                    ?.div(10L) ?: error("Imprecise Rial amount")
                require(record.amountToman == amount) { "SMS amount was changed" }
                require(record.kind == if (candidate.income) LedgerKind.INCOME else LedgerKind.EXPENSE) {
                    "Wrong transfer direction"
                }
                require(record.timestampMillis == candidate.receivedAtMillis) {
                    "SMS timestamp must not be changed silently"
                }
                val accounts = accountDao.list().map { it.toAccount() }
                val selectedId = if (candidate.income) record.toAccountId else record.fromAccountId
                require(accounts.any { it.id == selectedId }) { "Missing account" }
                val category = isSelectableCategory(
                    record.category,
                    income = candidate.income,
                    custom = customCategoryDao.list(),
                )
                require(category) { "Invalid category" }
                require(record.note.length <= 250)
                FinanceEngine.balances(
                    accounts, dao.getAll().map { it.toRecord() } + record,
                )
                requireActiveAccounts(record.fromAccountId, record.toAccountId)
            dao.insert(record.toEntity())
                check(bankSmsDao.markRegistered(id, record.id) == 1)
                record
            }
        }

    suspend fun linkBankSms(id: String, selected: LedgerRecord): LedgerRecord = database.withTransaction {
        val sms = requireNotNull(bankSmsDao.find(id))
        val record = requireNotNull(dao.find(selected.id)).toRecord()
        check(record == selected && sms.state == "PENDING")
        require(sms.amountRial > 0 && sms.amountRial % 10L == 0L && sms.amountRial / 10L == record.amountToman)
        require(record.kind == if (sms.income) LedgerKind.INCOME else LedgerKind.EXPENSE)
        require(kotlin.math.abs(record.timestampMillis - sms.receivedAtMillis) < 900_000L)
        check(!bankSmsDao.isLinked(record.id))
        check(bankSmsDao.markRegistered(id, record.id) == 1)
        record
    }

    /** Bank evidence and at most one independent commitment share one ledger movement.
     * Migration is completed before the outer write transaction; any failed link rolls back
     * the new ledger row and SMS approval together.
     */
    suspend fun reconcileBankPayment(id: String, record: LedgerRecord, existing: Boolean,
        target: BankPaymentTarget?): LedgerRecord = withContext(Dispatchers.IO) {
        ensureDefaultWallet()
        migrateLegacyIfNeeded()
        database.withTransaction {
            val posted = if (existing) linkBankSms(id, record) else registerBankSms(id, record)
            when (target) {
                is BankPaymentTarget.Obligation -> linkObligationPayment(target.id,
                    requireNotNull(posted.fromAccountId ?: posted.toAccountId), posted.id)
                is BankPaymentTarget.Recurring -> linkRecurring(target.id, target.day, posted)
                null -> Unit
            }
            posted
        }
    }

    suspend fun clearOldBankSmsHistory() = withContext(Dispatchers.IO) {
        bankSmsDao.purgeOlderHandled(
            System.currentTimeMillis() - 90L * 24 * 60 * 60 * 1000
        )
    }

    suspend fun loadSavingsGoals(): List<SavingsGoalEntity> =
        withContext(Dispatchers.IO) { database.savingsGoalDao().list() }

    suspend fun addSavingsGoal(goal: SavingsGoalEntity) = withContext(Dispatchers.IO) {
        require(goal.id.isNotBlank())
        require(goal.title.isNotBlank() && goal.title.length <= 60)
        require(goal.targetToman > 0L)
        require(goal.accountId != "wallet") {
            "Choose a dedicated savings account so no funds are counted twice"
        }
        database.withTransaction {
            val accounts = accountDao.list()
            requireActiveAccounts(goal.accountId)
            require(accounts.any { it.id == goal.accountId }) { "Account not found" }
            require(database.savingsGoalDao().list().none {
                it.accountId == goal.accountId || it.id == goal.id
            }) { "Savings account already assigned to a goal" }
            require(database.envelopeDao().list().none { it.accountId == goal.accountId }) { "Account already hosts envelopes" }
            database.savingsGoalDao().insert(goal)
        }
    }

    suspend fun loadWealthHistory(): List<WealthSnapshot> = withContext(Dispatchers.IO) { database.wealthDao().list() }

    /** A recorded observation of the complete local ledger. Never reconstruct past cash from today's accounts. */
    suspend fun captureWealth(): List<WealthSnapshot> = withContext(Dispatchers.IO) {
        ensureDefaultWallet()
        migrateLegacyIfNeeded()
        database.withTransaction {
            val balances = FinanceEngine.balances(accountDao.list().map { it.toAccount() }, dao.getAll().map { it.toRecord() })
            val positions = assetPositions(database.assetDao().holdings(), database.assetDao().events())
            val current = netWorth(balances, positions, obligationDao.list(), obligationPaymentDao.list())
            val history = database.wealthDao().list()
            val now = System.currentTimeMillis()
            val last = history.firstOrNull()
            // Keep an observation for each day when explicitly requested, plus changes within that day.
            if (last == null || last.worth() != current || formatJalaliDueDate(last.atMillis) != formatJalaliDueDate(now)) {
                database.wealthDao().insert(WealthSnapshot.from(current, now))
            }
            database.wealthDao().list()
        }
    }

    suspend fun loadAssets(): List<AssetPosition> = database.withTransaction {
        assetPositions(database.assetDao().holdings(), database.assetDao().events())
    }

    /** A holding is a non-cash memorandum; acquisition/disposal never silently posts a transaction. */
    suspend fun addAsset(holding: AssetHolding, initialValue: Long) = withContext(Dispatchers.IO) {
        database.withTransaction {
            val clean = holding.copy(title = holding.title.trim())
            validateAsset(clean)
            require(initialValue >= 0)
            database.assetDao().insertHolding(clean)
            database.assetDao().insertEvent(AssetEvent(assetId = clean.id, kind = "VALUE", valueToman = initialValue,
                atMillis = clean.createdAtMillis, note = "ارزش اولیه"))
        }
    }

    suspend fun buyAsset(holding: AssetHolding, value: Long, accountId: String, transactionId: String) = withContext(Dispatchers.IO) {
        ensureDefaultWallet(); migrateLegacyIfNeeded()
        database.withTransaction {
            val cost = requireNotNull(holding.costBasisToman)
            require(cost > 0 && value >= 0)
            val record = LedgerRecord(transactionId, LedgerKind.EXPENSE, cost, fromAccountId = accountId,
                category = "خرید دارایی", note = holding.title, timestampMillis = holding.createdAtMillis)
            val clean = holding.copy(title = holding.title.trim())
            validateAsset(clean)
            val accounts = accountDao.list().map { it.toAccount() }
            require(FinanceEngine.balances(accounts, dao.getAll().map { it.toRecord() }).getValue(accountId) >= cost)
            append(record)
            database.assetDao().insertHolding(clean)
            database.assetDao().insertEvent(AssetEvent(assetId = clean.id, kind = "VALUE", valueToman = value,
                atMillis = clean.createdAtMillis, note = "خرید ثبت‌شده", transactionId = record.id))
        }
    }

    suspend fun sellAsset(expected: AssetPosition, proceeds: Long, accountId: String, transactionId: String) = withContext(Dispatchers.IO) {
        ensureDefaultWallet(); migrateLegacyIfNeeded()
        database.withTransaction {
            val current = loadAssets().first { it.holding.id == expected.holding.id }
            check(current == expected && !current.archived)
            require(proceeds > 0)
            val at = maxOf(System.currentTimeMillis(), current.latest.atMillis)
            val record = LedgerRecord(transactionId, LedgerKind.INCOME, proceeds, toAccountId = accountId,
                category = "فروش دارایی", note = current.holding.title, timestampMillis = at)
            append(record)
            database.assetDao().insertEvent(AssetEvent(assetId = current.holding.id, kind = "SALE", valueToman = proceeds,
                atMillis = at, note = "فروش ثبت‌شده", transactionId = record.id))
        }
    }

    suspend fun changeAsset(expected: AssetPosition, kind: String, value: Long, note: String) = withContext(Dispatchers.IO) {
        database.withTransaction {
            val current = assetPositions(database.assetDao().holdings(), database.assetDao().events()).first { it.holding.id == expected.holding.id }
            check(current.latest.id == expected.latest.id && !current.sold) { "Asset changed or sold" }
            require(kind in listOf("VALUE", "ARCHIVE", "RESTORE"))
            val next = AssetEvent(id = Math.addExact(current.latest.id, 1L), assetId = current.holding.id,
                kind = kind, valueToman = value, atMillis = maxOf(System.currentTimeMillis(), current.latest.atMillis), note = note.trim())
            assetPositions(listOf(current.holding), current.history + next)
            database.assetDao().insertEvent(next.copy(id = 0))
        }
    }

    suspend fun loadEnvelopes() = withContext(Dispatchers.IO) { database.envelopeDao().list() }
    suspend fun envelopeHistory(id: String) = withContext(Dispatchers.IO) { database.envelopeDao().history(id) }

    suspend fun addEnvelope(item: MoneyEnvelope) = withContext(Dispatchers.IO) {
        ensureDefaultWallet()
        migrateLegacyIfNeeded()
        database.withTransaction {
            require(item.id.isNotBlank() && item.title.trim().length in 1..60 && item.createdAtMillis > 0)
            require(item.targetToman > 0 && item.reservedToman == 0L && item.priority in 0..2 && item.monthlyPlanToman >= 0)
            requireActiveAccounts(item.accountId)
            require(accountDao.list().any { it.id == item.accountId })
            require(database.savingsGoalDao().list().none { it.accountId == item.accountId })
            database.envelopeDao().insert(item.copy(title = item.title.trim()))
        }
    }

    /** Metadata edits never move cash; preserve previous plan and priority. */
    suspend fun editEnvelope(expected: MoneyEnvelope, title: String, target: Long, priority: Int,
        monthly: Long) = withContext(Dispatchers.IO) {
        database.withTransaction {
            val envelopeDao = database.envelopeDao()
            val current = envelopeDao.list().first { it.id == expected.id }
            check(current == expected) { "Envelope changed" }
            require(title.trim().length in 1..60 && target > 0 && target >= current.reservedToman)
            require(priority in 0..2 && monthly >= 0)
            val updated = current.copy(title = title.trim(), targetToman = target, priority = priority, monthlyPlanToman = monthly)
            if (updated != current) {
                check(envelopeDao.update(updated) == 1)
                val details = "نام: " + current.title + " ← " + updated.title +
                    "\nهدف: " + formatToman(current.targetToman) + " ← " + formatToman(updated.targetToman) +
                    "\nاولویت: " + envelopePriorityLabel(current.priority) + " ← " + envelopePriorityLabel(updated.priority) +
                    "\nبرنامه ماهانه: " + formatToman(current.monthlyPlanToman) + " ← " + formatToman(updated.monthlyPlanToman)
                envelopeDao.addEvent(EnvelopeEvent(envelopeId = current.id, atMillis = System.currentTimeMillis(),
                    previousToman = current.reservedToman, reservedToman = current.reservedToman, details = details))
            }
        }
    }

    /** Reservations are plans, not money movements; compare-and-set prevents stale edits. */
    suspend fun setEnvelopeReserve(id: String, expected: Long, amount: Long) = withContext(Dispatchers.IO) {
        database.withTransaction {
            val envelopeDao = database.envelopeDao()
            val all = envelopeDao.list()
            val current = all.first { it.id == id }
            check(current.reservedToman == expected) { "Reservation changed" }
            require(amount in 0..current.targetToman)
            if (amount > expected) {
                require(database.savingsGoalDao().list().none { it.accountId == current.accountId })
                val balances = FinanceEngine.balances(accountDao.list().map { it.toAccount() }, dao.getAll().map { it.toRecord() })
                val free = envelopeFreeCapacity(current.accountId, balances, all, id)
                require(amount <= free) { "Not enough unreserved account balance" }
            }
            if (amount != expected) {
                check(envelopeDao.updateReserve(id, expected, amount) == 1)
                envelopeDao.addEvent(EnvelopeEvent(envelopeId = id, atMillis = System.currentTimeMillis(),
                    previousToman = expected, reservedToman = amount))
            }
        }
    }

    private suspend fun migrateLegacyIfNeeded() {
        if (preferences.getBoolean("room_ledger_imported_v1", false)) return

        // A malformed legacy file is NEVER interpreted as an empty transaction
        // list: fail loudly, retain the original bytes, and allow later recovery.
        val legacyRaw = preferences.getString("ledger", "[]") ?: "[]"
        val parsed = JSONArray(legacyRaw)
        val legacy = buildList {
            for (index in 0 until parsed.length()) {
                val data = parsed.getJSONObject(index)
                val record = LedgerRecord(
                    id = data.getString("id"),
                    kind = LedgerKind.valueOf(data.getString("kind")),
                    amountToman = data.getLong("amount"),
                    fromAccountId = data.optString("from").takeIf { it.isNotEmpty() },
                    toAccountId = data.optString("to").takeIf { it.isNotEmpty() },
                    category = data.optString("category"),
                    note = data.optString("note"),
                    timestampMillis = data.optLong("created_at"),
                )
                require(record.id.isNotBlank() && record.amountToman > 0L) {
                    "Invalid legacy financial record"
                }
                add(record.toEntity())
            }
        }
        check(legacy.map { it.id }.distinct().size == legacy.size) {
            "Duplicate legacy record identifiers"
        }

        database.withTransaction {
            if (legacy.isNotEmpty()) {
                dao.insertLegacy(legacy)
                // IGNORE makes retry idempotent, but an existing row with the
                // same ID and different financial values must never pass as a
                // successful import. Compare full rows before setting the marker.
                val importedById = dao.getAll().associateBy { it.id }
                check(legacy.all { importedById[it.id] == it }) {
                    "Legacy ledger conflicts with existing Room transactions"
                }
            }
        }
        // Mark after DB commit. A crash before this point is safe to retry:
        // insertLegacy uses IGNORE on identical primary keys.
        check(preferences.edit().putBoolean("room_ledger_imported_v1", true).commit()) {
            "Legacy data imported but migration marker could not be persisted"
        }
    }
}

private fun LedgerRecord.toEntity() = LedgerEntity(
    id = id,
    kind = kind.name,
    amountToman = amountToman,
    fromAccountId = fromAccountId,
    toAccountId = toAccountId,
    category = category,
    note = note,
    timestampMillis = timestampMillis,
)

internal fun LedgerEntity.toRecord() = LedgerRecord(
    id = id,
    kind = LedgerKind.valueOf(kind),
    amountToman = amountToman,
    fromAccountId = fromAccountId,
    toAccountId = toAccountId,
    category = category,
    note = note,
    timestampMillis = timestampMillis,
)

private fun FinancialAccountEntity.toAccount() =
    Account(id, title, openingBalanceToman)

private fun Account.toEntity() =
    FinancialAccountEntity(id, title.trim(), openingBalanceToman)
