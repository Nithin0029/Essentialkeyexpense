package com.nothing.expensetracker.data.repository

import com.nothing.expensetracker.data.local.Expense
import com.nothing.expensetracker.data.local.ExpenseDao
import com.nothing.expensetracker.data.local.FriendDao
import com.nothing.expensetracker.data.local.Category
import com.nothing.expensetracker.data.local.CategoryDao
import com.nothing.expensetracker.data.local.Budget
import com.nothing.expensetracker.data.local.BudgetDao
import com.nothing.expensetracker.sync.SpreadsheetManager
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Singleton

@Singleton
class ExpenseRepository @Inject constructor(
    private val expenseDao: ExpenseDao,
    private val friendDao: FriendDao,
    private val categoryDao: CategoryDao,
    private val budgetDao: BudgetDao,
    private val spreadsheetManagerProvider: Provider<SpreadsheetManager>
) {
    fun getAllExpenses() = expenseDao.getAllExpenses()

    fun getFilteredExpenses(
        query: String,
        type: String,
        method: String,
        category: String,
        sort: String,
        startTime: Long = 0,
        endTime: Long = Long.MAX_VALUE
    ) = expenseDao.getFilteredExpenses(query, type, method, category, sort, startTime, endTime)

    fun getExpenseById(id: Long) = expenseDao.getExpenseById(id)

    suspend fun insertExpense(expense: Expense): Long {
        val id = expenseDao.insertExpense(expense.copy(syncStatus = "Pending"))
        val insertedExpense = expense.copy(id = id, syncStatus = "Pending")
        
        // Attempt direct sync
        val syncSuccess = spreadsheetManagerProvider.get().addTransactionToSheet(insertedExpense)
        if (syncSuccess) {
            expenseDao.updateSyncStatus(id, "Synced", System.currentTimeMillis(), null)
        } else {
            expenseDao.updateSyncStatus(id, "Pending", System.currentTimeMillis(), "Initial sync failed")
        }

        if ((expense.category == "Friends" || expense.category == "Friend") && !expense.friendId.isNullOrBlank()) {
            val friend = friendDao.getFriendByName(expense.friendId)
            if (friend != null) {
                spreadsheetManagerProvider.get().updateFriendSummaryInSheet(friend, triggeredByTransactionId = id)
            }
        }
        return id
    }

    suspend fun updateExpense(expense: Expense) {
        expenseDao.updateExpense(expense.copy(syncStatus = "Pending"))
        val syncSuccess = spreadsheetManagerProvider.get().updateTransactionInSheet(expense)
        if (syncSuccess) {
            expenseDao.updateSyncStatus(expense.id, "Synced", System.currentTimeMillis(), null)
        } else {
            expenseDao.updateSyncStatus(expense.id, "Pending", System.currentTimeMillis(), "Update sync failed")
        }
        if ((expense.category == "Friends" || expense.category == "Friend") && !expense.friendId.isNullOrBlank()) {
            val friend = friendDao.getFriendByName(expense.friendId)
            if (friend != null) {
                spreadsheetManagerProvider.get().updateFriendSummaryInSheet(friend, triggeredByTransactionId = expense.id)
            }
        }
    }

    suspend fun deleteExpense(expense: Expense) {
        // Soft delete locally first
        val deletedExpense = expense.copy(syncStatus = "Deleted")
        expenseDao.updateExpense(deletedExpense)
        
        // Attempt immediate cloud deletion
        val syncSuccess = spreadsheetManagerProvider.get().deleteTransactionFromSheet(expense.id.toString())
        if (syncSuccess) {
            deleteExpensePermanently(expense)
            android.util.Log.i("ExpenseRepository", "Transaction ${expense.id} deleted from cloud and locally.")
        } else {
            android.util.Log.w("ExpenseRepository", "Transaction ${expense.id} cloud deletion failed. Queued for retry.")
        }

        if ((expense.category == "Friends" || expense.category == "Friend") && !expense.friendId.isNullOrBlank()) {
            val friend = friendDao.getFriendByName(expense.friendId)
            if (friend != null) {
                spreadsheetManagerProvider.get().updateFriendSummaryInSheet(friend, triggeredByTransactionId = expense.id)
            }
        }
    }

    suspend fun deleteExpensePermanently(expense: Expense) {
        expenseDao.deleteExpense(expense)
    }

    suspend fun getUnsyncedExpenses() = expenseDao.getUnsyncedExpenses()

    suspend fun updateSyncStatus(id: Long, status: String, attempt: Long, error: String?) = 
        expenseDao.updateSyncStatus(id, status, attempt, error)

    fun getUnsyncedCount() = expenseDao.getUnsyncedCount()
    fun getSyncedCount() = expenseDao.getSyncedCount()
    fun getFailedCount() = expenseDao.getFailedCount()
    fun getLastSyncTime() = expenseDao.getLastSyncTime()

    // Category Management
    fun getCategories() = categoryDao.getAllCategories()

    suspend fun insertCategory(category: Category) {
        val categoryWithPending = category.copy(syncStatus = "Pending")
        val id = categoryDao.insertCategory(categoryWithPending)
        
        // Handle IGNORE case: if ID is -1, the category already exists
        if (id == -1L) {
            android.util.Log.d("ExpenseRepository", "Category '${category.name}' already exists locally. Skipping insert.")
            return
        }

        val finalCategory = categoryWithPending.copy(id = id)
        val syncSuccess = spreadsheetManagerProvider.get().addCategoryToSheet(finalCategory)
        if (syncSuccess) {
            categoryDao.updateSyncStatus(finalCategory.id, "Synced", System.currentTimeMillis(), null)
        } else {
            categoryDao.updateSyncStatus(finalCategory.id, "Pending", System.currentTimeMillis(), "Initial sync failed")
        }
    }

    suspend fun updateCategory(oldName: String, category: Category) {
        val updatedCategory = category.copy(syncStatus = "Pending")
        if (oldName != updatedCategory.name) {
            expenseDao.updateCategoryNameInTransactions(oldName, updatedCategory.name)
        }
        categoryDao.updateCategory(updatedCategory)
        
        val syncSuccess = spreadsheetManagerProvider.get().updateCategoryInSheet(oldName, updatedCategory)
        if (syncSuccess) {
            categoryDao.updateSyncStatus(updatedCategory.id, "Synced", System.currentTimeMillis(), null)
        } else {
            categoryDao.updateSyncStatus(updatedCategory.id, "Pending", System.currentTimeMillis(), "Update sync failed")
        }
    }

    suspend fun deleteCategory(category: Category) {
        if (category.name == "Friends") return // Safety lock

        val count = expenseDao.countExpensesByCategory(category.name)
        if (count == 0) {
            // Soft delete locally
            val deletedCategory = category.copy(syncStatus = "Deleted")
            categoryDao.updateCategory(deletedCategory)
            
            val syncSuccess = spreadsheetManagerProvider.get().deleteCategoryFromSheet(category.name)
            if (syncSuccess) {
                deleteCategoryPermanently(category)
            } else {
                categoryDao.updateSyncStatus(category.id, "Deleted", System.currentTimeMillis(), "Delete sync failed")
            }
        }
    }

    suspend fun deleteCategoryPermanently(category: Category) {
        categoryDao.deleteCategory(category)
    }

    suspend fun isCategoryInUse(categoryName: String): Boolean {
        return expenseDao.countExpensesByCategory(categoryName) > 0
    }

    suspend fun getCategoryUsageCount(categoryName: String): Int {
        return expenseDao.countExpensesByCategory(categoryName)
    }

    suspend fun getCategoryCount(): Int {
        return categoryDao.countCategories()
    }

    suspend fun getCategoryByNameCaseInsensitive(name: String): Category? {
        return categoryDao.getCategoryByNameCaseInsensitive(name.trim())
    }

    suspend fun deleteCategoryAndMoveTransactions(category: Category, replacementCategoryName: String) {
        val affectedExpenses = expenseDao.getExpensesByCategoryName(category.name)
        expenseDao.updateCategoryNameInTransactions(category.name, replacementCategoryName)
        
        // Soft delete locally
        val deletedCategory = category.copy(syncStatus = "Deleted")
        categoryDao.updateCategory(deletedCategory)
        
        val syncSuccess = spreadsheetManagerProvider.get().deleteCategoryFromSheet(category.name)
        if (syncSuccess) {
            deleteCategoryPermanently(category)
        } else {
            categoryDao.updateSyncStatus(category.id, "Deleted", System.currentTimeMillis(), "Delete sync failed")
        }
        
        affectedExpenses.forEach { expense ->
            updateExpense(expense.copy(category = replacementCategoryName))
        }
    }

    suspend fun deleteCategoryAndTransactions(category: Category) {
        val affectedExpenses = expenseDao.getExpensesByCategoryName(category.name)
        
        // Mark all as deleted for sync
        affectedExpenses.forEach { deleteExpense(it) }
        
        // Soft delete locally
        val deletedCategory = category.copy(syncStatus = "Deleted")
        categoryDao.updateCategory(deletedCategory)
        
        val syncSuccess = spreadsheetManagerProvider.get().deleteCategoryFromSheet(category.name)
        if (syncSuccess) {
            deleteCategoryPermanently(category)
        } else {
            categoryDao.updateSyncStatus(category.id, "Deleted", System.currentTimeMillis(), "Delete sync failed")
        }
    }

    suspend fun seedDefaultCategories() {
        android.util.Log.d("CATEGORY_SYNC", "Initialization Started")
        try {
            val currentCount = categoryDao.countCategories()
            if (currentCount > 0) {
                android.util.Log.d("CATEGORY_SYNC", "Already Initialized | Count: $currentCount | Skipped")
                return
            }

            val defaults = listOf(
                "Home", "Food", "Snacks", "College", "Fuel", 
                "Entertainment", "Medical", "Fitness", "Income", 
                "Travel", "Shopping", "Friends", "Other"
            )
            defaults.forEach { name ->
                // Insert directly to DAO to avoid triggering the 'insertCategory' cloud sync logic during seeding
                categoryDao.insertCategory(Category(
                    name = name, 
                    isSystem = true,
                    syncStatus = "Synced" 
                ))
            }
            android.util.Log.i("CATEGORY_SYNC", "Default Categories Inserted | Count: ${defaults.size}")
        } catch (e: Exception) {
            android.util.Log.e("CATEGORY_SYNC", "Critical error during seeding", e)
        }
    }

    fun getAllCategories() = categoryDao.getAllCategories().map { list -> list.map { it.name } }

    fun getAllFriends() = friendDao.getAllFriends().map { list -> list.map { it.name } }

    suspend fun getFriendByName(name: String) = friendDao.getFriendByName(name)

    fun getFriendBalances() = expenseDao.getFriendBalances()

    fun getExpensesByCategory() = expenseDao.getExpensesByCategory()

    fun getExpensesByCategoryFiltered(month: String, year: String) = 
        expenseDao.getExpensesByCategoryFiltered(month, year)

    fun getTotalUpiBankCredits() = expenseDao.getTotalUpiBankCredits()

    fun getTotalUpiBankDebits() = expenseDao.getTotalUpiBankDebits()

    fun getTotalCashCredits() = expenseDao.getTotalCashCredits()

    fun getTotalCashDebits() = expenseDao.getTotalCashDebits()

    // Budget Management
    fun getOverallBudget(month: Int, year: Int): Flow<Budget?> = budgetDao.getOverallBudget(month, year)

    fun getCategoryBudgets(month: Int, year: Int): Flow<List<Budget>> = budgetDao.getCategoryBudgets(month, year)

    suspend fun insertBudget(budget: Budget) {
        // 1. Check for existing budget row to prevent duplicates
        val existing = budgetDao.findExistingBudget(budget.categoryName, budget.month, budget.year)
        
        // 2. Prepare new budget with correct ID if found
        val budgetToInsert = if (existing != null) {
            budget.copy(id = existing.id, syncStatus = "Pending")
        } else {
            budget.copy(syncStatus = "Pending")
        }
        
        // 3. Insert/Update Room
        budgetDao.insertBudget(budgetToInsert)
        
        // 4. Sync to Cloud
        val syncSuccess = spreadsheetManagerProvider.get().syncBudgetToSheet(budgetToInsert)
        
        // 5. Update sync status
        if (syncSuccess) {
            val savedId = if (budgetToInsert.id == 0L) {
                // If it was a new insert, we need the generated ID
                budgetDao.getAllBudgets().find { 
                    it.categoryName == budget.categoryName && it.month == budget.month && it.year == budget.year && it.syncStatus != "Deleted"
                }?.id ?: 0L
            } else {
                budgetToInsert.id
            }
            
            if (savedId != 0L) {
                budgetDao.updateSyncStatus(savedId, "Synced", System.currentTimeMillis(), null)
            }
        } else {
            val savedId = if (budgetToInsert.id == 0L) {
                budgetDao.getAllBudgets().find { 
                    it.categoryName == budget.categoryName && it.month == budget.month && it.year == budget.year && it.syncStatus != "Deleted"
                }?.id ?: 0L
            } else {
                budgetToInsert.id
            }
            if (savedId != 0L) {
                budgetDao.updateSyncStatus(savedId, "Pending", System.currentTimeMillis(), "Initial sync failed")
            }
        }
    }

    suspend fun deleteBudget(budget: Budget) {
        // Soft delete locally
        val deletedBudget = budget.copy(syncStatus = "Deleted")
        budgetDao.updateBudget(deletedBudget)
        
        val syncSuccess = spreadsheetManagerProvider.get().deleteBudgetFromSheet(budget.categoryName)
        if (syncSuccess) {
            deleteBudgetPermanently(budget)
        } else {
            budgetDao.updateSyncStatus(budget.id, "Deleted", System.currentTimeMillis(), "Delete sync failed")
        }
    }

    suspend fun deleteBudgetPermanently(budget: Budget) {
        budgetDao.deleteBudget(budget)
    }
    
    suspend fun getUnsyncedCategories() = categoryDao.getUnsyncedCategories()
    suspend fun updateCategorySyncStatus(id: Long, status: String, attempt: Long, error: String?) = categoryDao.updateSyncStatus(id, status, attempt, error)
    suspend fun purgeDeletedCategories() = categoryDao.purgeDeletedCategories()
    fun getUnsyncedCategoryCount() = categoryDao.getUnsyncedCount()
    
    suspend fun getUnsyncedBudgets() = budgetDao.getUnsyncedBudgets()
    suspend fun updateBudgetSyncStatus(id: Long, status: String, attempt: Long, error: String?) = budgetDao.updateSyncStatus(id, status, attempt, error)
    suspend fun purgeDeletedBudgets() = budgetDao.purgeDeletedBudgets()
    fun getUnsyncedBudgetCount() = budgetDao.getUnsyncedCount()
}
