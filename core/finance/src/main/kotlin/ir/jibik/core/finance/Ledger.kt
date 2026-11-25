package ir.jibik.core.finance

/**
 * Integral tomans throughout the domain. Never use Float or Double for money.
 * Source of truth is a list of individual money movements, not UI totals.
 */
data class Account(
    val id: String,
    val title: String,
    val openingBalanceToman: Long = 0L,
    val archived: Boolean = false,
)

enum class LedgerKind { EXPENSE, INCOME, TRANSFER }

data class LedgerRecord(
    val id: String,
    val kind: LedgerKind,
    val amountToman: Long,
    val fromAccountId: String? = null,
    val toAccountId: String? = null,
    val category: String = "",
    val note: String = "",
    val timestampMillis: Long = 0L,
)

data class MoneySummary(
    val balanceToman: Long,
    val incomeToman: Long,
    val expenseToman: Long,
)

object FinanceEngine {
    fun validate(accounts: List<Account>, records: List<LedgerRecord>) {
        val ids = accounts.map { it.id }
        require(ids.size == ids.distinct().size) { "Duplicate account ID" }
        require(ids.none(String::isBlank)) { "Blank account ID" }
        require(records.map { it.id }.distinct().size == records.size) { "Duplicate record ID" }
        for (record in records) {
            require(record.amountToman > 0) { "Amount must be positive" }
            when (record.kind) {
                LedgerKind.EXPENSE -> {
                    require(record.fromAccountId in ids && record.toAccountId == null) {
                        "Expense must debit exactly one account"
                    }
                }
                LedgerKind.INCOME -> {
                    require(record.toAccountId in ids && record.fromAccountId == null) {
                        "Income must credit exactly one account"
                    }
                }
                LedgerKind.TRANSFER -> {
                    require(
                        record.fromAccountId in ids &&
                            record.toAccountId in ids &&
                            record.fromAccountId != record.toAccountId
                    ) { "Transfer needs distinct valid accounts" }
                }
            }
        }
    }

    fun balances(accounts: List<Account>, records: List<LedgerRecord>): Map<String, Long> {
        validate(accounts, records)
        val result = accounts.associate { it.id to it.openingBalanceToman }.toMutableMap()
        records.forEach { entry ->
            entry.fromAccountId?.let { id ->
                result[id] = Math.subtractExact(result.getValue(id), entry.amountToman)
            }
            entry.toAccountId?.let { id ->
                result[id] = Math.addExact(result.getValue(id), entry.amountToman)
            }
        }
        return result.toMap()
    }

    fun summary(accounts: List<Account>, records: List<LedgerRecord>): MoneySummary {
        val balance = balances(accounts, records).values.fold(0L, Math::addExact)
        val income = records.filter { it.kind == LedgerKind.INCOME }
            .fold(0L) { acc, record -> Math.addExact(acc, record.amountToman) }
        val expense = records.filter { it.kind == LedgerKind.EXPENSE }
            .fold(0L) { acc, record -> Math.addExact(acc, record.amountToman) }
        return MoneySummary(balance, income, expense)
    }
}

/**
 * Itemized shopping is metadata on a SINGLE expense transaction.
 * Items should never be posted as additional expense ledger records.
 */
data class ShoppingItem(val title: String, val quantity: Int, val unitPriceToman: Long)

fun itemizedPurchaseTotal(items: List<ShoppingItem>): Long =
    items.fold(0L) { sum, item ->
        require(item.quantity > 0 && item.unitPriceToman >= 0)
        Math.addExact(sum, Math.multiplyExact(item.quantity.toLong(), item.unitPriceToman))
    }
