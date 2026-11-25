package ir.jibik.app

import androidx.room.withTransaction
import ir.jibik.core.finance.FinanceEngine
import ir.jibik.core.finance.Account

internal data class IntegrityIssue(val label: String, val id: String)

/** Read-only diagnostic. Do not silently repair legacy payment history. */
internal object FinancialIntegrity {
    suspend fun inspect(room: JibikDatabase): List<IntegrityIssue> = room.withTransaction {
        val issues = mutableListOf<IntegrityIssue>()
        val db = room.openHelper.writableDatabase
        fun scan(label: String, sql: String) {
            db.query(sql).use { cursor ->
                while (cursor.moveToNext()) issues.add(IntegrityIssue(label, cursor.getString(0) ?: "—"))
            }
        }
        scan("چند نوبت برای یک پرداخت", "SELECT transactionId FROM recurring_decisions WHERE transactionId IS NOT NULL GROUP BY transactionId HAVING COUNT(*) > 1")
        scan("پرداخت مشترک تعهد و نوبت", "SELECT DISTINCT r.transactionId FROM recurring_decisions r JOIN obligation_payments p ON p.transactionId = r.transactionId")
        scan("چند پیام بانکی برای یک تراکنش", "SELECT linkedTransactionId FROM bank_sms_candidates WHERE linkedTransactionId IS NOT NULL GROUP BY linkedTransactionId HAVING COUNT(*) > 1")
        scan("ارتباط نامعتبر نوبت", """
            SELECT COALESCE(d.transactionId, d.ruleId) FROM recurring_decisions d
            LEFT JOIN recurring_rules r ON r.id = d.ruleId
            LEFT JOIN ledger_records l ON l.id = d.transactionId
            LEFT JOIN ledger_voids v ON v.transactionId = d.transactionId
            WHERE r.id IS NULL OR (d.transactionId IS NULL AND d.actual IS NOT NULL)
            OR (d.transactionId IS NOT NULL AND (l.id IS NULL OR v.transactionId IS NOT NULL
            OR d.actual IS NULL OR d.actual != l.amountToman OR d.actual <= 0
            OR l.kind != CASE WHEN r.income = 1 THEN 'INCOME' ELSE 'EXPENSE' END
            OR COALESCE(l.fromAccountId, l.toAccountId, '') != r.accountId))
        """.trimIndent())
        scan("ارتباط نامعتبر تعهد", """
            SELECT p.transactionId FROM obligation_payments p
            LEFT JOIN ledger_records l ON l.id = p.transactionId
            LEFT JOIN financial_obligations o ON o.id = p.obligationId
            LEFT JOIN ledger_voids v ON v.transactionId = p.transactionId
            WHERE l.id IS NULL OR o.id IS NULL OR v.transactionId IS NOT NULL
            OR p.amountToman != l.amountToman OR p.paidAtMillis != l.timestampMillis
            OR l.kind != CASE WHEN o.kind = 'RECEIVABLE' THEN 'INCOME' ELSE 'EXPENSE' END
        """.trimIndent())
        scan("ارتباط نامعتبر پیام بانکی", """
            SELECT COALESCE(s.linkedTransactionId, s.id) FROM bank_sms_candidates s
            LEFT JOIN ledger_records l ON l.id = s.linkedTransactionId
            LEFT JOIN ledger_voids v ON v.transactionId = s.linkedTransactionId
            WHERE (s.state = 'REGISTERED' AND s.linkedTransactionId IS NULL)
            OR (s.linkedTransactionId IS NOT NULL AND (s.state != 'REGISTERED' OR l.id IS NULL
            OR v.transactionId IS NOT NULL OR s.amountRial <= 0 OR s.amountRial % 10 != 0
            OR s.amountRial / 10 != l.amountToman
            OR l.kind != CASE WHEN s.income = 1 THEN 'INCOME' ELSE 'EXPENSE' END))
        """.trimIndent())
        scan("پاکت مالی نامعتبر", "SELECT e.id FROM money_envelopes e LEFT JOIN financial_accounts a ON a.id = e.accountId LEFT JOIN savings_goals g ON g.accountId = e.accountId WHERE a.id IS NULL OR g.id IS NOT NULL OR e.targetToman <= 0 OR e.reservedToman < 0 OR e.reservedToman > e.targetToman OR e.priority NOT BETWEEN 0 AND 2 OR e.monthlyPlanToman < 0")
        if (runCatching { validateAssetTransactions(assetPositions(room.assetDao().holdings(), room.assetDao().events()), room.ledgerDao().getAll().map { it.toRecord() }) }.isFailure)
            issues.add(IntegrityIssue("تاریخچه دارایی نامعتبر", "دارایی‌ها"))
        room.wealthDao().list().forEach { row -> if (runCatching { row.worth() }.isFailure)
            issues.add(IntegrityIssue("تصویر ثروت نامعتبر", row.id.toString())) }
        val records = room.ledgerDao().getAll().map { it.toRecord() }
        val allRecords = (records + room.ledgerDao().voided().map { it.toRecord() }).associateBy { it.id }
        room.purchaseLineDao().all().groupBy { it.transactionId }.forEach { (id, rows) ->
            val valid = runCatching {
                validateItemizedPurchase(requireNotNull(allRecords[id]), rows.map {
                    PurchaseLine(it.title, it.category, it.quantity, it.unitPriceToman, it.unit)
                })
            }.isSuccess
            if (!valid) issues.add(IntegrityIssue("اختلاف مبلغ رسید", id))
        }
        val payments = room.obligationPaymentDao().list().groupBy { it.obligationId }
        room.obligationDao().list().forEach { obligation ->
            val total = payments[obligation.id].orEmpty().fold(java.math.BigInteger.ZERO) { sum, p ->
                sum + p.amountToman.toBigInteger()
            }
            if (total > obligation.totalToman.toBigInteger()) issues.add(IntegrityIssue("پرداخت بیش از تعهد", obligation.id))
        }
        val accounts = room.accountDao().list().map { Account(it.id, it.title, it.openingBalanceToman) }
        if (runCatching { FinanceEngine.balances(accounts, records) }.isFailure)
            issues.add(IntegrityIssue("خطای محاسبه مانده", "دفتر مالی"))
        issues
    }
}
