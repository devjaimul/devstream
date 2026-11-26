package com.devsstream.cafepos.data

import com.devsstream.cafepos.core.catalog.Catalog
import com.devsstream.cafepos.data.remote.ApiJson
import com.devsstream.cafepos.data.remote.CatalogDto
import com.devsstream.cafepos.data.remote.PosApi
import com.devsstream.cafepos.data.remote.toCatalog
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

interface CatalogCache {
    fun load(): CatalogDto?
    fun save(catalog: CatalogDto)
}

class FileCatalogCache(private val file: File) : CatalogCache {
    // a broken file is treated like no file, the next refresh writes a good one
    override fun load(): CatalogDto? =
        runCatching { ApiJson.decodeFromString<CatalogDto>(file.readText()) }.getOrNull()

    override fun save(catalog: CatalogDto) {
        file.writeText(ApiJson.encodeToString(catalog))
    }
}

// The menu always comes from the server's catalog. The last good one is kept on disk
// so the till still sells with no network, even right after a restart.
@Singleton
class CatalogRepository @Inject constructor(
    private val api: PosApi,
    private val cache: CatalogCache,
) {
    private val current = MutableStateFlow(runCatching { cache.load()?.toCatalog() }.getOrNull())
    val catalog: StateFlow<Catalog?> = current.asStateFlow()

    // False if the server couldn't be reached or sent a catalog we can't read. Either
    // way the catalog we have stays in use; a bad catalog never replaces a good one.
    suspend fun refresh(): Boolean = withContext(Dispatchers.IO) {
        try {
            val dto = api.getCatalog().catalog
            val parsed = dto.toCatalog()
            cache.save(dto)
            current.value = parsed
            true
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            false
        }
    }
}
