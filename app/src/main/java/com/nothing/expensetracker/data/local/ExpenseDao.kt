package com.nothing.expensetracker.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

data class CategoryExpense(
    val category: String,
    val totalAmount: Double
)

data class FriendBalance(
    val friendName: String,
    val totalDebit: Double,
    val totalCredit: Double,
    val outstandingBalance: Double
)

@Dao
interface ExpenseDao {
    @Query("SELECT * FROM expenses ORDER BY timestamp DESC")
    fun getAllExpenses(): Flow<List<Expense>>

    @Query("""
        SELECT * FROM expenses 
        WHERE category LIKE '%' || :query || '%' 
        OR notes LIKE '%' || :query || '%' 
        OR friendId LIKE '%' || :query || '%' 
        OR paymentMethod LIKE '%' || :query || '%'
        ORDER BY timestamp DESC
    """)
    fun searchExpenses(query: String): Flow<List<Expense>>

    @Query("SELECT * FROM expenses WHERE id = :id")
    fun getExpenseById(id: Long): Flow<Expense?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertExpense(expense: Expense): Long

    @androidx.room.Update
    suspend fun updateExpense(expense: Expense)

    @Delete
    suspend fun deleteExpense(expense: Expense)

    @Query("DELETE FROM expenses WHERE id = :expenseId")
    suspend fun deleteExpenseById(expenseId: Long)

    @Query("SELECT * FROM expenses WHERE isSynced = 0")
    suspend fun getUnsyncedExpenses(): List<Expense>

    @Query("UPDATE expenses SET isSynced = 1 WHERE id = :id")
    suspend fun markAsSynced(id: Long)

    @Query("SELECT DISTINCT category FROM expenses ORDER BY category ASC")
    fun getAllCategories(): Flow<List<String>>

    @Query("SELECT DISTINCT friendId FROM expenses WHERE friendId IS NOT NULL AND friendId != '' ORDER BY friendId ASC")
    fun getAllFriends(): Flow<List<String>>

    @Query("""
        SELECT 
            friendId as friendName,
            SUM(CASE WHEN type = 'Debit' THEN amount ELSE 0 END) as totalDebit,
            SUM(CASE WHEN type = 'Credit' THEN amount ELSE 0 END) as totalCredit,
            SUM(CASE WHEN type = 'Debit' THEN amount ELSE -amount END) as outstandingBalance
        FROM expenses
        WHERE category = 'Friends' AND friendId IS NOT NULL AND friendId != ''
        GROUP BY friendId
    """)
    fun getFriendBalances(): Flow<List<FriendBalance>>

    @Query("SELECT * FROM expenses WHERE friendId = :friendName AND category = 'Friends' ORDER BY timestamp DESC")
    fun getTransactionsByFriend(friendName: String): Flow<List<Expense>>

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

    @Query("SELECT SUM(amount) FROM expenses WHERE type = 'Credit' AND (paymentMethod = 'UPI' OR paymentMethod = 'Bank')")
    fun getTotalUpiBankCredits(): Flow<Double?>

    @Query("SELECT SUM(amount) FROM expenses WHERE type = 'Debit' AND (paymentMethod = 'UPI' OR paymentMethod = 'Bank')")
    fun getTotalUpiBankDebits(): Flow<Double?>
}
