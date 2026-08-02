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

    suspend fun getFriendByNameCaseInsensitive(name: String) = friendDao.getFriendByNameCaseInsensitive(name.trim())

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
        val nameChanged = oldName != updatedFriend.name
        
        if (nameChanged) {
            // 1. Fetch historical transactions
            val transactions = expenseDao.getTransactionsByFriend(oldName).first()
            
            // 2. Update local transactions
            expenseDao.updateFriendNameInTransactions(oldName, updatedFriend.name)
            
            // 3. Update transactions in Google Sheets
            transactions.forEach { expense ->
                val updatedExpense = expense.copy(friendId = updatedFriend.name)
                // We attempt to update the sheet. If it fails (offline), the local DB is already updated 
                // and the transaction remains in its current sync state. 
                // Future syncs will use the new name because the local entity is updated.
                spreadsheetManagerProvider.get().updateTransactionInSheet(updatedExpense)
            }
        }

        // 4. Update the friend profile locally
        friendDao.updateFriend(updatedFriend)
        
        // 5. Update the Friend summary in Google Sheets
        val syncSuccess = spreadsheetManagerProvider.get().updateFriendSummaryInSheet(updatedFriend)
        if (syncSuccess) {
            friendDao.updateSyncStatus(updatedFriend.id, "Synced", System.currentTimeMillis(), null)
        } else {
            friendDao.updateSyncStatus(updatedFriend.id, "Pending", System.currentTimeMillis(), "Update sync failed")
        }
    }

    suspend fun deleteFriendOnly(friend: Friend) {
        // Soft delete locally first
        val deletedFriend = friend.copy(syncStatus = "Deleted")
        
        // 1. Remove friend link from all transactions (Keep the records)
        expenseDao.nullifyFriendId(deletedFriend.name)
        
        // 2. Mark friend for deletion to trigger sync
        friendDao.updateFriend(deletedFriend) 
        
        // 3. Attempt immediate sync
        val syncSuccess = spreadsheetManagerProvider.get().deleteFriendFromSheet(deletedFriend.id.toString(), deletedFriend.name)
        if (syncSuccess) {
            deleteFriendPermanently(deletedFriend)
        } else {
            friendDao.updateSyncStatus(deletedFriend.id, "Deleted", System.currentTimeMillis(), "Delete sync failed")
        }
    }

    suspend fun deleteFriendAndTransactions(friend: Friend) {
        val deletedFriend = friend.copy(syncStatus = "Deleted")
        
        // 1. Fetch and mark all associated transactions for deletion
        val transactions = expenseDao.getTransactionsByFriend(friend.name).first()
        transactions.forEach { expense ->
            val deletedExpense = expense.copy(syncStatus = "Deleted")
            expenseDao.updateExpense(deletedExpense)
            // Attempt immediate cloud deletion for each row
            spreadsheetManagerProvider.get().deleteTransactionFromSheet(expense.id.toString())
        }
        
        // 2. Mark friend for deletion
        friendDao.updateFriend(deletedFriend)
        
        // 3. Attempt cloud deletion for friend summary
        val syncSuccess = spreadsheetManagerProvider.get().deleteFriendFromSheet(deletedFriend.id.toString(), deletedFriend.name)
        if (syncSuccess) {
            deleteFriendPermanently(deletedFriend)
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
