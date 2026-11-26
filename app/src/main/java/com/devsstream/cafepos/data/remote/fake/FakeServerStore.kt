package com.devsstream.cafepos.data.remote.fake

import com.devsstream.cafepos.data.remote.ApiJson
import kotlinx.serialization.Serializable
import java.io.File

// Everything the fake server remembers. Kept apart from the app's Room database,
// like a real server on another machine, so killing the app doesn't wipe it.
@Serializable
data class ServerState(
    val catalogVersion: Int = 1,
    val nextOrderNo: Long = 1,
    val orders: Map<String, StoredOrder> = emptyMap(),
    val clock: ClockAnchor? = null,
)

@Serializable
data class StoredOrder(val serverOrderNo: Long, val receivedAt: String, val body: String)

interface FakeServerStore {
    fun load(): ServerState?
    fun save(state: ServerState)
}

class FileServerStore(private val file: File) : FakeServerStore {
    override fun load(): ServerState? =
        if (file.exists()) ApiJson.decodeFromString<ServerState>(file.readText()) else null

    override fun save(state: ServerState) {
        file.writeText(ApiJson.encodeToString(state))
    }
}
