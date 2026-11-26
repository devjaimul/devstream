package com.devsstream.cafepos.core.money

import org.junit.Assert.assertEquals
import org.junit.Test

class MoneyTest {

    @Test
    fun `percent rounds half up to the poisha`() {
        assertEquals(4703L, percentOf(94050, 500)) // 4702.5
        assertEquals(5211L, percentOf(104220, 500)) // 5211.0
        assertEquals(104L, percentOf(2070, 500)) // 103.5, the case a Double gets wrong
        assertEquals(103L, percentOf(2069, 500)) // 103.45
    }

    @Test
    fun `payable rounds half up to whole taka`() {
        assertEquals(109400L, roundToWholeBdt(109431))
        assertEquals(109500L, roundToWholeBdt(109450))
        assertEquals(109400L, roundToWholeBdt(109449))
        assertEquals(98800L, roundToWholeBdt(98753))
    }

    @Test
    fun `format with thousands and two decimals`() {
        assertEquals("1,094.31", formatBdt(109431))
        assertEquals("0.05", formatBdt(5))
        assertEquals("47.03", formatBdt(4703))
    }
}
