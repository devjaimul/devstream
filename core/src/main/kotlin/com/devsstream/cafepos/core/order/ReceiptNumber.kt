package com.devsstream.cafepos.core.order

import java.time.LocalDate
import java.time.format.DateTimeFormatter

// T1-20261120-0001: till id, server business day, sequence within that day
fun receiptNumber(tillId: String, day: LocalDate, seq: Int): String {
    require(seq > 0) { "sequence starts at 1, got $seq" }
    return "$tillId-${day.format(DateTimeFormatter.BASIC_ISO_DATE)}-${seq.toString().padStart(4, '0')}"
}
