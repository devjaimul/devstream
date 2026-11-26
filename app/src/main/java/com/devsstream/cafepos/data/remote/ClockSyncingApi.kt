package com.devsstream.cafepos.data.remote

import com.devsstream.cafepos.time.ServerClock
import java.time.Instant

// Wraps the real API so every successful response re-anchors the server clock.
class ClockSyncingApi(
    private val api: PosApi,
    private val clock: ServerClock,
    private val elapsedMs: () -> Long,
) : PosApi {

    override suspend fun getCatalog(): CatalogResponse {
        val sentAt = elapsedMs()
        val response = api.getCatalog()
        clock.onServerTime(Instant.parse(response.serverTime), sentAt, elapsedMs())
        return response
    }

    override suspend fun postOrder(order: OrderDto): PostOrderResponse {
        val sentAt = elapsedMs()
        val response = api.postOrder(order)
        clock.onServerTime(Instant.parse(response.serverTime), sentAt, elapsedMs())
        return response
    }
}
