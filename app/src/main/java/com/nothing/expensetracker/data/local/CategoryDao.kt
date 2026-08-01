package com.nothing.expensetracker.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface CategoryDao {
    @Query("SELECT * FROM categories ORDER BY name ASC")
    fun getAllCategories(): Flow<List<Category>>

    @Query("SELECT * FROM categories WHERE name = :name LIMIT 1")
    suspend fun getCategoryByName(name: String): Category?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertCategory(category: Category): Long

    @Update
    suspend fun updateCategory(category: Category)

    @Delete
    suspend fun deleteCategory(category: Category)

    @Query("SELECT COUNT(*) FROM categories")
    suspend fun countCategories(): Int

    @Query("SELECT * FROM categories WHERE syncStatus != 'Synced'")
    suspend fun getUnsyncedCategories(): List<Category>

    @Query("UPDATE categories SET syncStatus = :status, lastSyncAttempt = :attempt, syncError = :error WHERE id = :id")
    suspend fun updateSyncStatus(id: Long, status: String, attempt: Long, error: String?)

    @Query("DELETE FROM categories WHERE syncStatus = 'Deleted'")
    suspend fun purgeDeletedCategories()

    @Query("SELECT COUNT(*) FROM categories WHERE syncStatus = 'Pending' OR syncStatus = 'Failed' OR syncStatus = 'Deleted'")
    fun getUnsyncedCount(): Flow<Int>
}
