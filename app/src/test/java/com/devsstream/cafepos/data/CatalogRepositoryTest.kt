package com.devsstream.cafepos.data

import com.devsstream.cafepos.MemoryCatalogCache
import com.devsstream.cafepos.MemoryServerStore
import com.devsstream.cafepos.core.appendixA
import com.devsstream.cafepos.core.dhakaTime
import com.devsstream.cafepos.data.remote.fake.Failures
import com.devsstream.cafepos.data.remote.fake.FakePosServer
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CatalogRepositoryTest {

    private val cache = MemoryCatalogCache()
    private val server = FakePosServer(
        MemoryServerStore(),
        elapsedMs = { 10_000_000L },
        wallMs = { dhakaTime(2026, 11, 20, 15, 27, 30).toEpochMilli() },
        latencyMs = 0L..0L,
    ).apply { failures = Failures.OFF }

    @Test
    fun `refresh loads the server catalog and keeps a copy`() = runTest {
        val repo = CatalogRepository(server, cache)
        assertNull(repo.catalog.value)

        assertTrue(repo.refresh())

        assertEquals(appendixA, repo.catalog.value)
        assertEquals(1, cache.saved!!.version)
    }

    @Test
    fun `after a restart with no network the cached catalog is used`() = runTest {
        CatalogRepository(server, cache).refresh()
        server.failures = Failures.NETWORK_DOWN

        val afterRestart = CatalogRepository(server, cache)
        assertFalse(afterRestart.refresh())

        assertEquals(appendixA, afterRestart.catalog.value)
    }

    @Test
    fun `a failed refresh keeps the catalog we had`() = runTest {
        val repo = CatalogRepository(server, cache)
        repo.refresh()
        server.failures = Failures.NETWORK_DOWN

        assertFalse(repo.refresh())
        assertEquals(1, repo.catalog.value!!.version)
    }

    @Test
    fun `bumped catalog comes through on the next refresh`() = runTest {
        val repo = CatalogRepository(server, cache)
        repo.refresh()
        server.bumpCatalog()

        repo.refresh()

        assertEquals(2, repo.catalog.value!!.version)
        assertEquals(24_000L, repo.catalog.value!!.product("latte")!!.basePrice)
    }
}
