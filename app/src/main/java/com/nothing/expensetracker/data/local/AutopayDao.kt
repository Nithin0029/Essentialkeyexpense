package com.nothing.expensetracker.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface AutopayDao {
    @Query("SELECT * FROM autopay_rules ORDER BY dayOfMonth ASC")
    fun getAllAutopayRules(): Flow<List<AutopayRule>>

    @Query("SELECT * FROM autopay_rules WHERE id = :id")
    suspend fun getAutopayRuleById(id: Long): AutopayRule?

    @Query("SELECT * FROM autopay_rules WHERE isActive = 1")
    suspend fun getActiveAutopayRules(): List<AutopayRule>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAutopayRule(rule: AutopayRule): Long

    @Update
    suspend fun updateAutopayRule(rule: AutopayRule)

    @Delete
    suspend fun deleteAutopayRule(rule: AutopayRule)

    @Query("UPDATE autopay_rules SET lastRunMonth = :monthKey WHERE id = :id")
    suspend fun markRun(id: Long, monthKey: String)
}
