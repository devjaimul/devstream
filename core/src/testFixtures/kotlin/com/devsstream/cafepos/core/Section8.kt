package com.devsstream.cafepos.core

import com.devsstream.cafepos.core.order.ItemSelection
import com.devsstream.cafepos.core.pricing.OrderLine

// X, Y, Z and the TC-06 order from section 8 of the brief.
val latteX = ItemSelection("latte", "large", mapOf("oat" to 1, "extra_shot" to 1, "vanilla" to 1))
val americanoY = ItemSelection("americano", "regular", mapOf("extra_shot" to 2, "caramel" to 1))
val icedTeaZ = ItemSelection("iced_tea", "large", mapOf("vanilla" to 1))
val croissant = ItemSelection("croissant")
val brownie = ItemSelection("brownie")

val tc06Lines = listOf(OrderLine(latteX, 2), OrderLine(croissant, 2), OrderLine(brownie, 2))
