package com.nothing.expensetracker.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface PaymentMethodDao {
    @Query("SELECT * FROM payment_methods ORDER BY name ASC")
    fun getAllPaymentMethods(): Flow<List<PaymentMethod>>

    @Query("SELECT * FROM payment_methods WHERE LOWER(name) = LOWER(:name) LIMIT 1")
    suspend fun getByNameCaseInsensitive(name: String): PaymentMethod?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertPaymentMethod(method: PaymentMethod): Long

    @Update
    suspend fun updatePaymentMethod(method: PaymentMethod)

    @Delete
    suspend fun deletePaymentMethod(method: PaymentMethod)

    @Query("SELECT COUNT(*) FROM payment_methods")
    suspend fun countPaymentMethods(): Int
}
