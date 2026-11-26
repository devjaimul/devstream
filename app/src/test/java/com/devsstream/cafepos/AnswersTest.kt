package com.devsstream.cafepos

import com.devsstream.cafepos.core.appendixA
import com.devsstream.cafepos.core.brownie
import com.devsstream.cafepos.core.dhakaTime
import com.devsstream.cafepos.core.money.formatBdt
import com.devsstream.cafepos.core.pricing.OrderLine
import com.devsstream.cafepos.core.pricing.PriceBreakdown
import com.devsstream.cafepos.core.pricing.priceOrder
import com.devsstream.cafepos.core.tc06Lines
import com.devsstream.cafepos.data.CatalogRepository
import com.devsstream.cafepos.data.OrderRepository
import com.devsstream.cafepos.data.inMemoryDb
import com.devsstream.cafepos.data.remote.fake.Failures
import com.devsstream.cafepos.data.remote.fake.FakePosServer
import com.devsstream.cafepos.data.sync.SyncScheduler
import com.devsstream.cafepos.payment.CardResult
import com.devsstream.cafepos.payment.CardTerminal
import com.devsstream.cafepos.summary.SeedLoader
import com.devsstream.cafepos.summary.readAppendixB
import com.devsstream.cafepos.summary.summarize
import com.devsstream.cafepos.summary.toPaidOrder
import com.devsstream.terminal.LegacyCardTerminal
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File
import java.time.LocalDate

// Prints every number in ANSWERS.md from the app's own code, and fails if the file
// says something else. To see the output:
//   ./gradlew :app:testDebugUnitTest --tests "*AnswersTest" -Panswers --rerun
@RunWith(RobolectricTestRunner::class)
class AnswersTest {

    @Test
    fun `ANSWERS md matches what the code says`() {
        val answers = File("../ANSWERS.md").readText().replace("\r\n", "\n")
        for (block in listOf(q1toQ3(), q4(), q5(), q6())) {
            println(block + "\n")
            assertTrue("ANSWERS.md is out of date, expected this block:\n$block", block in answers)
        }
    }

    // Q1 to Q3 go through the real path: seed -> Room -> day summary
    private fun q1toQ3(): String = runBlocking {
        val db = inMemoryDb()
        val repo = OrderRepository(db)
        val server = FakePosServer(
            MemoryServerStore(),
            elapsedMs = { 0L },
            wallMs = { dhakaTime(2026, 11, 20, 20, 0).toEpochMilli() },
            latencyMs = 0L..0L,
        ).apply { failures = Failures.OFF }
        val catalogs = CatalogRepository(server, MemoryCatalogCache()).apply { refresh() }
        SeedLoader(repo, catalogs, object : SyncScheduler {
            override fun requestSync() = Unit
        }).loadAppendixB()

        val day = LocalDate.of(2026, 11, 20)
        val orders = repo.observeDay(day).first()
        val summary = summarize(day, orders)
        db.close()

        buildString {
            appendLine("Q1")
            orders.sortedBy { it.order.clientOrderId }.forEach {
                appendLine("${it.order.clientOrderId.removePrefix("appendix-b-")}  ${bdt(it.order.payable)}")
            }
            appendLine("Day total  ${bdt(summary.payable)}")
            appendLine()
            appendLine("Q2")
            appendLine("Cash  ${bdt(summary.cash)}")
            appendLine("Card  ${bdt(summary.card)}")
            appendLine()
            appendLine("Q3")
            append("${summary.topItems.joinToString { appendixA.product(it)!!.name }}: ${summary.topUnits} units")
        }
    }

    private fun q4(): String {
        val o3 = readAppendixB().orders.single { it.ref == "O3" }.toPaidOrder(appendixA)
        val p = o3.price
        // the same order without the coupon shows what T1 alone would have taken off
        val withoutCoupon = priceOrder(o3.lines, appendixA, null, o3.at.time)
        // VAT before rounding, in ten-thousandths of a poisha
        val rawVat = p.discountedSubtotal * appendixA.vatBps

        return buildString {
            appendLine("Q4 (O3, 16:59, DEVS10)")
            p.lines.forEach { appendLine("${it.quantity} x ${it.label} @ ${bdt(it.unitPrice)} = ${bdt(it.total)}") }
            appendLine("Promotions: ${p.promotions.joinToString { "${it.id} -${bdt(it.amount)}" }}")
            appendLine("Subtotal  ${bdt(p.subtotal)}")
            appendLine("Without coupon: ${discountText(withoutCoupon)}")
            appendLine("With coupon:    ${discountText(p)}")
            appendLine("After discount  ${bdt(p.discountedSubtotal)}")
            appendLine("VAT exact  ${rawVat / 10_000}.${rawVat % 10_000 / 1_000} poisha -> half-up ${bdt(p.vat)}")
            appendLine("Total  ${bdt(p.total)}")
            appendLine("Payable (half-up to whole BDT)  ${bdt(p.payable)}")
            append("Payments: ${o3.payments.joinToString { "${it.type} ${bdt(it.amount)}" }}")
        }
    }

    // real SDK and wrapper, on a virtual clock so the 15 s take no real time
    @OptIn(ExperimentalCoroutinesApi::class)
    private fun q5(): String {
        val sdk = LegacyCardTerminal()
        val lines = mutableListOf("Q5 (card 1,077.00)")
        runTest {
            var timeoutAt = -1L
            val terminal = CardTerminal(sdk, log = { timeoutAt = testScheduler.currentTime })
            val result = terminal.charge(107_700, "q5")

            lines += "No callback. Asked the terminal at ${timeoutAt / 1_000} s"
            lines += "queryStatus: ${if (result is CardResult.Approved) "APPROVED" else result.toString()}"
        }
        lines += "Charges at the terminal for this ref: ${sdk.attempts("q5")}"
        return lines.joinToString("\n")
    }

    private fun q6(): String {
        val time = dhakaTime(2026, 11, 20, 15, 30)
        val before = priceOrder(tc06Lines, appendixA, "DEVS10", time)
        val after = priceOrder(tc06Lines + OrderLine(brownie, 1), appendixA, "DEVS10", time)

        fun bakeryUnits(p: PriceBreakdown) = p.lines.filter { it.category == "BAKERY" }.sumOf { it.quantity }
        return buildString {
            appendLine("Q6 (TC-06 plus one Brownie)")
            appendLine("Bakery units ${bakeryUnits(before)} -> ${bakeryUnits(after)}")
            appendLine("P1  ${bdt(before.promotion("P1"))} -> ${bdt(after.promotion("P1"))}")
            appendLine("Subtotal  ${bdt(after.subtotal)}")
            appendLine("Discount: ${discountText(after)}")
            appendLine("VAT  ${bdt(after.vat)}")
            appendLine("Total  ${bdt(after.total)}")
            append("Payable  ${bdt(before.payable)} -> ${bdt(after.payable)}")
        }
    }

    private fun discountText(p: PriceBreakdown) = p.discount?.let { "${it.id} -${bdt(it.amount)}" } ?: "none"

    private fun bdt(poisha: Long) = formatBdt(poisha)
}
