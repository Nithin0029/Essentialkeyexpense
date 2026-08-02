package com.nothing.expensetracker.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface FriendDao {
    @Query("SELECT * FROM friends WHERE syncStatus != 'Deleted' ORDER BY name ASC")
    fun getAllFriends(): Flow<List<Friend>>

    @Query("SELECT * FROM friends WHERE name LIKE '%' || :query || '%' AND syncStatus != 'Deleted' ORDER BY name ASC")
    fun searchFriends(query: String): Flow<List<Friend>>

    @Query("SELECT * FROM friends WHERE name = :name LIMIT 1")
    suspend fun getFriendByName(name: String): Friend?

    @Query("SELECT * FROM friends WHERE LOWER(name) = LOWER(:name) AND syncStatus != 'Deleted' LIMIT 1")
    suspend fun getFriendByNameCaseInsensitive(name: String): Friend?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFriend(friend: Friend): Long

    @Update
    suspend fun updateFriend(friend: Friend)

    @Delete
    suspend fun deleteFriend(friend: Friend)

    @Query("SELECT * FROM friends WHERE syncStatus != 'Synced'")
    suspend fun getUnsyncedFriends(): List<Friend>

    @Query("UPDATE friends SET syncStatus = :status, lastSyncAttempt = :attempt, syncError = :error WHERE id = :id")
    suspend fun updateSyncStatus(id: Long, status: String, attempt: Long, error: String?)

    @Query("DELETE FROM friends WHERE syncStatus = 'Deleted'")
    suspend fun purgeDeletedFriends()

    @Query("SELECT COUNT(*) FROM friends WHERE syncStatus = 'Pending' OR syncStatus = 'Failed' OR syncStatus = 'Deleted'")
    fun getUnsyncedCount(): Flow<Int>
}
