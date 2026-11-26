package com.devsstream.cafepos.summary

import com.devsstream.cafepos.core.catalog.Catalog
import com.devsstream.cafepos.core.order.ItemSelection
import com.devsstream.cafepos.core.pricing.OrderLine
import com.devsstream.cafepos.core.pricing.priceOrder
import com.devsstream.cafepos.core.time.businessDay
import com.devsstream.cafepos.core.validation.validate
import com.devsstream.cafepos.data.CatalogRepository
import com.devsstream.cafepos.data.ImportedPayment
import com.devsstream.cafepos.data.OrderRepository
import com.devsstream.cafepos.data.PaidOrder
import com.devsstream.cafepos.data.remote.ApiJson
import com.devsstream.cafepos.data.remote.PaymentType
import com.devsstream.cafepos.data.sync.SyncScheduler
import com.devsstream.cafepos.time.ServerNow
import kotlinx.serialization.Serializable
import java.time.LocalDate
import java.time.OffsetDateTime
import javax.inject.Inject

@Serializable
data class SeedFile(val catalogVersion: Int, val orders: List<SeedOrder>)

@Serializable
data class SeedOrder(
    val ref: String,
    val serverTime: String,
    val coupon: String? = null,
    val allCard: Boolean = false,
    val cardPoisha: Long = 0,
    val lines: List<SeedLine>,
)

@Serializable
data class SeedLine(
    val productId: String,
    val sizeId: String? = null,
    val options: Map<String, Int> = emptyMap(),
    val quantity: Int,
)

sealed interface SeedResult {
    data class Loaded(val day: LocalDate, val added: Int) : SeedResult
    data class WrongCatalog(val needed: Int, val current: Int) : SeedResult
    data object NoCatalog : SeedResult
}

fun readAppendixB(): SeedFile {
    val resource = checkNotNull(SeedFile::class.java.getResource("/seed/appendix-b.json")) { "seed file missing" }
    return ApiJson.decodeFromString(resource.readText())
}

// Priced by our own engine at the order's server time, like a sale made then.
fun SeedOrder.toPaidOrder(catalog: Catalog): PaidOrder {
    val time = OffsetDateTime.parse(serverTime).toInstant()
    val orderLines = lines.map { OrderLine(ItemSelection(it.productId, it.sizeId, it.options), it.quantity) }
    for (line in orderLines) {
        val product = requireNotNull(catalog.product(line.selection.productId)) { "$ref: unknown product" }
        val problems = validate(line.selection, product, catalog)
        require(problems.isEmpty()) { "$ref: ${problems.joinToString { it.message }}" }
    }

    val price = priceOrder(orderLines, catalog, coupon, time)
    val card = if (allCard) price.payable else cardPoisha
    val payments = buildList {
        if (card > 0) add(ImportedPayment(PaymentType.CARD, card, cardRef = "appendix-b-$ref"))
        if (price.payable > card) add(ImportedPayment(PaymentType.CASH, price.payable - card))
    }
    return PaidOrder("appendix-b-$ref", orderLines, price, coupon, catalog, ServerNow(time, confirmed = true), payments)
}

class SeedLoader @Inject constructor(
    private val orders: OrderRepository,
    private val catalogs: CatalogRepository,
    private val sync: SyncScheduler,
) {
    // In server time order, so receipt numbers follow the clock. Loading again adds nothing.
    suspend fun loadAppendixB(seed: SeedFile = readAppendixB()): SeedResult {
        val catalog = catalogs.catalog.value ?: return SeedResult.NoCatalog
        // Appendix B is meant for the Appendix A prices; on v2 the totals would differ
        if (catalog.version != seed.catalogVersion) return SeedResult.WrongCatalog(seed.catalogVersion, catalog.version)

        val paid = seed.orders.map { it.toPaidOrder(catalog) }.sortedBy { it.at.time }
        val added = orders.importPaidOrders(paid)
        if (added > 0) sync.requestSync()
        return SeedResult.Loaded(paid.first().at.time.businessDay(catalog.timeZone), added)
    }
}
