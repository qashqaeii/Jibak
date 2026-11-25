package ir.jibik.app

import ir.jibik.core.finance.Account
import org.junit.Assert.*
import org.junit.Test

class BankHistoryComparisonTest {
    private val profiles = listOf(BankSenderProfile("a","melli","100","4004","a"))
    private val accounts = listOf(Account("a","A",0))
    private fun message(time: Long, balance: Long) = BankSmsMatch("melli","ملی",1000,true,"4004","100",time,
        time.toString().padStart(64,'0'),reportedBalanceToman=balance)
    @Test fun newestUnambiguousBalanceOnly() {
        val result = latestBankComparisons(listOf(message(1,500),message(2,700)), profiles,accounts,mapOf("a" to 1000L)).single()
        assertEquals(700L,result.reported)
        assertEquals(300.toBigInteger(),result.difference)
        assertTrue(latestBankComparisons(listOf(message(2,700),message(2,800)),profiles,accounts,mapOf("a" to 1000L)).isEmpty())
        assertTrue(latestBankComparisons(listOf(message(2,700).copy(sender="101")),profiles,accounts,mapOf("a" to 1000L)).isEmpty())
    }
}
