package com.devsstream.cafepos.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface OrderDao {

    @Insert
    suspend fun insert(order: OrderEntity): Long

    @Insert
    suspend fun insertLines(lines: List<OrderLineEntity>)

    @Insert
    suspend fun insert(payment: PaymentEntity): Long

    @Update
    suspend fun update(order: OrderEntity)

    @Update
    suspend fun update(payment: PaymentEntity)

    @Query("SELECT MAX(receiptSeq) FROM orders WHERE businessDay = :day")
    suspend fun lastReceiptSeq(day: String): Int?

    @Query("SELECT * FROM orders WHERE id = :id")
    suspend fun order(id: Long): OrderEntity?

    @Query("SELECT * FROM payments WHERE id = :id")
    suspend fun payment(id: Long): PaymentEntity?

    @Query("SELECT * FROM payments WHERE cardRef = :ref")
    suspend fun paymentByRef(ref: String): PaymentEntity?

    @Query("SELECT * FROM payments WHERE orderId = :orderId ORDER BY id")
    suspend fun payments(orderId: Long): List<PaymentEntity>

    @Transaction
    @Query("SELECT * FROM orders WHERE id = :id")
    suspend fun details(id: Long): OrderWithDetails?

    @Transaction
    @Query("SELECT * FROM orders WHERE status = :status ORDER BY id")
    suspend fun withStatus(status: OrderStatus): List<OrderWithDetails>

    @Transaction
    @Query("SELECT * FROM orders WHERE status = :status ORDER BY id LIMIT 1")
    suspend fun firstWithStatus(status: OrderStatus): OrderWithDetails?

    @Transaction
    @Query("SELECT * FROM orders WHERE id = :id")
    fun observe(id: Long): Flow<OrderWithDetails?>

    @Transaction
    @Query("SELECT * FROM orders ORDER BY id DESC")
    fun observeAll(): Flow<List<OrderWithDetails>>
}
