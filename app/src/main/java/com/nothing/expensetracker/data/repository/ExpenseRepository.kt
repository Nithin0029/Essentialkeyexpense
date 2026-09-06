package com.nothing.expensetracker.data.repository

import com.nothing.expensetracker.data.local.Expense
import com.nothing.expensetracker.data.local.ExpenseDao
import com.nothing.expensetracker.data.local.FriendDao
import com.nothing.expensetracker.data.local.Category
import com.nothing.expensetracker.data.local.CategoryDao
import com.nothing.expensetracker.data.local.Budget
import com.nothing.expensetracker.data.local.BudgetDao
import com.nothing.expensetracker.data.local.PaymentMethod
import com.nothing.expensetracker.data.local.PaymentMethodDao
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
    private val paymentMethodDao: PaymentMethodDao
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
        return id
    }

    suspend fun updateExpense(expense: Expense) {
        expenseDao.updateExpense(expense.copy(syncStatus = "Pending"))
    }

    suspend fun deleteExpense(expense: Expense) {
        // Soft delete locally first
        val deletedExpense = expense.copy(syncStatus = "Deleted")
        expenseDao.updateExpense(deletedExpense)
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
        categoryDao.insertCategory(categoryWithPending)
    }

    suspend fun updateCategory(oldName: String, category: Category) {
        val updatedCategory = category.copy(syncStatus = "Pending")
        if (oldName != updatedCategory.name) {
            expenseDao.updateCategoryNameInTransactions(oldName, updatedCategory.name)
        }
        categoryDao.updateCategory(updatedCategory)
    }

    suspend fun deleteCategory(category: Category) {
        if (category.name == "Friends" || category.name == "Transfer") return // Safety lock

        val count = expenseDao.countExpensesByCategory(category.name)
        if (count == 0) {
            // Soft delete locally
            val deletedCategory = category.copy(syncStatus = "Deleted")
            categoryDao.updateCategory(deletedCategory)
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
        expenseDao.updateCategoryNameInTransactions(category.name, replacementCategoryName)
        
        // Soft delete locally
        val deletedCategory = category.copy(syncStatus = "Deleted")
        categoryDao.updateCategory(deletedCategory)
    }

    suspend fun deleteCategoryAndTransactions(category: Category) {
        val affectedExpenses = expenseDao.getExpensesByCategoryName(category.name)
        
        // Mark all as deleted for sync
        affectedExpenses.forEach { deleteExpense(it) }
        
        // Soft delete locally
        val deletedCategory = category.copy(syncStatus = "Deleted")
        categoryDao.updateCategory(deletedCategory)
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
                "Travel", "Shopping", "Friends", "Transfer", "Other"
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

    // Payment Method Management
    fun getPaymentMethods() = paymentMethodDao.getAllPaymentMethods()

    fun getPaymentMethodNames() = paymentMethodDao.getAllPaymentMethods().map { list -> list.map { it.name } }

    suspend fun insertPaymentMethod(method: PaymentMethod) {
        paymentMethodDao.insertPaymentMethod(method)
    }

    suspend fun updatePaymentMethod(oldName: String, method: PaymentMethod) {
        if (oldName != method.name) {
            expenseDao.updatePaymentMethodNameInTransactions(oldName, method.name)
        }
        paymentMethodDao.updatePaymentMethod(method)
    }

    suspend fun deletePaymentMethod(method: PaymentMethod) {
        paymentMethodDao.deletePaymentMethod(method)
    }

    suspend fun getPaymentMethodUsageCount(methodName: String): Int {
        return expenseDao.countExpensesByPaymentMethod(methodName)
    }

    suspend fun getPaymentMethodCount(): Int {
        return paymentMethodDao.countPaymentMethods()
    }

    suspend fun getPaymentMethodByNameCaseInsensitive(name: String): PaymentMethod? {
        return paymentMethodDao.getByNameCaseInsensitive(name.trim())
    }

    suspend fun deletePaymentMethodAndMoveTransactions(method: PaymentMethod, replacementName: String) {
        expenseDao.updatePaymentMethodNameInTransactions(method.name, replacementName)
        paymentMethodDao.deletePaymentMethod(method)
    }

    suspend fun deletePaymentMethodAndTransactions(method: PaymentMethod) {
        val affected = expenseDao.getExpensesByPaymentMethodName(method.name)
        affected.forEach { deleteExpense(it) }
        paymentMethodDao.deletePaymentMethod(method)
    }

    suspend fun seedDefaultPaymentMethods() {
        val currentCount = paymentMethodDao.countPaymentMethods()
        if (currentCount > 0) return

        listOf("UPI", "Cash", "Bank").forEach { name ->
            paymentMethodDao.insertPaymentMethod(PaymentMethod(name = name, isSystem = true))
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
    }

    suspend fun deleteBudget(budget: Budget) {
        // Soft delete locally
        val deletedBudget = budget.copy(syncStatus = "Deleted")
        budgetDao.updateBudget(deletedBudget)
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
