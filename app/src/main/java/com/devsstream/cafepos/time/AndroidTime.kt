package com.devsstream.cafepos.time

import android.content.Context
import android.provider.Settings
import androidx.core.content.edit

class PrefsAnchorStore(context: Context) : AnchorStore {

    private val prefs = context.getSharedPreferences("server_clock", Context.MODE_PRIVATE)

    override fun load(): TimeAnchor? {
        if (!prefs.contains(KEY_SERVER)) return null
        return TimeAnchor(
            serverEpochMs = prefs.getLong(KEY_SERVER, 0),
            elapsedMs = prefs.getLong(KEY_ELAPSED, 0),
            bootCount = prefs.getInt(KEY_BOOT, -1).takeIf { it >= 0 },
            wallOffsetMs = prefs.getLong(KEY_OFFSET, 0),
        )
    }

    // commit, not apply: it must be on disk before a sale uses it
    override fun save(anchor: TimeAnchor) = prefs.edit(commit = true) {
        putLong(KEY_SERVER, anchor.serverEpochMs)
        putLong(KEY_ELAPSED, anchor.elapsedMs)
        putInt(KEY_BOOT, anchor.bootCount ?: -1)
        putLong(KEY_OFFSET, anchor.wallOffsetMs)
    }

    private companion object {
        const val KEY_SERVER = "server_epoch_ms"
        const val KEY_ELAPSED = "elapsed_ms"
        const val KEY_BOOT = "boot_count"
        const val KEY_OFFSET = "wall_offset_ms"
    }
}

fun readBootCount(context: Context): Int? = try {
    Settings.Global.getInt(context.contentResolver, Settings.Global.BOOT_COUNT)
} catch (e: Settings.SettingNotFoundException) {
    null
}
