package com.nothing.expensetracker.data.repository

import com.nothing.expensetracker.data.local.ExpenseDao
import com.nothing.expensetracker.data.local.Friend
import com.nothing.expensetracker.data.local.FriendDao
import com.nothing.expensetracker.sync.SpreadsheetManager
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
        val id = friendDao.insertFriend(friend)
        spreadsheetManagerProvider.get().addFriendToSheet(friend.copy(id = id))
    }

    suspend fun updateFriend(oldName: String, friend: Friend) {
        if (oldName != friend.name) {
            expenseDao.updateFriendNameInTransactions(oldName, friend.name)
        }
        friendDao.updateFriend(friend)
        spreadsheetManagerProvider.get().updateFriendSummaryInSheet(friend)
    }

    suspend fun deleteFriend(friend: Friend) {
        expenseDao.nullifyFriendId(friend.name)
        friendDao.deleteFriend(friend)
        spreadsheetManagerProvider.get().deleteFriendFromSheet(friend.id.toString())
    }
}
