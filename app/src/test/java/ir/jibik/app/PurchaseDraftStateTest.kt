package ir.jibik.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PurchaseDraftStateTest {
    @Test fun rotationRoundTripPreservesMultiplePendingLines() {
        val drafts = listOf(
            PurchaseDraftItem("برنج", "2", "850000", "خوراک / خرید مواد غذایی / برنج"),
            PurchaseDraftItem("میوه", "3", "90000", "خوراک / خرید مواد غذایی / میوه"),
        )
        assertEquals(drafts, decodePurchaseDrafts(encodePurchaseDrafts(drafts)))
    }

    @Test fun malformedSavedStateNeverCreatesPartialRows() {
        assertEquals(listOf(PurchaseDraftItem()), decodePurchaseDrafts(emptyList()))
        assertEquals(listOf(PurchaseDraftItem()), decodePurchaseDrafts(listOf("orphan")))
        assertEquals(listOf(PurchaseDraftItem()), decodePurchaseDrafts(List(84) { "x" }))
    }

    @Test fun restoredStateIsBoundedToTwentyLines() {
        val many = List(28) { index -> PurchaseDraftItem("item-$index", "1", "100", "cat") }
        val encoded = encodePurchaseDrafts(many)
        assertEquals(80, encoded.size)
        assertEquals(20, decodePurchaseDrafts(encoded).size)
        assertTrue(decodePurchaseDrafts(encoded).all { it.name.isNotBlank() })
    }
}
