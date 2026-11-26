package com.devsstream.cafepos.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.devsstream.cafepos.core.appendixA
import com.devsstream.cafepos.core.bdt
import com.devsstream.cafepos.core.dhakaTime
import com.devsstream.cafepos.core.pricing.priceOrder
import com.devsstream.cafepos.core.tc06Lines
import com.devsstream.cafepos.data.local.OrderWithDetails
import com.devsstream.cafepos.data.local.PosDatabase
import com.devsstream.cafepos.time.ServerNow

fun inMemoryDb(): PosDatabase =
    Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), PosDatabase::class.java)
        .allowMainThreadQueries()
        .build()

val at1530 = ServerNow(dhakaTime(2026, 11, 20, 15, 30), confirmed = true)

// the TC-06 order (payable 1,094), paid in cash unless told otherwise
suspend fun OrderRepository.tc06Sale(
    now: ServerNow = at1530,
    payments: List<NewPayment> = listOf(NewPayment.Cash(bdt(1094), bdt(1100))),
): OrderWithDetails =
    createOrder(tc06Lines, priceOrder(tc06Lines, appendixA, "DEVS10", now.time), "DEVS10", appendixA, now, payments)
