package com.devsstream.cafepos.payment

import com.devsstream.cafepos.data.OrderRepository
import com.devsstream.cafepos.data.local.OrderStatus
import com.devsstream.cafepos.data.local.PaymentEntity
import com.devsstream.cafepos.data.sync.SyncScheduler
import com.devsstream.cafepos.di.AppScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

// Card charges run in the app's scope, not a screen's, so rotating or leaving the
// screen doesn't stop the wait. Results go to Room and the screens just watch Room.
@Singleton
class CardPayments @Inject constructor(
    private val orders: OrderRepository,
    private val terminal: CardTerminal,
    private val sync: SyncScheduler,
    @param:AppScope private val scope: CoroutineScope,
) {
    private val waiting = MutableStateFlow(emptySet<String>())

    // refs with a charge() still waiting for its answer in this process
    val waitingRefs: StateFlow<Set<String>> = waiting.asStateFlow()

    // The payment must already be saved as PENDING with its ref (D3).
    fun start(payment: PaymentEntity) {
        val ref = checkNotNull(payment.cardRef) { "not a card payment" }
        check(markWaiting(ref)) { "already charging $ref" }
        scope.launch {
            try {
                record(ref, terminal.charge(payment.amount, ref))
            } finally {
                waiting.update { it - ref }
            }
        }
    }

    // On app start. A card left PENDING means the process died while waiting for it:
    // ask the terminal what happened, never charge it again (TC-17).
    suspend fun recover() {
        for (payment in orders.openCardPayments()) {
            val ref = checkNotNull(payment.cardRef)
            if (ref !in waiting.value) record(ref, terminal.status(ref))
        }
    }

    // "Check again" on "Card result unknown"
    suspend fun checkAgain(ref: String) {
        if (ref !in waiting.value) record(ref, terminal.status(ref))
    }

    // "Not charged, try again". Asks the terminal one more time first, in case it went through.
    suspend fun markNotCharged(ref: String) {
        check(ref !in waiting.value) { "still waiting for $ref" }
        when (val result = terminal.status(ref)) {
            CardResult.Unknown -> orders.voidCardPayment(ref)
            else -> record(ref, result)
        }
    }

    // false if this ref is already being charged
    private fun markWaiting(ref: String): Boolean {
        var added = false
        waiting.update { refs ->
            added = ref !in refs
            refs + ref
        }
        return added
    }

    private suspend fun record(ref: String, result: CardResult) {
        if (orders.finishCardPayment(ref, result) == OrderStatus.PENDING) sync.requestSync()
    }
}
