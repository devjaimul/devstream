package com.devsstream.cafepos.data.remote

import com.devsstream.cafepos.MemoryServerStore
import com.devsstream.cafepos.core.dhakaTime
import com.devsstream.cafepos.data.remote.fake.Failures
import com.devsstream.cafepos.data.remote.fake.FakePosServer
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

class OfflineGateApiTest {

    private var online = false
    private val server = FakePosServer(
        MemoryServerStore(),
        elapsedMs = { 10_000_000L },
        wallMs = { dhakaTime(2026, 11, 20, 15, 27, 30).toEpochMilli() },
        latencyMs = 0L..0L,
    ).apply { failures = Failures.OFF }
    private val api = OfflineGateApi(server) { online }

    @Test
    fun `no network fails like a real call, back online it goes through`() = runTest {
        assertTrue(runCatching { api.getCatalog() }.exceptionOrNull() is IOException)

        online = true
        assertEquals(1, api.getCatalog().catalog.version)
    }
}
