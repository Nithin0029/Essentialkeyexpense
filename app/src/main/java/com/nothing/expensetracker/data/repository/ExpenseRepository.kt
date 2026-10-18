package com.nothing.expensetracker.data.repository

import com.nothing.expensetracker.data.local.Expense
import com.nothing.expensetracker.data.local.ExpenseDao
import com.nothing.expensetracker.data.local.FriendDao
import com.nothing.expensetracker.data.local.Category
import com.nothing.expensetracker.data.local.CategoryDao
import com.nothing.expensetracker.data.local.Budget
import com.nothing.expensetracker.data.local.AutopayDao
import com.nothing.expensetracker.data.local.AutopayRule
import com.nothing.expensetracker.data.local.BudgetDao
import com.nothing.expensetracker.data.local.PaymentMethod
import com.nothing.expensetracker.data.local.PaymentMethodDao
import com.nothing.expensetracker.feature.budget.BudgetAlertManager
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
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
    private val paymentMethodDao: PaymentMethodDao,
    private val autopayDao: AutopayDao,
    private val budgetAlertManager: BudgetAlertManager
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
        budgetAlertManager.checkThresholds(expense)
        return id
    }

    suspend fun updateExpense(expense: Expense) {
        expenseDao.updateExpense(expense.copy(syncStatus = "Pending"))
        budgetAlertManager.checkThresholds(expense)
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

    /** Categories offered when adding a Debit transaction (includes subcategories). */
    fun getDebitCategories() = categoryDao.getDebitCategories()

    /** Categories offered when adding a Credit transaction. Always top-level. */
    fun getCreditCategories() = categoryDao.getCreditCategories()

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

    suspend fun countSubcategories(parentId: Long): Int {
        return categoryDao.countSubcategories(parentId)
    }

    suspend fun countDebitCategories(): Int = categoryDao.countDebitCategories()

    suspend fun countCreditCategories(): Int = categoryDao.countCreditCategories()

    /**
     * Turns a category on or off for one side of the ledger. This is what "linking" is: switching
     * [Category.usedForCredit] on for an existing debit category such as "Home" makes the one row
     * serve both sides, so its debit and credit totals are netted in Reports.
     */
    suspend fun setCategoryScope(category: Category, usedForDebit: Boolean, usedForCredit: Boolean) {
        categoryDao.updateCategory(
            category.copy(
                usedForDebit = usedForDebit,
                usedForCredit = usedForCredit,
                syncStatus = "Pending"
            )
        )
    }

    suspend fun deleteCategory(category: Category) {
        if (category.name in PROTECTED_CATEGORIES) return // Safety lock

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

    suspend fun getCategoryUsageCount(categoryName: String, type: String): Int {
        return expenseDao.countExpensesByCategoryAndType(categoryName, type)
    }

    suspend fun getCategoryCount(): Int {
        return categoryDao.countCategories()
    }

    suspend fun getCategoryByNameCaseInsensitive(name: String): Category? {
        return categoryDao.getCategoryByNameCaseInsensitive(name.trim())
    }

    suspend fun getCategoryById(id: Long): Category? = categoryDao.getCategoryById(id)

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

    /**
     * Ensures every default category exists, without touching custom categories or ones the user
     * deleted. Runs on every startup (not just fresh installs) so existing installs pick up newly
     * added defaults too; [CategoryDao.getCategoryByNameAnyStatus] also matches soft-deleted rows
     * so a category the user deliberately removed is never silently re-added.
     */
    suspend fun seedDefaultCategories() {
        android.util.Log.d("CATEGORY_SYNC", "Ensuring default categories")
        try {
            var inserted = 0

            // Debit side. A name that also appears in the Credit defaults is created already
            // linked, so "Friend", "Transfer" and "Other" are one category serving both sides
            // from the very first launch rather than two rows the user has to join up.
            DEFAULT_DEBIT_CATEGORIES.forEach { name ->
                if (shouldSkipDefault(name)) return@forEach
                if (categoryDao.getCategoryByNameAnyStatus(name) == null) {
                    categoryDao.insertCategory(
                        Category(
                            name = name,
                            isSystem = true,
                            syncStatus = "Synced",
                            usedForDebit = true,
                            usedForCredit = name in DEFAULT_CREDIT_CATEGORIES
                        )
                    )
                    inserted++
                }
            }

            // Credit side. Anything already present from the pass above (or from an older
            // install) is switched on for Credit rather than duplicated.
            DEFAULT_CREDIT_CATEGORIES.forEach { name ->
                val existing = categoryDao.getCategoryByNameAnyStatus(name)
                when {
                    existing == null -> {
                        categoryDao.insertCategory(
                            Category(
                                name = name,
                                isSystem = true,
                                syncStatus = "Synced",
                                usedForDebit = false,
                                usedForCredit = true
                            )
                        )
                        inserted++
                    }
                    // Don't resurrect a scope on a category the user deliberately deleted.
                    existing.syncStatus != "Deleted" && !existing.usedForCredit -> {
                        categoryDao.updateCategory(existing.copy(usedForCredit = true))
                    }
                }
            }

            android.util.Log.i("CATEGORY_SYNC", "Default categories ensured | Newly inserted: $inserted")
        } catch (e: Exception) {
            android.util.Log.e("CATEGORY_SYNC", "Critical error during seeding", e)
        }
    }

    /**
     * Guards against seeding a default that an existing install already has under its old name.
     *
     * The Debit side used to ship "Friends" (plural) while Credit shipped "Friend". The defaults
     * now use "Friend" on both sides so the two link into one category, but an install that
     * already has "Friends" must not end up showing both.
     */
    private suspend fun shouldSkipDefault(name: String): Boolean =
        name == "Friend" && categoryDao.getCategoryByNameAnyStatus("Friends") != null

    /**
     * One-time cleanup for categories dropped from the default set: "Income" made no sense as a
     * Debit-type category (Credit already has Salary/Refund/etc.), and Groceries/Snacks/Fuel/
     * Fitness/College were trimmed down to the categories actually wanted. Only removes a category
     * when it's unused so no existing transaction ever loses its category.
     */
    suspend fun cleanupLegacyCategories() {
        try {
            val legacyNames = listOf("Income", "Groceries", "Snacks", "Fuel", "Fitness", "College")
            legacyNames.forEach { name ->
                val category = categoryDao.getCategoryByNameCaseInsensitive(name) ?: return@forEach
                if (expenseDao.countExpensesByCategory(category.name) == 0) {
                    categoryDao.updateCategory(category.copy(syncStatus = "Deleted"))
                    android.util.Log.i("CATEGORY_SYNC", "Removed unused legacy category: $name")
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("CATEGORY_SYNC", "Legacy category cleanup failed", e)
        }
    }

    // Payment Method Management
    fun getPaymentMethods() = paymentMethodDao.getAllPaymentMethods()

    fun getAllPaymentMethods() = paymentMethodDao.getAllPaymentMethods()

    suspend fun getPaymentMethodByName(name: String) =
        paymentMethodDao.getByNameCaseInsensitive(name.trim())

    suspend fun insertRestoredPaymentMethod(method: PaymentMethod) {
        paymentMethodDao.insertPaymentMethod(method)
    }

    suspend fun getAllAutopayRulesOnce(): List<AutopayRule> = autopayDao.getAllAutopayRules().first()

    suspend fun getAutopayRuleById(id: Long) = autopayDao.getAutopayRuleById(id)

    suspend fun insertRestoredAutopayRule(rule: AutopayRule) {
        autopayDao.insertAutopayRule(rule)
    }

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

    /** Names of categories usable on a Debit transaction. */
    fun getDebitCategoryNames() = categoryDao.getDebitCategories().map { list -> list.map { it.name } }

    /** Names of categories usable on a Credit transaction. */
    fun getCreditCategoryNames() = categoryDao.getCreditCategories().map { list -> list.map { it.name } }

    // --- Restore-from-Sheets support ---

    suspend fun getAllExpenseIds(): List<Long> = expenseDao.getAllExpenseIds()

    /** Inserts a row pulled from Google Sheets. Already marked "Synced" because the sheet is
     *  where it came from, so it must not be queued straight back up as a pending change. */
    suspend fun insertRestoredExpense(expense: Expense) {
        expenseDao.insertExpense(expense.copy(syncStatus = "Synced"))
    }

    /** Bulk restore insert. One statement rather than one per row. */
    suspend fun insertRestoredExpenses(expenses: List<Expense>) {
        expenseDao.insertExpenses(expenses.map { it.copy(syncStatus = "Synced") })
    }

    suspend fun insertRestoredCategory(category: Category) {
        categoryDao.insertCategory(category.copy(syncStatus = "Synced"))
    }

    suspend fun findExistingBudget(categoryName: String?, month: Int, year: Int) =
        budgetDao.findExistingBudget(categoryName, month, year)

    suspend fun insertRestoredBudget(budget: com.nothing.expensetracker.data.local.Budget) {
        budgetDao.insertBudget(budget.copy(syncStatus = "Synced"))
    }

    suspend fun getFriendByNameCaseInsensitive(name: String) =
        friendDao.getFriendByNameCaseInsensitive(name.trim())

    suspend fun insertRestoredFriend(friend: com.nothing.expensetracker.data.local.Friend) {
        friendDao.insertFriend(friend.copy(syncStatus = "Synced"))
    }

    suspend fun getAllBudgetsOnce() = budgetDao.getAllBudgets()

    /** Full friend rows, including syncStatus, as opposed to [getAllFriends] which maps to names. */
    fun getAllFriendEntities() = friendDao.getAllFriends()

    suspend fun friendHasTransactions(friendName: String): Boolean =
        expenseDao.getTransactionsByFriend(friendName).first().isNotEmpty()

    /** Hard deletes, used when the spreadsheet says a record is gone. Deliberately not the
     *  soft-delete paths: the row is already absent upstream, so queueing a delete to push
     *  back up would be pointless work. */
    suspend fun deleteFriendPermanently(friend: com.nothing.expensetracker.data.local.Friend) {
        friendDao.deleteFriend(friend)
    }


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

    companion object {
        /**
         * Categories a fresh install starts with. Deliberately short - the list is a starting
         * point, not a taxonomy, and anything missing is one tap away in Category Management.
         *
         * "Friend", "Transfer" and "Other" appear in both lists on purpose: they are seeded as a
         * single linked category serving both sides of the ledger.
         */
        val DEFAULT_DEBIT_CATEGORIES = listOf(
            "Bills", "Entertainment", "Food", "Friend", "Home",
            "Investment", "Medical", "Other", "Transfer"
        )

        val DEFAULT_CREDIT_CATEGORIES = listOf(
            "Friend", "Other", "Refund", "Salary", "Transfer"
        )

        /**
         * Categories the app's own logic looks up by exact name, so they can't be deleted:
         * "Friends"/"Friend" drive friend settlements (TransactionConstants.isFriendCategory) and
         * "Transfer" marks self-transfers that are excluded from income and spending totals.
         */
        val PROTECTED_CATEGORIES = setOf("Friends", "Friend", "Transfer")
    }
}
