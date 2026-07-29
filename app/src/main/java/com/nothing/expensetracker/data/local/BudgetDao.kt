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
    @Query("SELECT * FROM budgets WHERE month = :month AND year = :year AND categoryName IS NULL LIMIT 1")
    fun getOverallBudget(month: Int, year: Int): Flow<Budget?>

    @Query("SELECT * FROM budgets WHERE month = :month AND year = :year AND categoryName IS NOT NULL")
    fun getCategoryBudgets(month: Int, year: Int): Flow<List<Budget>>

    @Query("SELECT * FROM budgets WHERE month = :month AND year = :year AND categoryName = :categoryName LIMIT 1")
    suspend fun getBudgetByCategory(categoryName: String, month: Int, year: Int): Budget?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBudget(budget: Budget)

    @Update
    suspend fun updateBudget(budget: Budget)

    @Delete
    suspend fun deleteBudget(budget: Budget)
    
    @Query("SELECT * FROM budgets")
    suspend fun getAllBudgets(): List<Budget>
}
