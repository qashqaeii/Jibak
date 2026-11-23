package ir.jibik.app

import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.snapshots.SnapshotStateList

/**
 * Draft values are saved in Android's SavedStateRegistry. Only unsent form
 * inputs are saved; persisted receipts always come from Room, never this list.
 */
internal data class PurchaseDraftItem(
    val name: String = "",
    val quantity: String = "1",
    val unitPrice: String = "",
    val category: String = "",
)

internal fun encodePurchaseDrafts(items: List<PurchaseDraftItem>): List<String> =
    items.take(20).flatMap { item ->
        listOf(item.name, item.quantity, item.unitPrice, item.category)
    }

/** Reject partial/corrupt state and start with one empty row instead. */
internal fun decodePurchaseDrafts(encoded: List<String>): List<PurchaseDraftItem> {
    if (encoded.isEmpty() || encoded.size % 4 != 0 || encoded.size > 80) {
        return listOf(PurchaseDraftItem())
    }
    return encoded.chunked(4).map { parts ->
        PurchaseDraftItem(
            name = parts[0].take(80),
            quantity = parts[1].take(5),
            unitPrice = parts[2].take(19),
            category = parts[3].take(120),
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
