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
    @Query("SELECT * FROM expenses WHERE syncStatus != 'Deleted' ORDER BY timestamp DESC")
    fun getAllExpenses(): Flow<List<Expense>>

    @Query("""
        SELECT * FROM expenses 
        WHERE syncStatus != 'Deleted'
        AND (:query = '' OR category LIKE '%' || :query || '%' OR notes LIKE '%' || :query || '%' OR friendId LIKE '%' || :query || '%' OR paymentMethod LIKE '%' || :query || '%')
        AND (:type = 'All' OR type = :type)
        AND (:method = 'All' OR paymentMethod = :method)
        AND (:category = 'All' OR category = :category)
        AND (timestamp >= :startTime AND timestamp <= :endTime)
        ORDER BY 
            CASE WHEN :sort = 'NEWEST' THEN timestamp END DESC,
            CASE WHEN :sort = 'OLDEST' THEN timestamp END ASC,
            CASE WHEN :sort = 'HIGHEST_AMOUNT' THEN amount END DESC,
            CASE WHEN :sort = 'LOWEST_AMOUNT' THEN amount END ASC,
            CASE WHEN :sort = 'CATEGORY_AZ' THEN category END ASC,
            CASE WHEN :sort = 'CATEGORY_ZA' THEN category END DESC
    """)
    fun getFilteredExpenses(
        query: String,
        type: String,
        method: String,
        category: String,
        sort: String,
        startTime: Long = 0,
        endTime: Long = Long.MAX_VALUE
    ): Flow<List<Expense>>

    @Query("SELECT * FROM expenses WHERE id = :id AND syncStatus != 'Deleted'")
    fun getExpenseById(id: Long): Flow<Expense?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertExpense(expense: Expense): Long

    @androidx.room.Update
    suspend fun updateExpense(expense: Expense)

    @Delete
    suspend fun deleteExpense(expense: Expense)

    @Query("DELETE FROM expenses WHERE id = :expenseId")
    suspend fun deleteExpenseById(expenseId: Long)

    @Query("SELECT * FROM expenses WHERE syncStatus != 'Synced'")
    suspend fun getUnsyncedExpenses(): List<Expense>

    @Query("UPDATE expenses SET syncStatus = :status, lastSyncAttempt = :attempt, syncError = :error WHERE id = :id")
    suspend fun updateSyncStatus(id: Long, status: String, attempt: Long, error: String?)

    @Query("SELECT COUNT(*) FROM expenses WHERE syncStatus = 'Pending' OR syncStatus = 'Failed' OR syncStatus = 'Deleted'")
    fun getUnsyncedCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM expenses WHERE syncStatus = 'Synced'")
    fun getSyncedCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM expenses WHERE syncStatus = 'Failed'")
    fun getFailedCount(): Flow<Int>

    @Query("SELECT MAX(lastSyncAttempt) FROM expenses WHERE syncStatus = 'Synced'")
    fun getLastSyncTime(): Flow<Long?>

    @Query("SELECT DISTINCT category FROM expenses WHERE syncStatus != 'Deleted' ORDER BY category ASC")
    fun getAllCategories(): Flow<List<String>>

    @Query("SELECT DISTINCT friendId FROM expenses WHERE syncStatus != 'Deleted' AND friendId IS NOT NULL AND friendId != '' ORDER BY friendId ASC")
    fun getAllFriends(): Flow<List<String>>

    @Query("""
        SELECT 
            friendId as friendName,
            SUM(CASE WHEN type = 'Debit' THEN amount ELSE 0 END) as totalDebit,
            SUM(CASE WHEN type = 'Credit' THEN amount ELSE 0 END) as totalCredit,
            SUM(CASE WHEN type = 'Debit' THEN amount ELSE -amount END) as outstandingBalance
        FROM expenses
        WHERE syncStatus != 'Deleted' AND (category = 'Friends' OR category = 'Friend') AND friendId IS NOT NULL AND friendId != ''
        GROUP BY friendId
    """)
    fun getFriendBalances(): Flow<List<FriendBalance>>

    @Query("SELECT * FROM expenses WHERE syncStatus != 'Deleted' AND friendId = :friendName AND (category = 'Friends' OR category = 'Friend') ORDER BY timestamp DESC")
    fun getTransactionsByFriend(friendName: String): Flow<List<Expense>>

    @Query("UPDATE expenses SET friendId = NULL WHERE friendId = :friendName")
    suspend fun nullifyFriendId(friendName: String)

    @Query("UPDATE expenses SET friendId = :newName WHERE friendId = :oldName")
    suspend fun updateFriendNameInTransactions(oldName: String, newName: String)

    @Query("UPDATE expenses SET category = :newName WHERE category = :oldName")
    suspend fun updateCategoryNameInTransactions(oldName: String, newName: String)

    @Query("SELECT COUNT(*) FROM expenses WHERE category = :categoryName")
    suspend fun countExpensesByCategory(categoryName: String): Int

    @Query("SELECT * FROM expenses WHERE category = :categoryName")
    suspend fun getExpensesByCategoryName(categoryName: String): List<Expense>

    @Query("DELETE FROM expenses WHERE category = :categoryName")
    suspend fun deleteExpensesByCategory(categoryName: String)

    @Query("SELECT category, SUM(amount) as totalAmount FROM expenses WHERE syncStatus != 'Deleted' AND type = 'Debit' GROUP BY category ORDER BY totalAmount DESC")
    fun getExpensesByCategory(): Flow<List<CategoryExpense>>

    @Query("""
        SELECT category, SUM(amount) as totalAmount 
        FROM expenses 
        WHERE syncStatus != 'Deleted'
          AND type = 'Debit' 
          AND strftime('%m', datetime(timestamp / 1000, 'unixepoch')) = :month
          AND strftime('%Y', datetime(timestamp / 1000, 'unixepoch')) = :year
        GROUP BY category 
        ORDER BY totalAmount DESC
    """)
    fun getExpensesByCategoryFiltered(month: String, year: String): Flow<List<CategoryExpense>>

    @Query("SELECT SUM(amount) FROM expenses WHERE syncStatus != 'Deleted' AND type = 'Credit' AND (paymentMethod = 'UPI' OR paymentMethod = 'Bank')")
    fun getTotalUpiBankCredits(): Flow<Double?>

    @Query("SELECT SUM(amount) FROM expenses WHERE syncStatus != 'Deleted' AND type = 'Debit' AND (paymentMethod = 'UPI' OR paymentMethod = 'Bank')")
    fun getTotalUpiBankDebits(): Flow<Double?>

    @Query("SELECT SUM(amount) FROM expenses WHERE syncStatus != 'Deleted' AND type = 'Credit' AND paymentMethod = 'Cash'")
    fun getTotalCashCredits(): Flow<Double?>

    @Query("SELECT SUM(amount) FROM expenses WHERE syncStatus != 'Deleted' AND type = 'Debit' AND paymentMethod = 'Cash'")
    fun getTotalCashDebits(): Flow<Double?>
}
