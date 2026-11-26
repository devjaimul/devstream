package com.devsstream.cafepos.time

import com.devsstream.cafepos.MemoryAnchorStore
import com.devsstream.cafepos.MemoryServerStore
import com.devsstream.cafepos.core.bdt
import com.devsstream.cafepos.core.dhakaTime
import com.devsstream.cafepos.core.pricing.priceOrder
import com.devsstream.cafepos.core.tc06Lines
import com.devsstream.cafepos.core.time.businessDay
import com.devsstream.cafepos.data.remote.ClockSyncingApi
import com.devsstream.cafepos.data.remote.fake.Failures
import com.devsstream.cafepos.data.remote.fake.FakePosServer
import com.devsstream.cafepos.data.remote.toCatalog
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

// Fake server, ServerClock and pricing wired together the way the app does it.
class Tc09ServerTimeTest {

    private var elapsed = 10_000_000L
    // real time 15:27:30, so the fake server (150 s ahead) says 15:30:00
    private var wall = dhakaTime(2026, 11, 20, 15, 27, 30).toEpochMilli()

    private val server = FakePosServer(MemoryServerStore(), { elapsed }, { wall }).apply { failures = Failures.OFF }
    private val clock = ServerClock(MemoryAnchorStore(), { elapsed }, { wall }, { 1 })
    private val api = ClockSyncingApi(server, clock, { elapsed })

    @Test
    fun `TC-09 phone says 19 Nov 09_00 offline, sale is priced like TC-06 and belongs to 20 Nov`() = runTest {
        val catalog = api.getCatalog().catalog.toCatalog()

        wall = dhakaTime(2026, 11, 19, 9, 0).toEpochMilli() // cashier changes the phone clock
        server.failures = Failures.NETWORK_DOWN // and we're offline

        val now = clock.now()
        assertTrue(now.confirmed)
        assertEquals(dhakaTime(2026, 11, 20, 15, 30), now.time)

        val price = priceOrder(tc06Lines, catalog, "DEVS10", now.time)
        assertEquals(bdt(1094), price.payable)
        assertEquals(LocalDate.of(2026, 11, 20), now.time.businessDay(catalog.timeZone))
    }
}
