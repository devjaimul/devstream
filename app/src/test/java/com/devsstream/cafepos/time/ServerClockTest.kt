package com.devsstream.cafepos.time

import com.devsstream.cafepos.MemoryAnchorStore
import com.devsstream.cafepos.core.dhakaTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration

class ServerClockTest {

    private val store = MemoryAnchorStore()
    private var elapsed = 10_000_000L
    private var wall = dhakaTime(2026, 11, 20, 15, 27, 30).toEpochMilli()
    private var boots: Int? = 7

    private fun clock() = ServerClock(store, { elapsed }, { wall }, { boots })

    private val serverAt1530 = dhakaTime(2026, 11, 20, 15, 30)

    @Test
    fun `now is the anchor plus elapsed time`() {
        val c = clock()
        c.onServerTime(serverAt1530, elapsed, elapsed)
        elapsed += 90_000

        assertEquals(ServerNow(serverAt1530.plusSeconds(90), confirmed = true), c.now())
    }

    @Test
    fun `changing the phone clock does not move server time`() {
        val c = clock()
        c.onServerTime(serverAt1530, elapsed, elapsed)

        wall = dhakaTime(2026, 11, 19, 9, 0).toEpochMilli()
        assertEquals(serverAt1530, c.now().time)

        wall += Duration.ofDays(30).toMillis()
        assertEquals(serverAt1530, c.now().time)
    }

    @Test
    fun `the middle of the request is used as the anchor`() {
        val c = clock()
        val sent = elapsed
        elapsed += 2_000 // the call took 2 s
        c.onServerTime(serverAt1530, sent, elapsed)

        // server stamped ~1 s into the call, so 1 s has passed since then
        assertEquals(serverAt1530.plusSeconds(1), c.now().time)
    }

    @Test
    fun `app restart in the same boot keeps the server time`() {
        clock().onServerTime(serverAt1530, elapsed, elapsed)
        wall = dhakaTime(2026, 11, 19, 9, 0).toEpochMilli()
        elapsed += 600_000

        val afterRestart = clock()
        assertEquals(ServerNow(serverAt1530.plusSeconds(600), confirmed = true), afterRestart.now())
    }

    @Test
    fun `after a reboot it uses phone clock plus the last offset, not confirmed`() {
        clock().onServerTime(serverAt1530, elapsed, elapsed) // offset +150 s
        boots = 8
        elapsed = 30_000
        wall = dhakaTime(2026, 11, 20, 18, 0).toEpochMilli()

        val now = clock().now()
        assertFalse(now.confirmed)
        assertEquals(dhakaTime(2026, 11, 20, 18, 2, 30), now.time)
    }

    @Test
    fun `after a reboot winding the phone back cannot go before the last server time`() {
        clock().onServerTime(serverAt1530, elapsed, elapsed)
        boots = 8
        elapsed = 30_000
        wall = dhakaTime(2026, 11, 19, 9, 0).toEpochMilli()

        val now = clock().now()
        assertFalse(now.confirmed)
        assertEquals(serverAt1530, now.time)
    }

    @Test
    fun `without a boot count a smaller elapsed time means a reboot`() {
        boots = null
        clock().onServerTime(serverAt1530, elapsed, elapsed)
        elapsed = 5_000

        assertFalse(clock().now().confirmed)
    }

    @Test
    fun `never synced falls back to the phone clock, not confirmed`() {
        val now = clock().now()
        assertFalse(now.confirmed)
        assertEquals(wall, now.time.toEpochMilli())
    }

    @Test
    fun `a new response replaces the anchor`() {
        val c = clock()
        c.onServerTime(serverAt1530, elapsed, elapsed)
        c.onServerTime(dhakaTime(2026, 11, 20, 16, 0), elapsed, elapsed)

        assertTrue(c.now().confirmed)
        assertEquals(dhakaTime(2026, 11, 20, 16, 0), c.now().time)
    }
}
