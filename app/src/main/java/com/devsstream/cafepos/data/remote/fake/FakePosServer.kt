package com.devsstream.cafepos.data.remote.fake

import com.devsstream.cafepos.data.remote.ApiJson
import com.devsstream.cafepos.data.remote.CatalogDto
import com.devsstream.cafepos.data.remote.CatalogResponse
import com.devsstream.cafepos.data.remote.HttpException
import com.devsstream.cafepos.data.remote.OrderDto
import com.devsstream.cafepos.data.remote.PosApi
import com.devsstream.cafepos.data.remote.PostOrderResponse
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.IOException
import java.time.Instant
import kotlin.random.Random

enum class Failures {
    RANDOM, // Appendix C: 20% IOException, plus 10% of postOrder save and then return 500
    OFF,
    NETWORK_DOWN, // every call throws IOException
    ERROR_AFTER_SAVE, // every postOrder saves, then returns 500
}

// Appendix C fake API. Every call waits 300-2000 ms and may fail.
class FakePosServer(
    private val store: FakeServerStore,
    elapsedMs: () -> Long,
    wallMs: () -> Long,
    private val random: Random = Random.Default,
    private val latencyMs: LongRange = 300L..2_000L,
) : PosApi {

    private val mutex = Mutex()
    private var state = store.load() ?: ServerState()
    private val clock = FakeServerClock(elapsedMs, wallMs, state.clock)

    @Volatile
    var failures = Failures.RANDOM

    init {
        save(state.copy(clock = clock.anchor))
    }

    override suspend fun getCatalog(): CatalogResponse {
        val outcome = roll(isPost = false)
        delay(random.nextLong(latencyMs.first, latencyMs.last + 1))
        if (outcome == Outcome.DROP) throw IOException("fake server: connection reset")

        val version = mutex.withLock { state.catalogVersion }
        return CatalogResponse(serverTime = clock.now().toString(), catalog = readCatalog(version))
    }

    override suspend fun postOrder(order: OrderDto): PostOrderResponse {
        val outcome = roll(isPost = true)
        delay(random.nextLong(latencyMs.first, latencyMs.last + 1))
        if (outcome == Outcome.DROP) throw IOException("fake server: connection reset")

        // goes over the "wire" as JSON, like a real request body
        val body = ApiJson.encodeToString(order)
        val received = ApiJson.decodeFromString<OrderDto>(body)

        val response = mutex.withLock {
            val now = clock.now().toString()
            val existing = state.orders[received.clientOrderId]
            if (existing != null) {
                PostOrderResponse(existing.serverOrderNo, now, duplicate = true)
            } else {
                val no = state.nextOrderNo
                save(
                    state.copy(
                        nextOrderNo = no + 1,
                        orders = state.orders + (received.clientOrderId to StoredOrder(no, now, body)),
                    ),
                )
                PostOrderResponse(no, now, duplicate = false)
            }
        }

        if (outcome == Outcome.SAVED_THEN_500) throw HttpException(500, "fake server: saved, then failed to answer")
        return response
    }

    // ---- test hooks ----

    suspend fun setServerTime(time: Instant) = mutex.withLock {
        clock.set(time)
        save(state.copy(clock = clock.anchor))
    }

    // v1 -> v2 (Latte base 240). Returns false if there is no next catalog file.
    suspend fun bumpCatalog(): Boolean = mutex.withLock {
        val next = state.catalogVersion + 1
        if (catalogResource(next) == null) return@withLock false
        save(state.copy(catalogVersion = next))
        true
    }

    fun now(): Instant = clock.now()

    suspend fun orders(): List<OrderDto> = mutex.withLock {
        state.orders.values.sortedBy { it.serverOrderNo }.map { ApiJson.decodeFromString<OrderDto>(it.body) }
    }

    // ---- internals ----

    private enum class Outcome { OK, DROP, SAVED_THEN_500 }

    private fun roll(isPost: Boolean): Outcome = when (failures) {
        Failures.OFF -> Outcome.OK
        Failures.NETWORK_DOWN -> Outcome.DROP
        Failures.ERROR_AFTER_SAVE -> if (isPost) Outcome.SAVED_THEN_500 else Outcome.OK
        Failures.RANDOM -> {
            val r = random.nextInt(100)
            when {
                r < 20 -> Outcome.DROP
                isPost && r < 30 -> Outcome.SAVED_THEN_500
                else -> Outcome.OK
            }
        }
    }

    private fun save(newState: ServerState) {
        state = newState
        store.save(newState)
    }

    private fun catalogResource(version: Int) = javaClass.getResource("/fake-server/catalog-v$version.json")

    private fun readCatalog(version: Int): CatalogDto {
        val resource = checkNotNull(catalogResource(version)) { "no catalog file for version $version" }
        return ApiJson.decodeFromString(resource.readText())
    }
}
