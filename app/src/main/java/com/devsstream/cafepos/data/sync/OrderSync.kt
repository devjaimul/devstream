package com.devsstream.cafepos.data.sync

import com.devsstream.cafepos.data.OrderRepository
import com.devsstream.cafepos.data.local.OrderStatus
import com.devsstream.cafepos.data.remote.PosApi
import kotlinx.coroutines.CancellationException
import javax.inject.Inject

enum class SyncOutcome { DONE, RETRY_LATER }

// One pass over the queue, oldest paid order first. If an upload fails the pass stops
// there, so the orders behind it wait and the server still gets them in creation order.
// Once that order is FAILED (5th failure) it stops blocking and the rest go out.
class OrderSync @Inject constructor(
    private val orders: OrderRepository,
    private val api: PosApi,
) {
    suspend fun run(): SyncOutcome {
        while (true) {
            val next = orders.nextToSync() ?: return SyncOutcome.DONE
            try {
                // duplicate = true just means an earlier try already got through (C3)
                val response = api.postOrder(next.toDto())
                orders.markSynced(next.order.id, response.serverOrderNo)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                val status = orders.syncFailed(next.order.id, e.message ?: e.javaClass.simpleName)
                if (status != OrderStatus.FAILED) return SyncOutcome.RETRY_LATER
            }
        }
    }
}
