package com.devsstream.cafepos.core

import com.devsstream.cafepos.core.catalog.Catalog
import com.devsstream.cafepos.core.catalog.ModifierGroup
import com.devsstream.cafepos.core.catalog.ModifierOption
import com.devsstream.cafepos.core.catalog.OptionRule
import com.devsstream.cafepos.core.catalog.OrderDiscount
import com.devsstream.cafepos.core.catalog.Product
import com.devsstream.cafepos.core.catalog.Promotion
import com.devsstream.cafepos.core.catalog.Size
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

fun bdt(amount: Int): Long = amount * 100L

private fun regularLarge(largeExtra: Int) = listOf(
    Size("regular", "Regular", 0),
    Size("large", "Large", bdt(largeExtra)),
)

val dhaka: ZoneId = ZoneId.of("Asia/Dhaka")

fun dhakaTime(year: Int, month: Int, day: Int, hour: Int, minute: Int, second: Int = 0): Instant =
    ZonedDateTime.of(year, month, day, hour, minute, second, 0, dhaka).toInstant()

// Catalog from Appendix A of the brief. Tests are pinned to these numbers.
val appendixA = Catalog(
    version = 1,
    timeZone = dhaka,
    vatBps = 500,
    products = listOf(
        Product("americano", "Americano", "BEVERAGE", bdt(180), regularLarge(40), listOf("milk", "shots", "syrup")),
        Product("latte", "Latte", "BEVERAGE", bdt(220), regularLarge(50), listOf("milk", "shots", "syrup")),
        Product("iced_tea", "Iced Tea", "BEVERAGE", bdt(120), regularLarge(30), listOf("syrup")),
        Product("chicken_sandwich", "Chicken Sandwich", "FOOD", bdt(320)),
        Product("veg_wrap", "Veg Wrap", "FOOD", bdt(260)),
        Product("croissant", "Croissant", "BAKERY", bdt(150)),
        Product("brownie", "Brownie", "BAKERY", bdt(120)),
    ),
    groups = listOf(
        ModifierGroup(
            id = "milk",
            name = "Milk",
            options = listOf(ModifierOption("oat", "Oat", bdt(60)), ModifierOption("almond", "Almond", bdt(70))),
            max = 1,
        ),
        ModifierGroup(
            id = "shots",
            name = "Shots",
            options = listOf(ModifierOption("extra_shot", "Extra shot", bdt(50))),
            max = 2,
        ),
        ModifierGroup(
            id = "syrup",
            name = "Syrup",
            options = listOf(
                ModifierOption("vanilla", "Vanilla", bdt(30)),
                ModifierOption("caramel", "Caramel", bdt(30)),
                ModifierOption("hazelnut", "Hazelnut", bdt(35)),
            ),
            maxDistinct = 2,
            maxPerOption = 1,
        ),
    ),
    rules = listOf(
        OptionRule.Requires("R1", "hazelnut", anyOf = listOf("oat", "almond")),
        OptionRule.Excludes("R2", "almond", others = listOf("caramel")),
    ),
    promotions = listOf(
        Promotion.CheapestFree("P1", "BAKERY", every = 3),
        Promotion.HappyHour("P2", "BEVERAGE", percentBps = 1000, from = LocalTime.of(15, 0), until = LocalTime.of(17, 0)),
    ),
    orderDiscounts = listOf(
        OrderDiscount.Threshold("T1", minSubtotal = bdt(1000), amountOff = bdt(80)),
        OrderDiscount.Coupon(
            id = "C1",
            code = "DEVS10",
            percentBps = 1000,
            cap = bdt(120),
            validUntil = ZonedDateTime.of(2026, 12, 1, 0, 0, 0, 0, dhaka).toInstant(),
        ),
    ),
)
