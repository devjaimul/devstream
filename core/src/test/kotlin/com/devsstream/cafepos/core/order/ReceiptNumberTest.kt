package com.devsstream.cafepos.core.order

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class ReceiptNumberTest {

    private val day = LocalDate.of(2026, 11, 20)

    @Test
    fun `first receipt of the day`() {
        assertEquals("T1-20261120-0001", receiptNumber("T1", day, 1))
    }

    @Test
    fun `sequence is padded to 4 digits`() {
        assertEquals("T1-20261120-0042", receiptNumber("T1", day, 42))
    }

    @Test
    fun `past 9999 it just gets longer`() {
        assertEquals("T1-20261120-10000", receiptNumber("T1", day, 10_000))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `zero is not a sequence`() {
        receiptNumber("T1", day, 0)
    }
}
