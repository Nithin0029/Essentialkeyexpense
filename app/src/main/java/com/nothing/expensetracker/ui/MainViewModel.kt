package com.nothing.expensetracker.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import android.util.Log
import com.nothing.expensetracker.data.local.AppPrefs
import com.nothing.expensetracker.data.local.CategoryExpense
import com.nothing.expensetracker.data.local.Expense
import com.nothing.expensetracker.data.repository.ExpenseRepository
import com.nothing.expensetracker.data.repository.FriendRepository
import com.nothing.expensetracker.ui.history.TransactionConstants
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters
import java.util.*
import javax.inject.Inject

data class DashboardData(
    val bankBalance: Double = 0.0,
    val cashBalance: Double = 0.0,
    val totalAssets: Double = 0.0,
    val openingBankBalance: Double = 0.0,
    val openingCashBalance: Double = 0.0,
    val income: Double = 0.0,
    val expense: Double = 0.0,
    val todaySpending: Double = 0.0,
    val weekSpending: Double = 0.0,
    val monthSpending: Double = 0.0,
    val topCategories: List<CategoryExpense> = emptyList(),
    val recentTransactions: List<Expense> = emptyList(),
    val hasTransactions: Boolean = false,
    val isAllSynced: Boolean = true
)

sealed class DashboardUiState {
    object Loading : DashboardUiState()
    data class Success(val data: DashboardData) : DashboardUiState()
    object Empty : DashboardUiState()
}

