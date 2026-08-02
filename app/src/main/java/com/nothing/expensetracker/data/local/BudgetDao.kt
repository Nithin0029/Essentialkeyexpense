package com.nothing.expensetracker.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface BudgetDao {
    @Query("SELECT * FROM budgets WHERE month = :month AND year = :year AND categoryName IS NULL AND syncStatus != 'Deleted' ORDER BY id DESC LIMIT 1")
    fun getOverallBudget(month: Int, year: Int): Flow<Budget?>

    @Query("SELECT * FROM budgets WHERE month = :month AND year = :year AND categoryName IS NOT NULL AND syncStatus != 'Deleted' GROUP BY categoryName HAVING id = MAX(id)")
    fun getCategoryBudgets(month: Int, year: Int): Flow<List<Budget>>

    @Query("SELECT * FROM budgets WHERE month = :month AND year = :year AND categoryName IS :categoryName AND syncStatus != 'Deleted' LIMIT 1")
    suspend fun findExistingBudget(categoryName: String?, month: Int, year: Int): Budget?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBudget(budget: Budget)

    @Update
    suspend fun updateBudget(budget: Budget)

    @Delete
    suspend fun deleteBudget(budget: Budget)
    
    @Query("SELECT * FROM budgets")
    suspend fun getAllBudgets(): List<Budget>

    @Query("SELECT * FROM budgets WHERE syncStatus != 'Synced'")
    suspend fun getUnsyncedBudgets(): List<Budget>

    @Query("UPDATE budgets SET syncStatus = :status, lastSyncAttempt = :attempt, syncError = :error WHERE id = :id")
    suspend fun updateSyncStatus(id: Long, status: String, attempt: Long, error: String?)

    @Query("DELETE FROM budgets WHERE syncStatus = 'Deleted'")
    suspend fun purgeDeletedBudgets()

    @Query("SELECT COUNT(*) FROM budgets WHERE syncStatus = 'Pending' OR syncStatus = 'Failed' OR syncStatus = 'Deleted'")
    fun getUnsyncedCount(): Flow<Int>
}
