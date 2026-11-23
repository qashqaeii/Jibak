package ir.jibik.app

import ir.jibik.core.finance.LedgerKind
import ir.jibik.core.finance.LedgerRecord
import ir.jibik.core.finance.ShoppingItem
import ir.jibik.core.finance.itemizedPurchaseTotal

/** Receipt classification never creates secondary money movements. */
internal data class PurchaseLine(
    val title: String,
    val category: String,
    val quantity: Int,
    val unitPriceToman: Long,
    val unit: String = "عدد",
)

internal val PURCHASE_UNITS = listOf(
    "عدد", "بسته", "گرم", "کیلوگرم", "میلی‌لیتر", "لیتر", "متر", "دست",
)

internal fun validateItemizedPurchase(payment: LedgerRecord, lines: List<PurchaseLine>) {
    require(payment.kind == LedgerKind.EXPENSE) { "A purchase is a single expense" }
    require(payment.amountToman > 0L)
    require(lines.isNotEmpty() && lines.size <= 30)
    lines.forEach {
        require(it.title.isNotBlank() && it.title.length <= 80)
        require(it.category.isNotBlank() && it.category.length <= 120)
        require(it.unit in PURCHASE_UNITS) { "Unsupported purchase quantity unit" }
    }
    val total = itemizedPurchaseTotal(
        lines.map { ShoppingItem(it.title, it.quantity, it.unitPriceToman) }
    )
    require(total == payment.amountToman) { "Receipt items do not match payment total" }
}
