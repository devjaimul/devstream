package com.devsstream.cafepos.data.sync

import com.devsstream.cafepos.data.local.OrderWithDetails
import com.devsstream.cafepos.data.local.PaymentStatus
import com.devsstream.cafepos.data.remote.AmountDto
import com.devsstream.cafepos.data.remote.OptionQuantityDto
import com.devsstream.cafepos.data.remote.OrderDto
import com.devsstream.cafepos.data.remote.OrderLineDto
import com.devsstream.cafepos.data.remote.PaymentDto
import java.time.Instant

// Exactly what was saved at sale time, nothing is priced again here (D5).
fun OrderWithDetails.toDto(): OrderDto = OrderDto(
    clientOrderId = order.clientOrderId,
    receiptNo = order.receiptNo,
    createdAt = Instant.ofEpochMilli(order.createdAt).toString(),
    businessDay = order.businessDay,
    timeConfirmed = order.timeConfirmed,
    catalogVersion = order.catalogVersion,
    coupon = order.coupon,
    lines = lines.sortedBy { it.id }.map { line ->
        OrderLineDto(
            productId = line.productId,
            sizeId = line.sizeId,
            options = line.options.map { (id, quantity) -> OptionQuantityDto(id, quantity) },
            label = line.label,
            category = line.category,
            quantity = line.quantity,
            unitPricePoisha = line.unitPrice,
            totalPoisha = line.total,
        )
    },
    promotions = order.promotions.map { (id, amount) -> AmountDto(id, amount) },
    subtotalPoisha = order.subtotal,
    discount = order.discountId?.let { AmountDto(it, order.discountAmount) },
    vatPoisha = order.vat,
    totalPoisha = order.total,
    payablePoisha = order.payable,
    // declined and voided card attempts stay on the till
    payments = payments.filter { it.status == PaymentStatus.APPROVED }.sortedBy { it.id }.map { payment ->
        PaymentDto(
            type = payment.type,
            amountPoisha = payment.amount,
            tenderedPoisha = payment.tendered,
            changePoisha = payment.change,
            cardRef = payment.cardRef,
            authCode = payment.authCode,
        )
    },
)
