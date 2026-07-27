package com.nothing.expensetracker.data.repository

import com.nothing.expensetracker.data.local.Friend
import com.nothing.expensetracker.data.local.FriendDao
import com.nothing.expensetracker.data.local.ExpenseDao
import javax.inject.Inject
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

    suspend fun insertFriend(friend: Friend) = friendDao.insertFriend(friend)

    suspend fun updateFriend(friend: Friend) = friendDao.updateFriend(friend)

    suspend fun deleteFriend(friend: Friend) = friendDao.deleteFriend(friend)
}
