package com.devsstream.cafepos.data.remote

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import java.io.IOException

// The fake server lives inside the app, so airplane mode would not stop it. This makes
// every call fail the way it would with a real server when the phone has no network.
class OfflineGateApi(
    private val api: PosApi,
    private val isOnline: () -> Boolean,
) : PosApi {

    override suspend fun getCatalog(): CatalogResponse {
        checkOnline()
        return api.getCatalog()
    }

    override suspend fun postOrder(order: OrderDto): PostOrderResponse {
        checkOnline()
        return api.postOrder(order)
    }

    private fun checkOnline() {
        if (!isOnline()) throw IOException("no network")
    }
}

fun isOnline(context: Context): Boolean {
    val connectivity = context.getSystemService(ConnectivityManager::class.java)
    val caps = connectivity.getNetworkCapabilities(connectivity.activeNetwork) ?: return false
    return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
}
