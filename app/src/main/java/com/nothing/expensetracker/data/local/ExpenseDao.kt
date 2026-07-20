package com.nothing.expensetracker.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

data class CategoryExpense(
    val category: String,
    val totalAmount: Double
)

@Dao
interface ExpenseDao {
    @Query("SELECT * FROM expenses ORDER BY timestamp DESC")
    fun getAllExpenses(): Flow<List<Expense>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertExpense(expense: Expense): Long

    @Query("SELECT * FROM expenses WHERE isSynced = 0")
    suspend fun getUnsyncedExpenses(): List<Expense>

    @Query("UPDATE expenses SET isSynced = 1 WHERE id = :id")
    suspend fun markAsSynced(id: Long)

    @Query("SELECT category, SUM(amount) as totalAmount FROM expenses WHERE type = 'Debit' GROUP BY category ORDER BY totalAmount DESC")
    fun getExpensesByCategory(): Flow<List<CategoryExpense>>

    @Query("""
        SELECT category, SUM(amount) as totalAmount 
        FROM expenses 
        WHERE type = 'Debit' 
          AND strftime('%m', datetime(timestamp / 1000, 'unixepoch')) = :month
          AND strftime('%Y', datetime(timestamp / 1000, 'unixepoch')) = :year
        GROUP BY category 
        ORDER BY totalAmount DESC
    """)
    fun getExpensesByCategoryFiltered(month: String, year: String): Flow<List<CategoryExpense>>
}
