package com.devsstream.cafepos.time

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.time.Instant

data class TimeAnchor(
    val serverEpochMs: Long,
    val elapsedMs: Long,
    val bootCount: Int?,
    // server time minus the phone clock at that moment, only used after a reboot
    val wallOffsetMs: Long,
)

interface AnchorStore {
    fun load(): TimeAnchor?
    fun save(anchor: TimeAnchor)
}

data class ServerNow(val time: Instant, val confirmed: Boolean)

// The app's idea of server time (D1). Never reads the phone clock while it has an
// anchor from this boot, so changing the phone's date or time does nothing.
class ServerClock(
    private val store: AnchorStore,
    private val elapsedMs: () -> Long,
    private val wallMs: () -> Long,
    private val bootCount: () -> Int?,
) {
    @Volatile
    private var anchor: TimeAnchor? = store.load()

    // sentAt / receivedAt are elapsedRealtime around the request. The server stamped
    // its time somewhere in between, the middle is the best guess.
    fun onServerTime(serverTime: Instant, sentAtElapsed: Long, receivedAtElapsed: Long) {
        val middle = (sentAtElapsed + receivedAtElapsed) / 2
        val wallAtMiddle = wallMs() - (elapsedMs() - middle)
        val serverMs = serverTime.toEpochMilli()
        val newAnchor = TimeAnchor(serverMs, middle, bootCount(), serverMs - wallAtMiddle)
        anchor = newAnchor
        store.save(newAnchor)
    }

    fun now(): ServerNow {
        val a = anchor
        if (a != null && sameBoot(a)) {
            return ServerNow(Instant.ofEpochMilli(a.serverEpochMs + (elapsedMs() - a.elapsedMs)), confirmed = true)
        }
        // rebooted, or never talked to the server: best guess from the phone clock,
        // but never earlier than the last server time we saw
        val guess = wallMs() + (a?.wallOffsetMs ?: 0)
        val floor = a?.serverEpochMs ?: Long.MIN_VALUE
        return ServerNow(Instant.ofEpochMilli(maxOf(guess, floor)), confirmed = false)
    }

    private fun sameBoot(a: TimeAnchor): Boolean {
        val boots = bootCount()
        if (boots != null && a.bootCount != null) return boots == a.bootCount
        return elapsedMs() >= a.elapsedMs
    }
}

// server time once a second, for screens (prices change at 17:00 without a tap)
fun ServerClock.ticks(): Flow<ServerNow> = flow {
    while (true) {
        emit(now())
        delay(1_000)
    }
}
