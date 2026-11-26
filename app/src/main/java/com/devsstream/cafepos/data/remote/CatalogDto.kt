package com.devsstream.cafepos.data.remote

import com.devsstream.cafepos.core.catalog.Catalog
import com.devsstream.cafepos.core.catalog.ModifierGroup
import com.devsstream.cafepos.core.catalog.ModifierOption
import com.devsstream.cafepos.core.catalog.OptionRule
import com.devsstream.cafepos.core.catalog.OrderDiscount
import com.devsstream.cafepos.core.catalog.Product
import com.devsstream.cafepos.core.catalog.Promotion
import com.devsstream.cafepos.core.catalog.Size
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.time.LocalTime
import java.time.OffsetDateTime
import java.time.ZoneId

@Serializable
data class CatalogDto(
    val version: Int,
    val timeZone: String,
    val vatBps: Int,
    val products: List<ProductDto>,
    val groups: List<GroupDto> = emptyList(),
    val rules: List<RuleDto> = emptyList(),
    val promotions: List<PromotionDto> = emptyList(),
    val orderDiscounts: List<DiscountDto> = emptyList(),
)

@Serializable
data class ProductDto(
    val id: String,
    val name: String,
    val category: String,
    val basePricePoisha: Long,
    val sizes: List<SizeDto> = emptyList(),
    val groupIds: List<String> = emptyList(),
)

@Serializable
data class SizeDto(val id: String, val name: String, val extraPoisha: Long)

@Serializable
data class GroupDto(
    val id: String,
    val name: String,
    val options: List<OptionDto>,
    val max: Int? = null,
    val maxDistinct: Int? = null,
    val maxPerOption: Int? = null,
)

@Serializable
data class OptionDto(val id: String, val name: String, val pricePoisha: Long)

@Serializable
sealed interface RuleDto {
    @Serializable
    @SerialName("requires")
    data class Requires(val id: String, val optionId: String, val anyOf: List<String>) : RuleDto

    @Serializable
    @SerialName("excludes")
    data class Excludes(val id: String, val optionId: String, val others: List<String>) : RuleDto
}

@Serializable
sealed interface PromotionDto {
    @Serializable
    @SerialName("cheapest_free")
    data class CheapestFree(val id: String, val category: String, val every: Int) : PromotionDto

    @Serializable
    @SerialName("happy_hour")
    data class HappyHour(
        val id: String,
        val category: String,
        val percentBps: Int,
        val from: String,
        val until: String,
    ) : PromotionDto
}

@Serializable
sealed interface DiscountDto {
    @Serializable
    @SerialName("threshold")
    data class Threshold(val id: String, val minSubtotalPoisha: Long, val amountOffPoisha: Long) : DiscountDto

    @Serializable
    @SerialName("coupon")
    data class Coupon(
        val id: String,
        val code: String,
        val percentBps: Int,
        val capPoisha: Long? = null,
        val validUntil: String? = null,
    ) : DiscountDto
}

fun CatalogDto.toCatalog(): Catalog = Catalog(
    version = version,
    timeZone = ZoneId.of(timeZone),
    vatBps = vatBps,
    products = products.map { p ->
        Product(
            id = p.id,
            name = p.name,
            category = p.category,
            basePrice = p.basePricePoisha,
            sizes = p.sizes.map { Size(it.id, it.name, it.extraPoisha) },
            groupIds = p.groupIds,
        )
    },
    groups = groups.map { g ->
        ModifierGroup(
            id = g.id,
            name = g.name,
            options = g.options.map { ModifierOption(it.id, it.name, it.pricePoisha) },
            max = g.max,
            maxDistinct = g.maxDistinct,
            maxPerOption = g.maxPerOption,
        )
    },
    rules = rules.map { r ->
        when (r) {
            is RuleDto.Requires -> OptionRule.Requires(r.id, r.optionId, r.anyOf)
            is RuleDto.Excludes -> OptionRule.Excludes(r.id, r.optionId, r.others)
        }
    },
    promotions = promotions.map { p ->
        when (p) {
            is PromotionDto.CheapestFree -> Promotion.CheapestFree(p.id, p.category, p.every)
            is PromotionDto.HappyHour ->
                Promotion.HappyHour(p.id, p.category, p.percentBps, LocalTime.parse(p.from), LocalTime.parse(p.until))
        }
    },
    orderDiscounts = orderDiscounts.map { d ->
        when (d) {
            is DiscountDto.Threshold -> OrderDiscount.Threshold(d.id, d.minSubtotalPoisha, d.amountOffPoisha)
            is DiscountDto.Coupon -> OrderDiscount.Coupon(
                id = d.id,
                code = d.code,
                percentBps = d.percentBps,
                cap = d.capPoisha,
                validUntil = d.validUntil?.let { OffsetDateTime.parse(it).toInstant() },
            )
        }
    },
)
