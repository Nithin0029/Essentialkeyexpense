package com.nothing.expensetracker.sync

import com.nothing.expensetracker.data.repository.ExpenseRepository
import com.nothing.expensetracker.data.repository.FriendRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.withContext
import android.util.Log
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SyncManager @Inject constructor(
    private val repository: ExpenseRepository,
    private val friendRepository: FriendRepository,
    private val syncScheduler: SyncScheduler,
    private val spreadsheetManager: SpreadsheetManager
) {
    private val tag = "SyncManager"

    fun getUnsyncedCount(): Flow<Int> = combine(
        repository.getUnsyncedCount(),
        repository.getUnsyncedCategoryCount(),
        repository.getUnsyncedBudgetCount(),
        friendRepository.getUnsyncedCount()
    ) { expense, category, budget, friend ->
        expense + category + budget + friend
    }

    fun getSyncedCount(): Flow<Int> = repository.getSyncedCount()
    fun getFailedCount(): Flow<Int> = repository.getFailedCount()
    fun getLastSyncTime(): Flow<Long?> = repository.getLastSyncTime()

    suspend fun syncNow() = withContext(Dispatchers.IO) {
        Log.i(tag, "[SYNC] Global sync cycle started")

        // Priority 1: Categories (Names are keys for Transactions/Budgets)
        try {
            val unsyncedCategories = repository.getUnsyncedCategories()
            Log.d(tag, "[SYNC] CATEGORY | Queue Count: ${unsyncedCategories.size}")
            unsyncedCategories.forEach { category ->
                val isDelete = category.syncStatus == "Deleted"
                Log.d(tag, "[SYNC] CATEGORY | ID: ${category.id} | Operation: ${if (isDelete) "DELETE" else "CREATE/UPDATE"} | Status: ${category.syncStatus}")
                
                val success = if (isDelete) {
                    spreadsheetManager.deleteCategoryFromSheet(category.name)
                } else {
                    spreadsheetManager.addCategoryToSheet(category)
                }

                if (success) {
                    if (isDelete) {
                        repository.deleteCategoryPermanently(category)
                    } else {
                        repository.updateCategorySyncStatus(category.id, "Synced", System.currentTimeMillis(), null)
                    }
                    Log.i(tag, "[SYNC] CATEGORY | Success | ID: ${category.id}")
                }
            }
        } catch (e: Exception) {
            Log.e(tag, "[SYNC] CATEGORY | Loop Error", e)
        }

        // Priority 2: Friends (IDs/Names are keys for Transactions)
        try {
            val unsyncedFriends = friendRepository.getUnsyncedFriends()
            Log.d(tag, "[SYNC] FRIEND | Queue Count: ${unsyncedFriends.size}")
            unsyncedFriends.forEach { friend ->
                val isDelete = friend.syncStatus == "Deleted"
                Log.d(tag, "[SYNC] FRIEND | ID: ${friend.id} | Operation: ${if (isDelete) "DELETE" else "CREATE/UPDATE"} | Status: ${friend.syncStatus}")
                
                val success = if (isDelete) {
                    spreadsheetManager.deleteFriendFromSheet(friend.id.toString(), friend.name)
                } else {
                    spreadsheetManager.addFriendToSheet(friend)
                }

                if (success) {
                    if (isDelete) {
                        friendRepository.deleteFriendPermanently(friend)
                    } else {
                        friendRepository.updateSyncStatus(friend.id, "Synced", System.currentTimeMillis(), null)
                    }
                    Log.i(tag, "[SYNC] FRIEND | Success | ID: ${friend.id}")
                }
            }
        } catch (e: Exception) {
            Log.e(tag, "[SYNC] FRIEND | Loop Error", e)
        }

        // Priority 3: Budgets
        try {
            val unsyncedBudgets = repository.getUnsyncedBudgets()
            Log.d(tag, "[SYNC] BUDGET | Queue Count: ${unsyncedBudgets.size}")
            unsyncedBudgets.forEach { budget ->
                val isDelete = budget.syncStatus == "Deleted"
                Log.d(tag, "[SYNC] BUDGET | ID: ${budget.id} | Operation: ${if (isDelete) "DELETE" else "CREATE/UPDATE"} | Status: ${budget.syncStatus}")
                
                val success = if (isDelete) {
                    spreadsheetManager.deleteBudgetFromSheet(budget.categoryName)
                } else {
                    spreadsheetManager.syncBudgetToSheet(budget)
                }

                if (success) {
                    if (isDelete) {
                        repository.deleteBudgetPermanently(budget)
                    } else {
                        repository.updateBudgetSyncStatus(budget.id, "Synced", System.currentTimeMillis(), null)
                    }
                    Log.i(tag, "[SYNC] BUDGET | Success | ID: ${budget.id}")
                }
            }
        } catch (e: Exception) {
            Log.e(tag, "[SYNC] BUDGET | Loop Error", e)
        }

        // Priority 4: Transactions
        try {
            val unsyncedExpenses = repository.getUnsyncedExpenses()
            Log.d(tag, "[SYNC] TRANSACTION | Queue Count: ${unsyncedExpenses.size}")
            unsyncedExpenses.forEach { expense ->
                val isDelete = expense.syncStatus == "Deleted"
                Log.d(tag, "[SYNC] TRANSACTION | ID: ${expense.id} | Operation: ${if (isDelete) "DELETE" else "CREATE/UPDATE"} | Status: ${expense.syncStatus}")
                
                val success = if (isDelete) {
                    spreadsheetManager.deleteTransactionFromSheet(expense.id.toString())
                } else {
                    spreadsheetManager.addTransactionToSheet(expense)
                }

                if (success) {
                    if (isDelete) {
                        repository.deleteExpensePermanently(expense)
                    } else {
                        repository.updateSyncStatus(expense.id, "Synced", System.currentTimeMillis(), null)
                        
                        // Recalculate friend summary if needed
                        if ((expense.category == "Friends" || expense.category == "Friend") && !expense.friendId.isNullOrBlank()) {
                            val friend = repository.getFriendByName(expense.friendId)
                            if (friend != null) {
                                spreadsheetManager.updateFriendSummaryInSheet(friend, triggeredByTransactionId = expense.id)
                            }
                        }
                    }
                    Log.i(tag, "[SYNC] TRANSACTION | Success | ID: ${expense.id}")
                }
            }
        } catch (e: Exception) {
            Log.e(tag, "[SYNC] TRANSACTION | Loop Error", e)
        }
        
        Log.i(tag, "[SYNC] Global sync cycle completed")
    }
}