@HiltViewModel
class MainViewModel @Inject constructor(
    private val repository: ExpenseRepository,
    private val friendRepository: FriendRepository,
    private val appPrefs: AppPrefs,
    private val syncScheduler: com.nothing.expensetracker.sync.SyncScheduler,
    private val autopayScheduler: com.nothing.expensetracker.feature.autopay.AutopayScheduler
) : ViewModel() {

    private val _selectedMonth = MutableStateFlow(SimpleDateFormat("MM", Locale.getDefault()).format(Date()))
    val selectedMonth: StateFlow<String> = _selectedMonth

    private val _selectedYear = MutableStateFlow(SimpleDateFormat("yyyy", Locale.getDefault()).format(Date()))
    val selectedYear: StateFlow<String> = _selectedYear

    /** True until the user has been asked for their starting balances, once per install. */
    val needsBalanceSetup: StateFlow<Boolean> = appPrefs.hasSetUpBalances
        .map { !it }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    /**
     * Records the balances the user is holding right now.
     *
     * Solves for the opening balance rather than storing the figure directly, so the number is
     * correct whether this is a brand-new install with no transactions (where the two are the
     * same) or one that has just restored a history from the spreadsheet.
     */
    fun completeBalanceSetup(bankNow: Double, cashNow: Double) {
        viewModelScope.launch {
            val bankCredits = repository.getTotalUpiBankCredits().first() ?: 0.0
            val bankDebits = repository.getTotalUpiBankDebits().first() ?: 0.0
            val cashCredits = repository.getTotalCashCredits().first() ?: 0.0
            val cashDebits = repository.getTotalCashDebits().first() ?: 0.0
            appPrefs.setOpeningBankBalance(bankNow - (bankCredits - bankDebits))
            appPrefs.setOpeningCashBalance(cashNow - (cashCredits - cashDebits))
            appPrefs.markBalancesSetUp()
        }
    }

    /** Dismissed without entering anything; the prompt should not return. */
    fun skipBalanceSetup() = appPrefs.markBalancesSetUp()

    init {
        viewModelScope.launch {
            try {
                Log.d("MainViewModel", "Startup: Seeding default categories...")
                repository.seedDefaultCategories()
                repository.cleanupLegacyCategories()
                repository.seedDefaultPaymentMethods()

                Log.d("MainViewModel", "Startup: Scheduling background sync...")
                syncScheduler.scheduleSync()

                Log.d("MainViewModel", "Startup: Scheduling autopay checks...")
                autopayScheduler.scheduleDailyCheck()
                autopayScheduler.runNow()


                // Diagnostic log for friends (wrapped in secondary try-catch)
                launch {
                    try {
                        val friends = friendRepository.getAllFriends().first()
                        friends.forEach { friend ->
                            Log.d("ROOM_FRIEND_DEBUG", "ID: ${friend.id} | Name: ${friend.name} | Status: ${friend.syncStatus}")
                        }
                    } catch (e: Exception) {
                        Log.w("ROOM_FRIEND_DEBUG", "Diagnostic log failed", e)
                    }
                }
            } catch (e: Exception) {
                Log.e("MainViewModel", "Critical startup error", e)
            }
        }
    }

    val expenses: StateFlow<List<Expense>> = repository.getAllExpenses()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val uiState: StateFlow<DashboardUiState> = combine(
        repository.getAllExpenses().distinctUntilChanged(),
        repository.getTotalUpiBankCredits().distinctUntilChanged(),
        repository.getTotalUpiBankDebits().distinctUntilChanged(),
        repository.getTotalCashCredits().distinctUntilChanged(),
        repository.getTotalCashDebits().distinctUntilChanged(),
        appPrefs.openingBankBalance,
        appPrefs.openingCashBalance
    ) { flows ->
        try {
            val allExpenses = (flows[0] as? List<*>)?.filterIsInstance<Expense>() ?: emptyList()
            val bankCredits = flows[1] as? Double ?: 0.0
            val bankDebits = flows[2] as? Double ?: 0.0
            val cashCredits = flows[3] as? Double ?: 0.0
            val cashDebits = flows[4] as? Double ?: 0.0
            val opBank = flows[5] as? Double ?: 0.0
            val opCash = flows[6] as? Double ?: 0.0

            if (allExpenses.isEmpty()) {
                return@combine DashboardUiState.Empty
            }

            val now = LocalDate.now()
            val zoneId = ZoneId.systemDefault()
            val startOfWeek = now.with(TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY))
            val startOfMonth = now.with(TemporalAdjusters.firstDayOfMonth())

            var income = 0.0
            var expenseTotal = 0.0
            var todaySpending = 0.0
            var weekSpending = 0.0
            var monthSpending = 0.0
            val categoryMap = mutableMapOf<String, Double>()
            val creditByCategory = mutableMapOf<String, Double>()

            // Credit filed under a category linked across both sides (e.g. "Home") is money
            // coming back against that category, so the breakdown below nets it off — matching
            // how Reports presents the same figures.
            val linkedCategoryNames = repository.getCategories().first()
                .filter { it.isLinked }
                .map { it.name }
                .toSet()

            allExpenses.forEach { expense ->
                val amount = expense.amount
                val isDebit = expense.type == "Debit"
                val isTransfer = TransactionConstants.isNonSpendingCategory(expense.type, expense.category)
                val date = Instant.ofEpochMilli(expense.timestamp).atZone(zoneId).toLocalDate()
                val isThisMonth = !date.isBefore(startOfMonth) && !date.isAfter(now)

                if (isDebit) {
                    if (!isTransfer) {
                        // Dashboard's expense total & category breakdown reflect the current month only.
                        if (isThisMonth) {
                            expenseTotal += amount
                            categoryMap[expense.category] = categoryMap.getOrDefault(expense.category, 0.0) + amount
                            monthSpending += amount
                        }
                        if (date.isEqual(now)) {
                            todaySpending += amount
                        }
                        if (!date.isBefore(startOfWeek) && !date.isAfter(now)) {
                            weekSpending += amount
                        }
                    }
                } else {
                    if (!isTransfer && isThisMonth) {
                        income += amount
                        if (expense.category in linkedCategoryNames) {
                            creditByCategory[expense.category] =
                                creditByCategory.getOrDefault(expense.category, 0.0) + amount
                        }
                    }
                }
            }

            // 1. Balances
            val bankBalance = opBank + bankCredits - bankDebits
            val cashBalance = opCash + cashCredits - cashDebits
            val totalAssets = bankBalance + cashBalance

            // 7. Category Breakdown
            val topCategories = categoryMap
                .map { (cat, total) ->
                    // Clamped at zero: a category that earned back more than it cost shouldn't
                    // render as a negative slice of spending.
                    CategoryExpense(cat, (total - creditByCategory.getOrDefault(cat, 0.0)).coerceAtLeast(0.0))
                }
                .filter { it.totalAmount > 0.0 }
                .sortedByDescending { it.totalAmount }
                .take(5)

            // 8. Recent Transactions (Transfers excluded — they're a self-account movement, not
            // an expense/income event worth surfacing on the dashboard; still visible in History)
            val recentTransactions = allExpenses
                .filterNot { TransactionConstants.isNonSpendingCategory(it.type, it.category) }
                .take(5)

            // 9. Global Sync Status
            val isAllSynced = allExpenses.all { it.syncStatus == "Synced" }

            DashboardUiState.Success(
                DashboardData(
                    bankBalance = bankBalance,
                    cashBalance = cashBalance,
                    totalAssets = totalAssets,
                    openingBankBalance = opBank,
                    openingCashBalance = opCash,
                    income = income,
                    expense = expenseTotal,
                    todaySpending = todaySpending,
                    weekSpending = weekSpending,
                    monthSpending = monthSpending,
                    topCategories = topCategories,
                    recentTransactions = recentTransactions,
                    hasTransactions = true,
                    isAllSynced = isAllSynced
                )
            )
        } catch (e: Exception) {
            Log.e("MainViewModel", "UI State combination error", e)
            DashboardUiState.Empty
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = DashboardUiState.Loading
    )

    fun updateExpense(expense: Expense) {
        viewModelScope.launch {
            repository.updateExpense(expense)
        }
    }

    fun deleteExpense(expense: Expense) {
        viewModelScope.launch {
            repository.deleteExpense(expense)
        }
    }
}
