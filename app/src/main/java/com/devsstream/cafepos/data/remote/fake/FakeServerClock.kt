package com.devsstream.cafepos.data.remote.fake

import kotlinx.serialization.Serializable
import java.time.Instant

@Serializable
data class ClockAnchor(val serverEpochMs: Long, val elapsedMs: Long)

// The fake server's own clock. It reads the wall clock once to start, then only moves
// with the monotonic clock (SystemClock.elapsedRealtime), so changing the phone's clock
// does nothing to it. The anchor is saved, so it also survives an app restart.
class FakeServerClock(
    private val elapsedMs: () -> Long,
    private val wallMs: () -> Long,
    saved: ClockAnchor?,
) {
    // elapsedRealtime restarts from 0 on reboot; then the saved anchor is useless
    // and we start again from the wall clock, like a real server would resync NTP.
    var anchor: ClockAnchor = saved?.takeIf { it.elapsedMs <= elapsedMs() } ?: fresh()
        private set

    fun now(): Instant = Instant.ofEpochMilli(anchor.serverEpochMs + (elapsedMs() - anchor.elapsedMs))

    fun set(time: Instant) {
        anchor = ClockAnchor(time.toEpochMilli(), elapsedMs())
    }

    private fun fresh() = ClockAnchor(wallMs() + AHEAD_OF_REAL_TIME_MS, elapsedMs())

    companion object {
        const val AHEAD_OF_REAL_TIME_MS = 150_000L
    }
}
