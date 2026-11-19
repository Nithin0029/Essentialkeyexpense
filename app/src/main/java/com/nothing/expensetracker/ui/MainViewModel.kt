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
import com.nothing.expensetracker.data.repository.AccountBalance
import com.nothing.expensetracker.data.repository.rollUpAccountBalances
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters
import java.util.*
import javax.inject.Inject

data class DashboardData(
    /**
     * Every account that holds money, each already including whatever draws on it. Replaces the
     * old fixed pair of bank and cash figures, which could not describe a second bank account.
     */
    val accounts: List<AccountBalance> = emptyList(),
    val totalAssets: Double = 0.0,
    /** Movement on a payment method that no longer exists; non-zero means the totals are short. */
    val unassignedNet: Double = 0.0,
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
     * Every account that holds money, for the first-run prompt to ask about by name.
     *
     * Read separately from [uiState] because the prompt shows over the loading and empty states
     * too, where there is no dashboard data to take the accounts from.
     */
    val accounts: StateFlow<List<AccountBalance>> = combine(
        repository.getAllPaymentMethods().distinctUntilChanged(),
        repository.getTotalsByPaymentMethod().distinctUntilChanged()
    ) { methods, totals ->
        rollUpAccountBalances(methods, totals).accounts
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /**
     * Records the balances the user is holding right now, one account at a time.
     *
     * Solves for the opening balance rather than storing the figure directly, so the number is
     * correct whether this is a brand-new install with no transactions (where the two are the
     * same) or one that has just restored a history from the spreadsheet.
     *
     * Keyed by account id. It used to write to the names "Bank" and "Cash", which returned false
     * without a word once an account had been renamed — so everything the user typed was thrown
     * away while setup was still recorded as finished.
     */
    fun completeBalanceSetup(balances: Map<Long, Double>) {
        viewModelScope.launch {
            balances.forEach { (id, balance) ->
                repository.setAccountBalanceToday(id, balance)
            }
            appPrefs.markBalancesSetUp()
            // The accounts own these numbers now, so the one-shot copy out of preferences must
            // not run later and overwrite what the user just typed.
            appPrefs.markOpeningBalancesMigrated()
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

                // Opening balances predate accounts and live in preferences, where no Room
                // migration can reach them. Carry them over once, after seeding has guaranteed
                // the accounts exist.
                if (!appPrefs.hasMigratedOpeningBalances()) {
                    repository.backfillAccountOpeningBalances(
                        bank = appPrefs.openingBankBalance.value,
                        cash = appPrefs.openingCashBalance.value
                    )
                    appPrefs.markOpeningBalancesMigrated()
                }

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
        // Balances come from the accounts now rather than three hardcoded method names, so a
        // second bank account is just another row instead of money with nowhere to go.
        repository.getAllPaymentMethods().distinctUntilChanged(),
        repository.getTotalsByPaymentMethod().distinctUntilChanged()
    ) { allExpenses, paymentMethods, methodTotals ->
        try {
            val rollUp = rollUpAccountBalances(paymentMethods, methodTotals)

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
                    accounts = rollUp.accounts,
                    totalAssets = rollUp.totalAssets,
                    unassignedNet = rollUp.unassignedNet,
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
