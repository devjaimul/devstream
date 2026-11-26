package com.devsstream.cafepos

import com.devsstream.cafepos.data.CatalogCache
import com.devsstream.cafepos.data.remote.CatalogDto
import com.devsstream.cafepos.data.remote.fake.FakeServerStore
import com.devsstream.cafepos.data.remote.fake.ServerState
import com.devsstream.cafepos.time.AnchorStore
import com.devsstream.cafepos.time.TimeAnchor

// In-memory stand-ins for the file / SharedPreferences stores. Reusing one instance
// across two objects is how the tests simulate an app restart.

class MemoryServerStore : FakeServerStore {
    var saved: ServerState? = null
    override fun load() = saved
    override fun save(state: ServerState) {
        saved = state
    }
}

class MemoryAnchorStore : AnchorStore {
    var saved: TimeAnchor? = null
    override fun load() = saved
    override fun save(anchor: TimeAnchor) {
        saved = anchor
    }
}

class MemoryCatalogCache : CatalogCache {
    var saved: CatalogDto? = null
    override fun load() = saved
    override fun save(catalog: CatalogDto) {
        saved = catalog
    }
}
