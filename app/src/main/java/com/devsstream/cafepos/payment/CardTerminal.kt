package com.devsstream.cafepos.payment

import com.devsstream.terminal.LegacyCardTerminal
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

sealed interface CardResult {
    // authCode is null when the result came from queryStatus, not the callback
    data class Approved(val authCode: String?) : CardResult
    data class Declined(val reason: String?) : CardResult
    data object Unknown : CardResult
}

class CardTerminal(
    private val terminal: LegacyCardTerminal,
    private val callbackTimeout: Duration = 15.seconds,
    private val log: (String) -> Unit = {},
) {

    // Calls the SDK's charge() exactly once. Never call this twice with the same ref,
    // that is a second real charge. Use status(ref) to find out what happened.
    suspend fun charge(amountPoisha: Long, ref: String): CardResult {
        val fromCallback = withTimeoutOrNull(callbackTimeout) {
            suspendCancellableCoroutine { cont ->
                val answered = AtomicBoolean(false)

                fun answer(result: CardResult) = when {
                    !answered.compareAndSet(false, true) -> log("ignored duplicate callback for $ref: $result")
                    !cont.isActive -> log("ignored callback for $ref, nobody is waiting: $result")
                    else -> cont.resume(result)
                }

                terminal.charge(amountPoisha, ref, object : LegacyCardTerminal.Callback {
                    override fun onApproved(authCode: String) = answer(CardResult.Approved(authCode))
                    override fun onDeclined(reason: String) = answer(CardResult.Declined(reason))
                })
            }
        }
        if (fromCallback != null) return fromCallback

        log("no callback for $ref after $callbackTimeout, asking the terminal")
        return status(ref)
    }

    // Never charges. Safe to call as often as we like, e.g. after a restart.
    fun status(ref: String): CardResult = when (terminal.queryStatus(ref)) {
        "APPROVED" -> CardResult.Approved(authCode = null)
        "DECLINED" -> CardResult.Declined(reason = null)
        else -> CardResult.Unknown
    }
}
