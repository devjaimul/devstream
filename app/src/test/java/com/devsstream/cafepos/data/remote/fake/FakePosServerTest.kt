package com.devsstream.cafepos.data.remote.fake

import com.devsstream.cafepos.core.appendixA
import com.devsstream.cafepos.core.dhakaTime
import com.devsstream.cafepos.data.remote.HttpException
import com.devsstream.cafepos.data.remote.OrderDto
import com.devsstream.cafepos.data.remote.OrderLineDto
import com.devsstream.cafepos.data.remote.PaymentDto
import com.devsstream.cafepos.data.remote.PaymentType
import com.devsstream.cafepos.data.remote.toCatalog
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.time.Duration
import java.time.Instant
import kotlin.random.Random

class FakePosServerTest {

    private class MemoryStore : FakeServerStore {
        var saved: ServerState? = null
        override fun load() = saved
        override fun save(state: ServerState) {
            saved = state
        }
    }

    private val store = MemoryStore()

    // fake phone clocks we can move by hand
    private var elapsed = 5_000_000L
    private var wall = dhakaTime(2026, 11, 20, 15, 30).toEpochMilli()

    private fun server(failures: Failures = Failures.OFF, random: Random = Random(1)) =
        FakePosServer(store, { elapsed }, { wall }, random).also { it.failures = failures }

    private fun order(id: String) = OrderDto(
        clientOrderId = id,
        receiptNo = "T1-20261120-0001",
        createdAt = "2026-11-20T09:30:00Z",
        businessDay = "2026-11-20",
        catalogVersion = 1,
        lines = listOf(
            OrderLineDto("brownie", label = "Brownie", category = "BAKERY", quantity = 1, unitPricePoisha = 12_000, totalPoisha = 12_000),
        ),
        promotions = emptyList(),
        subtotalPoisha = 12_000,
        vatPoisha = 600,
        totalPoisha = 12_600,
        payablePoisha = 12_600,
        payments = listOf(PaymentDto(PaymentType.CASH, 12_600, tenderedPoisha = 20_000, changePoisha = 7_400)),
    )

    @Test
    fun `getCatalog returns Appendix A with version and server time`() = runTest {
        val response = server().getCatalog()

        assertEquals(appendixA, response.catalog.toCatalog())
        assertEquals(1, response.catalog.version)
        assertEquals(Instant.ofEpochMilli(wall + 150_000).toString(), response.serverTime)
    }

    @Test
    fun `server clock is 150 s ahead and ignores the phone clock`() {
        val s = server()
        val start = s.now()
        assertEquals(Instant.ofEpochMilli(wall).plusSeconds(150), start)

        wall -= Duration.ofDays(1).toMillis() // cashier sets the phone to 19 Nov
        elapsed += 10_000
        assertEquals(start.plusSeconds(10), s.now())
    }

    @Test
    fun `server clock carries on after an app restart`() {
        val start = server().now()

        wall -= Duration.ofDays(1).toMillis()
        elapsed += 60_000
        val afterRestart = server()

        assertEquals(start.plusSeconds(60), afterRestart.now())
    }

    @Test
    fun `after a phone reboot the clock starts again from the wall clock`() {
        server()
        elapsed = 1_000 // elapsedRealtime restarts on boot
        wall = dhakaTime(2026, 11, 21, 8, 0).toEpochMilli()

        assertEquals(Instant.ofEpochMilli(wall).plusSeconds(150), server().now())
    }

    @Test
    fun `setServerTime hook moves the server clock`() = runTest {
        val s = server()
        s.setServerTime(dhakaTime(2026, 12, 1, 15, 30))
        elapsed += 1_000

        assertEquals(dhakaTime(2026, 12, 1, 15, 30, 1), s.now())
    }

    @Test
    fun `same clientOrderId twice is one order with duplicate true`() = runTest {
        val s = server()
        val first = s.postOrder(order("a"))
        val second = s.postOrder(order("a"))

        assertFalse(first.duplicate)
        assertTrue(second.duplicate)
        assertEquals(first.serverOrderNo, second.serverOrderNo)
        assertEquals(1, s.orders().size)
    }

    @Test
    fun `saved then 500, the retry is a duplicate and there is still one order`() = runTest {
        val s = server(Failures.ERROR_AFTER_SAVE)
        val error = runCatching { s.postOrder(order("a")) }.exceptionOrNull()
        assertTrue(error is HttpException && error.code == 500)

        s.failures = Failures.OFF
        val retry = s.postOrder(order("a"))

        assertTrue(retry.duplicate)
        assertEquals(1L, retry.serverOrderNo)
        assertEquals(1, s.orders().size)
    }

    @Test
    fun `orders survive an app restart`() = runTest {
        server().postOrder(order("a"))

        val afterRestart = server()
        assertTrue(afterRestart.postOrder(order("a")).duplicate)
        assertEquals(1, afterRestart.orders().size)
    }

    @Test
    fun `network down throws IOException and saves nothing`() = runTest {
        val s = server(Failures.NETWORK_DOWN)

        assertTrue(runCatching { s.postOrder(order("a")) }.exceptionOrNull() is IOException)
        assertTrue(runCatching { s.getCatalog() }.exceptionOrNull() is IOException)
        assertEquals(0, s.orders().size)
    }

    @Test
    fun `random mode fails about 20 percent with IOException and 10 percent with 500`() = runTest {
        val s = server(Failures.RANDOM, Random(42))
        var dropped = 0
        var error500 = 0
        repeat(1_000) { i ->
            when (runCatching { s.postOrder(order("o-$i")) }.exceptionOrNull()) {
                null -> Unit
                is IOException -> dropped++
                is HttpException -> error500++
            }
        }

        assertTrue("dropped=$dropped", dropped in 170..230)
        assertTrue("error500=$error500", error500 in 70..130)
        // a 500 still saved the order, a dropped call did not
        assertEquals(1_000 - dropped, s.orders().size)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `every call takes 300 to 2000 ms`() = runTest {
        val s = server()
        repeat(50) { i ->
            val before = testScheduler.currentTime
            s.postOrder(order("o-$i"))
            val took = testScheduler.currentTime - before
            assertTrue("took $took ms", took in 300..2_000)
        }
    }

    @Test
    fun `bumpCatalog serves version 2 with latte at 240`() = runTest {
        val s = server()
        assertTrue(s.bumpCatalog())

        val catalog = s.getCatalog().catalog.toCatalog()
        assertEquals(2, catalog.version)
        assertEquals(24_000L, catalog.product("latte")!!.basePrice)
        assertFalse("there is no v3 file", s.bumpCatalog())
    }
}
