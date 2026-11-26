package com.devsstream.cafepos.core.money

import java.util.Locale

// bps: 1000 = 10%. Rounds half-up to the nearest poisha.
fun percentOf(amount: Long, bps: Int): Long {
    require(amount >= 0) { "negative amount $amount" }
    return (amount * bps + 5_000) / 10_000
}

// 109431 (1,094.31) -> 109400 (1,094). Result is still in poisha.
fun roundToWholeBdt(poisha: Long): Long {
    require(poisha >= 0) { "negative amount $poisha" }
    return (poisha + 50) / 100 * 100
}

fun formatBdt(poisha: Long): String =
    String.format(Locale.US, "%,d.%02d", poisha / 100, poisha % 100)
