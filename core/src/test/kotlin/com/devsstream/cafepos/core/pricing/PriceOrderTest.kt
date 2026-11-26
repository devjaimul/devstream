package com.devsstream.cafepos.core.pricing

import com.devsstream.cafepos.core.appendixA
import com.devsstream.cafepos.core.bdt
import com.devsstream.cafepos.core.catalog.OrderDiscount
import com.devsstream.cafepos.core.dhakaTime
import com.devsstream.cafepos.core.order.ItemSelection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PriceOrderTest {

    private val latteX = ItemSelection("latte", "large", mapOf("oat" to 1, "extra_shot" to 1, "vanilla" to 1))
    private val americanoY = ItemSelection("americano", "regular", mapOf("extra_shot" to 2, "caramel" to 1))
    private val icedTeaZ = ItemSelection("iced_tea", "large", mapOf("vanilla" to 1))
    private val croissant = ItemSelection("croissant")
    private val brownie = ItemSelection("brownie")

    private val tc06Lines = listOf(OrderLine(latteX, 2), OrderLine(croissant, 2), OrderLine(brownie, 2))
    private val nov20at1530 = dhakaTime(2026, 11, 20, 15, 30)

    private fun unitPrice(selection: ItemSelection) =
        priceOrder(listOf(OrderLine(selection, 1)), appendixA, null, nov20at1530).lines.single().unitPrice

    @Test
    fun `TC-05 unit prices of X Y Z`() {
        assertEquals(bdt(410), unitPrice(latteX))
        assertEquals(bdt(310), unitPrice(americanoY))
        assertEquals(bdt(180), unitPrice(icedTeaZ))
    }

    @Test
    fun `TC-06 happy hour with DEVS10 pays 1094`() {
        val p = priceOrder(tc06Lines, appendixA, "DEVS10", nov20at1530)

        assertEquals(bdt(120), p.promotion("P1"))
        assertEquals(bdt(82), p.promotion("P2"))
        assertEquals(bdt(1158), p.subtotal)
        assertEquals(Adjustment("C1", 11580), p.discount)
        assertEquals(5211L, p.vat)
        assertEquals(109431L, p.total)
        assertEquals(bdt(1094), p.payable)
        assertNull(p.couponRejection)
    }

    @Test
    fun `TC-07 at 17_00_00 no happy hour and C1 capped at 120`() {
        val p = priceOrder(tc06Lines, appendixA, "DEVS10", dhakaTime(2026, 11, 20, 17, 0, 0))

        assertEquals(bdt(120), p.promotion("P1"))
        assertEquals(0L, p.promotion("P2"))
        assertEquals(bdt(1240), p.subtotal)
        assertEquals(Adjustment("C1", bdt(120)), p.discount)
        assertEquals(bdt(56), p.vat)
        assertEquals(bdt(1176), p.payable)
    }

    @Test
    fun `TC-08 on 1 Dec DEVS10 is expired and T1 applies`() {
        val p = priceOrder(tc06Lines, appendixA, "DEVS10", dhakaTime(2026, 12, 1, 15, 30))

        assertEquals(CouponRejection.EXPIRED, p.couponRejection)
        assertEquals(Adjustment("T1", bdt(80)), p.discount)
        assertEquals(5390L, p.vat)
        assertEquals(bdt(1132), p.payable)
    }

    @Test
    fun `Q6 one more brownie keeps P1 at 120 and pays 1216`() {
        val lines = listOf(OrderLine(latteX, 2), OrderLine(croissant, 2), OrderLine(brownie, 3))
        val p = priceOrder(lines, appendixA, "DEVS10", nov20at1530)

        // 5 bakery units -> floor(5 / 3) = 1 free unit, still the 120 brownie
        assertEquals(bdt(120), p.promotion("P1"))
        assertEquals(bdt(1278), p.subtotal)
        assertEquals(Adjustment("C1", bdt(120)), p.discount)
        assertEquals(5790L, p.vat)
        assertEquals(bdt(1216), p.payable)
    }

    @Test
    fun `O3 VAT of 47_025 rounds up to 47_03`() {
        val lines = listOf(
            OrderLine(ItemSelection("iced_tea", "large"), 3),
            OrderLine(ItemSelection("chicken_sandwich"), 2),
        )
        val p = priceOrder(lines, appendixA, "DEVS10", dhakaTime(2026, 11, 20, 16, 59))

        assertEquals(bdt(45), p.promotion("P2"))
        assertEquals(bdt(1045), p.subtotal)
        assertEquals(Adjustment("C1", 10450), p.discount)
        assertEquals(94050L, p.discountedSubtotal)
        assertEquals(4703L, p.vat)
        assertEquals(bdt(988), p.payable)
    }

    @Test
    fun `happy hour starts at 15_00_00 and ends before 17_00_00`() {
        fun p2At(h: Int, m: Int, s: Int) =
            priceOrder(tc06Lines, appendixA, null, dhakaTime(2026, 11, 20, h, m, s)).promotion("P2")

        assertEquals(0L, p2At(14, 59, 59))
        assertEquals(bdt(82), p2At(15, 0, 0))
        assertEquals(bdt(82), p2At(16, 59, 59))
        assertEquals(0L, p2At(17, 0, 0))
    }

    @Test
    fun `happy hour only touches beverages`() {
        val lines = listOf(OrderLine(croissant, 1), OrderLine(ItemSelection("veg_wrap"), 1))
        assertEquals(0L, priceOrder(lines, appendixA, null, nov20at1530).promotion("P2"))
    }

    @Test
    fun `DEVS10 works until the last second of 30 Nov`() {
        val before = priceOrder(tc06Lines, appendixA, "DEVS10", dhakaTime(2026, 11, 30, 23, 59, 59))
        val at = priceOrder(tc06Lines, appendixA, "DEVS10", dhakaTime(2026, 12, 1, 0, 0, 0))

        assertEquals("C1", before.discount?.id)
        assertEquals(CouponRejection.EXPIRED, at.couponRejection)
    }

    @Test
    fun `coupon code ignores case and spaces`() {
        val p = priceOrder(tc06Lines, appendixA, "  devs10 ", nov20at1530)
        assertEquals("C1", p.discount?.id)
    }

    @Test
    fun `unknown coupon is rejected and T1 still applies`() {
        val p = priceOrder(tc06Lines, appendixA, "FREE50", nov20at1530)
        assertEquals(CouponRejection.UNKNOWN, p.couponRejection)
        assertEquals(Adjustment("T1", bdt(80)), p.discount)
    }

    @Test
    fun `no discount under 1000 without a coupon`() {
        val p = priceOrder(listOf(OrderLine(latteX, 1)), appendixA, null, dhakaTime(2026, 11, 20, 10, 0))
        assertNull(p.discount)
        assertEquals(bdt(410), p.subtotal)
        assertEquals(2050L, p.vat)
        assertEquals(bdt(431), p.payable) // 430.50 rounds half-up
    }

    @Test
    fun `P1 frees the cheapest bakery unit across lines`() {
        val lines = listOf(OrderLine(croissant, 2), OrderLine(brownie, 1))
        assertEquals(bdt(120), priceOrder(lines, appendixA, null, nov20at1530).promotion("P1"))

        val sixCroissants = listOf(OrderLine(croissant, 6))
        assertEquals(bdt(300), priceOrder(sixCroissants, appendixA, null, nov20at1530).promotion("P1"))
    }

    @Test
    fun `on a tie T1 wins over C1`() {
        // T1 threshold lowered so that 10% of the subtotal equals T1's 80 exactly
        val catalog = appendixA.copy(
            orderDiscounts = appendixA.orderDiscounts.map {
                if (it is OrderDiscount.Threshold) it.copy(minSubtotal = bdt(500)) else it
            },
        )
        // 320 + 2 x 150 + 180 = 800, and 10% of 800 is 80
        val lines = listOf(
            OrderLine(ItemSelection("chicken_sandwich"), 1),
            OrderLine(croissant, 2),
            OrderLine(ItemSelection("americano", "regular"), 1),
        )
        val p = priceOrder(lines, catalog, "DEVS10", dhakaTime(2026, 11, 20, 10, 0))

        assertEquals(bdt(800), p.subtotal)
        assertEquals(Adjustment("T1", bdt(80)), p.discount)
    }

    @Test
    fun `catalog v2 with latte at 240 needs no code change`() {
        val v2 = appendixA.copy(
            version = 2,
            products = appendixA.products.map { if (it.id == "latte") it.copy(basePrice = bdt(240)) else it },
        )
        val p = priceOrder(listOf(OrderLine(latteX, 1)), v2, null, dhakaTime(2026, 11, 20, 10, 0))
        assertEquals(bdt(430), p.lines.single().unitPrice)
    }

    @Test
    fun `line label lists size and options`() {
        val p = priceOrder(listOf(OrderLine(americanoY, 1)), appendixA, null, nov20at1530)
        assertEquals("Americano Regular, Extra shot x2, Caramel", p.lines.single().label)
    }
}
