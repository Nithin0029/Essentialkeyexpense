package com.nothing.expensetracker.data.repository

import com.nothing.expensetracker.data.local.ExpenseDao
import com.nothing.expensetracker.data.local.Friend
import com.nothing.expensetracker.data.local.FriendDao
import com.nothing.expensetracker.sync.SpreadsheetManager
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Singleton

@Singleton
class FriendRepository @Inject constructor(
    private val friendDao: FriendDao,
    private val expenseDao: ExpenseDao,
    private val spreadsheetManagerProvider: Provider<SpreadsheetManager>
) {
    fun getAllFriends() = friendDao.getAllFriends()

    fun getFriendBalances() = expenseDao.getFriendBalances()

    fun getTransactionsByFriend(name: String) = expenseDao.getTransactionsByFriend(name)

    fun searchFriends(query: String) = friendDao.searchFriends(query)

    suspend fun getFriendByName(name: String) = friendDao.getFriendByName(name)

    suspend fun hasTransactions(friendName: String): Boolean {
        return expenseDao.getTransactionsByFriend(friendName).first().isNotEmpty()
    }

    suspend fun insertFriend(friend: Friend) {
        val friendWithPendingStatus = friend.copy(syncStatus = "Pending")
        val id = friendDao.insertFriend(friendWithPendingStatus)
        val finalFriend = friendWithPendingStatus.copy(id = id)
        
        // Attempt immediate sync
        val syncSuccess = spreadsheetManagerProvider.get().addFriendToSheet(finalFriend)
        if (syncSuccess) {
            friendDao.updateSyncStatus(id, "Synced", System.currentTimeMillis(), null)
        } else {
            friendDao.updateSyncStatus(id, "Pending", System.currentTimeMillis(), "Initial sync failed")
        }
    }

    suspend fun updateFriend(oldName: String, friend: Friend) {
        val updatedFriend = friend.copy(syncStatus = "Pending")
        if (oldName != updatedFriend.name) {
            expenseDao.updateFriendNameInTransactions(oldName, updatedFriend.name)
        }
        friendDao.updateFriend(updatedFriend)
        
        // Attempt immediate sync
        val syncSuccess = spreadsheetManagerProvider.get().updateFriendSummaryInSheet(updatedFriend)
        if (syncSuccess) {
            friendDao.updateSyncStatus(updatedFriend.id, "Synced", System.currentTimeMillis(), null)
        } else {
            friendDao.updateSyncStatus(updatedFriend.id, "Pending", System.currentTimeMillis(), "Update sync failed")
        }
    }

    suspend fun deleteFriend(friend: Friend) {
        // Soft delete locally first
        val deletedFriend = friend.copy(syncStatus = "Deleted")
        expenseDao.nullifyFriendId(deletedFriend.name)
        friendDao.updateFriend(deletedFriend) 
        
        // Attempt immediate sync
        val syncSuccess = spreadsheetManagerProvider.get().deleteFriendFromSheet(deletedFriend.id.toString(), deletedFriend.name)
        if (syncSuccess) {
            deleteFriendPermanently(deletedFriend) // Final purge
        } else {
            friendDao.updateSyncStatus(deletedFriend.id, "Deleted", System.currentTimeMillis(), "Delete sync failed")
        }
    }

    suspend fun deleteFriendPermanently(friend: Friend) {
        friendDao.deleteFriend(friend)
    }
    
    suspend fun getUnsyncedFriends() = friendDao.getUnsyncedFriends()
    
    suspend fun updateSyncStatus(id: Long, status: String, attempt: Long, error: String?) = 
        friendDao.updateSyncStatus(id, status, attempt, error)
        
    suspend fun purgeDeletedFriends() = friendDao.purgeDeletedFriends()
    
    fun getUnsyncedCount(): Flow<Int> = friendDao.getUnsyncedCount()
}
