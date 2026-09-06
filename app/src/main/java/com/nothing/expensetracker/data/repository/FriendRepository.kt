package com.nothing.expensetracker.data.repository

import com.nothing.expensetracker.data.local.ExpenseDao
import com.nothing.expensetracker.data.local.Friend
import com.nothing.expensetracker.data.local.FriendDao
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Singleton

@Singleton
class FriendRepository @Inject constructor(
    private val friendDao: FriendDao,
    private val expenseDao: ExpenseDao
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
        friendDao.insertFriend(friendWithPendingStatus)
    }

    suspend fun updateFriend(oldName: String, friend: Friend) {
        val updatedFriend = friend.copy(syncStatus = "Pending")
        val nameChanged = oldName != updatedFriend.name
        
        if (nameChanged) {
            // 1. Update local transactions
            expenseDao.updateFriendNameInTransactions(oldName, updatedFriend.name)
        }

        // 2. Update the friend profile locally
        friendDao.updateFriend(updatedFriend)
    }

    suspend fun deleteFriendOnly(friend: Friend) {
        // Soft delete locally first
        val deletedFriend = friend.copy(syncStatus = "Deleted")
        
        // 1. Remove friend link from all transactions (Keep the records)
        expenseDao.nullifyFriendId(deletedFriend.name)
        
        // 2. Mark friend for deletion to trigger sync
        friendDao.updateFriend(deletedFriend) 
    }

    suspend fun deleteFriendAndTransactions(friend: Friend) {
        val deletedFriend = friend.copy(syncStatus = "Deleted")
        
        // 1. Fetch and mark all associated transactions for deletion
        val transactions = expenseDao.getTransactionsByFriend(friend.name).first()
        transactions.forEach { expense ->
            val deletedExpense = expense.copy(syncStatus = "Deleted")
            expenseDao.updateExpense(deletedExpense)
        }
        
        // 2. Mark friend for deletion
        friendDao.updateFriend(deletedFriend)
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
