package com.devsstream.cafepos.data

import com.devsstream.cafepos.core.catalog.Catalog
import com.devsstream.cafepos.data.remote.ApiJson
import com.devsstream.cafepos.data.remote.CatalogDto
import com.devsstream.cafepos.data.remote.HttpException
import com.devsstream.cafepos.data.remote.PosApi
import com.devsstream.cafepos.data.remote.toCatalog
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.io.IOException
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

// The menu always comes from the server's catalog. The last one is kept on disk so
// the till still sells with no network, even right after a restart.
@Singleton
class CatalogRepository @Inject constructor(
    private val api: PosApi,
    private val cache: CatalogCache,
) {
    private val current = MutableStateFlow(cache.load()?.toCatalog())
    val catalog: StateFlow<Catalog?> = current.asStateFlow()

    // false if the server couldn't be reached; the catalog we have stays in use
    suspend fun refresh(): Boolean {
        val response = try {
            api.getCatalog()
        } catch (e: IOException) {
            return false
        } catch (e: HttpException) {
            return false
        }
        cache.save(response.catalog)
        current.value = response.catalog.toCatalog()
        return true
    }
}
