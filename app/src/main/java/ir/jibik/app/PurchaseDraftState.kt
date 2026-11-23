package ir.jibik.app

import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.snapshots.SnapshotStateList

/**
 * SavedState contains only unsent form inputs, not copies of stored receipts.
 * Version-tagged format remains compatible with the old four-field drafts.
 */
internal data class PurchaseDraftItem(
    val name: String = "",
    val quantity: String = "1",
    val unitPrice: String = "",
    val category: String = "",
    val unit: String = "عدد",
)

private const val DRAFT_V2 = "__jibik_purchase_v2__"

internal fun encodePurchaseDrafts(items: List<PurchaseDraftItem>): List<String> =
    listOf(DRAFT_V2) + items.take(20).flatMap { item ->
        listOf(item.name, item.quantity, item.unitPrice, item.category, item.unit)
    }

/** Invalid state never becomes an unverified payment or a fabricated line item. */
internal fun decodePurchaseDrafts(encoded: List<String>): List<PurchaseDraftItem> {
    val modern = encoded.firstOrNull() == DRAFT_V2
    val payload = if (modern) encoded.drop(1) else encoded
    val columns = if (modern) 5 else 4
    if (payload.isEmpty() || payload.size % columns != 0 ||
        payload.size > columns * 20) {
        return listOf(PurchaseDraftItem())
    }
    return payload.chunked(columns).map { parts ->
        PurchaseDraftItem(
            name = parts[0].take(80),
            quantity = parts[1].take(5),
            unitPrice = parts[2].take(19),
            category = parts[3].take(120),
            unit = if (modern && parts[4] in PURCHASE_UNITS) parts[4] else "عدد",
        )
    }
}

internal val purchaseDraftStateSaver =
    listSaver<SnapshotStateList<PurchaseDraftItem>, String>(
        save = { encodePurchaseDrafts(it) },
        restore = { mutableStateListOf<PurchaseDraftItem>().apply {
            addAll(decodePurchaseDrafts(it))
        } },
    )
