package com.devsstream.cafepos.data.remote

import kotlinx.serialization.Serializable

// What the app talks to. The only implementation is FakePosServer (Appendix C),
// a real one would be a Retrofit service with the same two calls.
interface PosApi {
    suspend fun getCatalog(): CatalogResponse

    // Idempotent on order.clientOrderId
    suspend fun postOrder(order: OrderDto): PostOrderResponse
}

@Serializable
data class CatalogResponse(val serverTime: String, val catalog: CatalogDto)

@Serializable
data class PostOrderResponse(val serverOrderNo: Long, val serverTime: String, val duplicate: Boolean)

class HttpException(val code: Int, message: String) : Exception("HTTP $code: $message")
