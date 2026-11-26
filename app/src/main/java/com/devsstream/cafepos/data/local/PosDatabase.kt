package com.devsstream.cafepos.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(
    entities = [OrderEntity::class, OrderLineEntity::class, PaymentEntity::class],
    version = 1,
)
@TypeConverters(Converters::class)
abstract class PosDatabase : RoomDatabase() {
    abstract fun orderDao(): OrderDao
}
