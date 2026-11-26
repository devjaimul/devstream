package com.devsstream.cafepos.core.order

// What the cashier picked for one product. options = optionId -> quantity.
data class ItemSelection(
    val productId: String,
    val sizeId: String? = null,
    val options: Map<String, Int> = emptyMap(),
)
