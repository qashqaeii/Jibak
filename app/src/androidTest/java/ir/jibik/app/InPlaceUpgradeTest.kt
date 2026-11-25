package ir.jibik.app

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import ir.jibik.core.finance.FinanceEngine
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Only the explicitly seeded emulator upgrade job may execute this fixture. */
@RunWith(AndroidJUnit4::class)
class InPlaceUpgradeTest {
    @Test fun installedLedgerAndPreferencesSurviveApkReplacement() = runBlocking {
        val fixture = InstrumentationRegistry.getArguments().getString("upgradeFixture")?.toIntOrNull()
        assumeTrue(fixture == 4 || fixture == 20)
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val db = JibikDatabase.get(context)
        val store = LocalLedgerStore(context, db)
        val records = store.load()
        assertEquals(setOf("upgrade-income", "upgrade-expense"), records.map { it.id }.toSet())
        assertEquals(2, records.size)
        assertEquals(1500000L, records.first { it.id == "upgrade-income" }.amountToman)
        assertEquals(250000L, records.first { it.id == "upgrade-expense" }.amountToman)
        assertEquals("خرید آزمایشی", records.first { it.id == "upgrade-expense" }.note)
        val balances = FinanceEngine.balances(store.loadAccounts(), records)
        assertEquals(1250000L, balances.getValue("wallet"))
        assertEquals(300000L, balances.getValue("upgrade-savings"))
        assertEquals(7000000L, store.budgetToman())
        assertTrue(store.hasFinishedWelcome())
        assertFalse(store.areHomeAmountsVisible())
        val receipt = db.purchaseLineDao().forTransaction("upgrade-expense").single()
        assertEquals("کالای آزمون", receipt.title)
        assertEquals(2, receipt.quantity)
        assertEquals(125000L, receipt.unitPriceToman)
        assertEquals("عدد", receipt.unit)
        assertEquals("upgrade-goal", store.loadSavingsGoals().single().id)
        assertTrue(context.getSharedPreferences("jibik_local_v1", Context.MODE_PRIVATE)
            .getBoolean("room_ledger_imported_v1", false))
        if (fixture == 20) {
            assertTrue(store.loadCustomCategories().any { it.id == "upgrade-category" })
            assertEquals(350000L, store.loadAssets().single().valueToman)
        }
        assertEquals(records, store.load()) // No duplicate import after reopening the store.
        db.openHelper.writableDatabase.query("PRAGMA user_version").use {
            assertTrue(it.moveToFirst())
            assertEquals(20, it.getInt(0))
        }
        db.openHelper.writableDatabase.query("PRAGMA integrity_check").use {
            assertTrue(it.moveToFirst())
            assertEquals("ok", it.getString(0))
        }
        db.openHelper.writableDatabase.query("PRAGMA foreign_key_check").use { assertFalse(it.moveToFirst()) }
    }
}
