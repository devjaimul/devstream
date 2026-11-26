package com.devsstream.cafepos.data.remote

import com.devsstream.cafepos.core.appendixA
import org.junit.Assert.assertEquals
import org.junit.Test

class CatalogDtoTest {

    private fun load(name: String): CatalogDto {
        val text = javaClass.getResourceAsStream("/fake-server/$name")!!.bufferedReader().use { it.readText() }
        return ApiJson.decodeFromString(text)
    }

    @Test
    fun `catalog-v1 json is exactly Appendix A`() {
        assertEquals(appendixA, load("catalog-v1.json").toCatalog())
    }

    @Test
    fun `catalog-v2 only changes the version and latte base to 240`() {
        val v2 = load("catalog-v2.json").toCatalog()
        val expected = appendixA.copy(
            version = 2,
            products = appendixA.products.map { if (it.id == "latte") it.copy(basePrice = 24_000) else it },
        )
        assertEquals(expected, v2)
    }
}
