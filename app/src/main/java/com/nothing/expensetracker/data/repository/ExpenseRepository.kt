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

    suspend fun insertExpense(expense: Expense) {
        expenseDao.insertExpense(expense)
        if (expense.category == "Friends" && !expense.friendId.isNullOrBlank()) {
            val friend = friendDao.getFriendByName(expense.friendId)
            if (friend != null) {
                spreadsheetManagerProvider.get().updateFriendSummaryInSheet(friend)
            }
        }
    }

    suspend fun updateExpense(expense: Expense) {
        expenseDao.updateExpense(expense)
        val syncSuccess = spreadsheetManagerProvider.get().updateTransactionInSheet(expense)
        if (syncSuccess) {
            expenseDao.markAsSynced(expense.id)
        }
        if (expense.category == "Friends" && !expense.friendId.isNullOrBlank()) {
            val friend = friendDao.getFriendByName(expense.friendId)
            if (friend != null) {
                spreadsheetManagerProvider.get().updateFriendSummaryInSheet(friend)
            }
        }
    }

    suspend fun deleteExpense(expense: Expense) {
        expenseDao.deleteExpense(expense)
        spreadsheetManagerProvider.get().deleteTransactionFromSheet(expense.id.toString())
        if (expense.category == "Friends" && !expense.friendId.isNullOrBlank()) {
            val friend = friendDao.getFriendByName(expense.friendId)
            if (friend != null) {
                spreadsheetManagerProvider.get().updateFriendSummaryInSheet(friend)
            }
        }
    }

    suspend fun getUnsyncedExpenses() = expenseDao.getUnsyncedExpenses()

    suspend fun markAsSynced(id: Long) = expenseDao.markAsSynced(id)

    // Category Management
    fun getCategories() = categoryDao.getAllCategories()

    suspend fun insertCategory(category: Category) {
        categoryDao.insertCategory(category)
        spreadsheetManagerProvider.get().addCategoryToSheet(category)
    }

    suspend fun updateCategory(oldName: String, category: Category) {
        if (oldName != category.name) {
            expenseDao.updateCategoryNameInTransactions(oldName, category.name)
        }
        categoryDao.updateCategory(category)
        spreadsheetManagerProvider.get().updateCategoryInSheet(oldName, category)
    }

    suspend fun deleteCategory(category: Category) {
        if (category.name == "Friends") return // Safety lock

        val count = expenseDao.countExpensesByCategory(category.name)
        if (count == 0) {
            categoryDao.deleteCategory(category)
            spreadsheetManagerProvider.get().deleteCategoryFromSheet(category.name)
        }
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

    suspend fun deleteCategoryAndMoveTransactions(category: Category, replacementCategoryName: String) {
        // 1. Get affected transactions for sync baseline
        val affectedExpenses = expenseDao.getExpensesByCategoryName(category.name)
        
        // 2. Update local DB
        expenseDao.updateCategoryNameInTransactions(category.name, replacementCategoryName)
        categoryDao.deleteCategory(category)
        
        // 3. Sync to Sheets
        spreadsheetManagerProvider.get().deleteCategoryFromSheet(category.name)
        affectedExpenses.forEach { expense ->
            spreadsheetManagerProvider.get().updateTransactionInSheet(expense.copy(category = replacementCategoryName))
        }
    }

    suspend fun deleteCategoryAndTransactions(category: Category) {
        // 1. Get affected transaction IDs
        val affectedExpenses = expenseDao.getExpensesByCategoryName(category.name)
        
        // 2. Update local DB
        expenseDao.deleteExpensesByCategory(category.name)
        categoryDao.deleteCategory(category)
        
        // 3. Sync to Sheets
        spreadsheetManagerProvider.get().deleteCategoryFromSheet(category.name)
        affectedExpenses.forEach { expense ->
            spreadsheetManagerProvider.get().deleteTransactionFromSheet(expense.id.toString())
        }
    }

    suspend fun seedDefaultCategories() {
        if (categoryDao.countCategories() > 0) return

        val defaults = listOf(
            "Home", "Food", "Snacks", "College", "Fuel", 
            "Entertainment", "Medical", "Fitness", "Income", 
            "Travel", "Shopping", "Friends", "Other"
        )
        defaults.forEach { name ->
            categoryDao.insertCategory(Category(name = name, isSystem = true))
        }
    }

    fun getAllCategories() = categoryDao.getAllCategories().map { list -> list.map { it.name } }

    fun getAllFriends() = friendDao.getAllFriends().map { list -> list.map { it.name } }

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
        budgetDao.insertBudget(budget)
        spreadsheetManagerProvider.get().syncBudgetToSheet(budget)
    }

    suspend fun deleteBudget(budget: Budget) {
        budgetDao.deleteBudget(budget)
        spreadsheetManagerProvider.get().deleteBudgetFromSheet(budget.categoryName)
    }
}
