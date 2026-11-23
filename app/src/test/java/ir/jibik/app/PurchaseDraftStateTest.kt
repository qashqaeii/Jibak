package ir.jibik.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PurchaseDraftStateTest {
    @Test fun rotationRoundTripPreservesMultiplePendingLines() {
        val drafts = listOf(
            PurchaseDraftItem("برنج", "2", "850000", "خوراک / خرید مواد غذایی / برنج", "کیلوگرم"),
            PurchaseDraftItem("میوه", "3", "90000", "خوراک / خرید مواد غذایی / میوه", "بسته"),
        )
        assertEquals(drafts, decodePurchaseDrafts(encodePurchaseDrafts(drafts)))
    }

    @Test fun malformedSavedStateNeverCreatesPartialRows() {
        assertEquals(listOf(PurchaseDraftItem()), decodePurchaseDrafts(emptyList()))
        assertEquals(listOf(PurchaseDraftItem()), decodePurchaseDrafts(listOf("orphan")))
        assertEquals(listOf(PurchaseDraftItem()), decodePurchaseDrafts(List(84) { "x" }))
    }

    @Test fun legacyFourFieldDraftsRestoreWithSafeDefaultUnit() {
        val legacy = listOf("پنیر", "2", "100000", "خوراک")
        val decoded = decodePurchaseDrafts(legacy)
        assertEquals(1, decoded.size)
        assertEquals("عدد", decoded.first().unit)
        assertEquals("پنیر", decoded.first().name)
    }

    @Test fun invalidV2UnitFallsBackWithoutInventingMoney() {
        val encoded = listOf(
            "__jibik_purchase_v2__", "برنج", "1", "50000",
            "خوراک", "غیرمجاز",
        )
        assertEquals("عدد", decodePurchaseDrafts(encoded).first().unit)
        assertEquals("50000", decodePurchaseDrafts(encoded).first().unitPrice)
    }

    @Test fun restoredStateIsBoundedToTwentyLines() {
        val many = List(28) { index -> PurchaseDraftItem("item-$index", "1", "100", "cat") }
        val encoded = encodePurchaseDrafts(many)
        assertEquals(101, encoded.size)
        assertEquals(20, decodePurchaseDrafts(encoded).size)
        assertTrue(decodePurchaseDrafts(encoded).all { it.name.isNotBlank() })
    }
}
