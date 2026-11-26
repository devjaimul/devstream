package com.devsstream.cafepos.core.validation

import com.devsstream.cafepos.core.appendixA
import com.devsstream.cafepos.core.order.ItemSelection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ValidateTest {

    private fun check(productId: String, size: String? = null, vararg options: Pair<String, Int>): List<Violation> {
        val selection = ItemSelection(productId, size, options.toMap())
        return validate(selection, appendixA.product(productId)!!, appendixA)
    }

    private fun List<Violation>.ids() = map { it.id }

    @Test
    fun `TC-01 latte with no size is blocked`() {
        val v = check("latte")
        assertEquals(listOf("size"), v.ids())
        assertEquals("A size is required for Latte", v.single().message)
    }

    @Test
    fun `TC-02 americano with 3 extra shots is blocked by shots max 2`() {
        val v = check("americano", "regular", "extra_shot" to 3)
        assertEquals(listOf("shots"), v.ids())
        assertEquals("Shots: at most 2, got 3", v.single().message)
    }

    @Test
    fun `TC-03 hazelnut with no milk breaks R1`() {
        val v = check("latte", "regular", "hazelnut" to 1)
        assertEquals(listOf("R1"), v.ids())
        assertEquals("Hazelnut requires Oat or Almond", v.single().message)
    }

    @Test
    fun `TC-04 lists both syrup maxDistinct and R2`() {
        val v = check("latte", "large", "almond" to 1, "caramel" to 1, "hazelnut" to 1, "vanilla" to 1)
        assertEquals(listOf("syrup", "R2"), v.ids())
        assertEquals("Syrup: at most 2 different, got 3", v[0].message)
        assertEquals("Almond can't be combined with Caramel", v[1].message)
    }

    @Test
    fun `X Y and Z from section 8 are valid`() {
        assertTrue(check("latte", "large", "oat" to 1, "extra_shot" to 1, "vanilla" to 1).isEmpty())
        assertTrue(check("americano", "regular", "extra_shot" to 2, "caramel" to 1).isEmpty())
        assertTrue(check("iced_tea", "large", "vanilla" to 1).isEmpty())
    }

    @Test
    fun `iced tea does not take milk`() {
        val v = check("iced_tea", "regular", "oat" to 1)
        assertEquals(listOf("milk"), v.ids())
        assertEquals("Iced Tea doesn't take Milk", v.single().message)
    }

    @Test
    fun `milk is max 1 in total`() {
        assertEquals(listOf("milk"), check("latte", "regular", "oat" to 1, "almond" to 1).ids())
        assertEquals(listOf("milk"), check("latte", "regular", "oat" to 2).ids())
    }

    @Test
    fun `syrup allows only one of each`() {
        val v = check("latte", "regular", "vanilla" to 2)
        assertEquals(listOf("syrup"), v.ids())
        assertEquals("Syrup: at most 1 of Vanilla, got 2", v.single().message)
    }

    @Test
    fun `hazelnut with almond is fine`() {
        assertTrue(check("latte", "regular", "almond" to 1, "hazelnut" to 1).isEmpty())
    }

    @Test
    fun `quantity 0 counts as not picked`() {
        assertTrue(check("americano", "regular", "extra_shot" to 0, "hazelnut" to 0).isEmpty())
    }

    @Test
    fun `size on a product without sizes is blocked`() {
        assertEquals(listOf("size"), check("croissant", "large").ids())
    }

    @Test
    fun `unknown size is blocked`() {
        assertEquals(listOf("size"), check("latte", "venti").ids())
    }

    @Test
    fun `all problems come back together, not just the first`() {
        val v = check("latte", null, "extra_shot" to 3, "hazelnut" to 1)
        assertEquals(listOf("size", "shots", "R1"), v.ids())
    }
}
