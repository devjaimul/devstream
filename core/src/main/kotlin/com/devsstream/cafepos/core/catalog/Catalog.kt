package com.devsstream.cafepos.core.catalog

import java.time.ZoneId

// All money in the catalog is Long poisha (1 BDT = 100 poisha).
data class Catalog(
    val version: Int,
    val timeZone: ZoneId,
    val vatBps: Int,
    val products: List<Product>,
    val groups: List<ModifierGroup>,
    val rules: List<OptionRule>,
    val promotions: List<Promotion>,
    val orderDiscounts: List<OrderDiscount>,
) {
    fun product(id: String): Product? = products.find { it.id == id }

    fun group(id: String): ModifierGroup? = groups.find { it.id == id }

    fun option(id: String): ModifierOption? =
        groups.firstNotNullOfOrNull { g -> g.options.find { it.id == id } }
}

data class Product(
    val id: String,
    val name: String,
    val category: String,
    val basePrice: Long,
    val sizes: List<Size> = emptyList(),
    val groupIds: List<String> = emptyList(),
)

data class Size(
    val id: String,
    val name: String,
    val extraPrice: Long,
)

// null limit = no limit of that kind
data class ModifierGroup(
    val id: String,
    val name: String,
    val options: List<ModifierOption>,
    val max: Int? = null,
    val maxDistinct: Int? = null,
    val maxPerOption: Int? = null,
)

data class ModifierOption(
    val id: String,
    val name: String,
    val price: Long,
)

sealed interface OptionRule {
    val id: String
    val optionId: String

    data class Requires(
        override val id: String,
        override val optionId: String,
        val anyOf: List<String>,
    ) : OptionRule

    data class Excludes(
        override val id: String,
        override val optionId: String,
        val others: List<String>,
    ) : OptionRule
}
