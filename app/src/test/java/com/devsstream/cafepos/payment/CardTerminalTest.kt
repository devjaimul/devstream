package com.devsstream.cafepos.payment

import com.devsstream.terminal.LegacyCardTerminal
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CopyOnWriteArrayList

// The SDK calls back from its own thread in real time, so tests that wait for a
// callback use runBlocking. TC-12 never gets a callback, so it can use runTest's
// virtual clock and skip the 15 s wait.
class CardTerminalTest {

    private val sdk = LegacyCardTerminal()
    private val logs = CopyOnWriteArrayList<String>()
    private val terminal = CardTerminal(sdk, log = { logs += it })

    private fun bdt(amount: Int) = amount * 100L

    @Test
    fun `TC-11 1013 is declined with INSUFFICIENT_FUNDS`() = runBlocking {
        val result = terminal.charge(bdt(1013), "ref-11")

        assertEquals(CardResult.Declined("INSUFFICIENT_FUNDS"), result)
        assertEquals(1, sdk.attempts("ref-11"))
    }

    @OptIn(ExperimentalCoroutinesApi::class) // testScheduler.currentTime
    @Test
    fun `TC-12 1077 never calls back, after 15 s status is APPROVED and attempts is 1`() = runTest {
        val result = terminal.charge(bdt(1077), "ref-12")

        assertEquals(15_000L, testScheduler.currentTime)
        assertEquals(CardResult.Approved(authCode = null), result)
        assertEquals(1, sdk.attempts("ref-12"))
    }

    @Test
    fun `TC-13 1055 calls back twice but we resolve once`() = runBlocking {
        val result = terminal.charge(bdt(1055), "ref-13")
        assertNotNull((result as CardResult.Approved).authCode)

        delay(2_000) // the duplicate comes 1 s after the first one
        assertEquals(1, logs.count { "duplicate" in it })
        assertEquals(1, sdk.attempts("ref-13"))
    }

    @Test
    fun `normal amount is approved by the callback`() = runBlocking {
        val result = terminal.charge(bdt(600), "ref-600")
        assertTrue(result is CardResult.Approved && result.authCode != null)
    }

    @Test
    fun `callback after cancel is ignored and status still has the real answer`() = runBlocking {
        val job = launch { terminal.charge(bdt(600), "ref-cancel") }
        delay(500)
        job.cancelAndJoin()

        delay(2_500) // approval callback fires at 2 s
        assertTrue(logs.any { "nobody is waiting" in it })
        assertEquals(CardResult.Approved(authCode = null), terminal.status("ref-cancel"))
        assertEquals(1, sdk.attempts("ref-cancel"))
    }

    @Test
    fun `a ref the terminal has never seen is Unknown`() {
        assertEquals(CardResult.Unknown, terminal.status("ref-nobody-charged"))
    }
}
