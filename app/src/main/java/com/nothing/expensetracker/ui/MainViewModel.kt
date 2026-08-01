package com.nothing.expensetracker.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nothing.expensetracker.data.local.AppPrefs
import com.nothing.expensetracker.data.local.CategoryExpense
import com.nothing.expensetracker.data.local.Expense
import com.nothing.expensetracker.data.repository.ExpenseRepository
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
    private val appPrefs: AppPrefs,
    private val syncScheduler: com.nothing.expensetracker.sync.SyncScheduler
) : ViewModel() {

    private val _selectedMonth = MutableStateFlow(SimpleDateFormat("MM", Locale.getDefault()).format(Date()))
    val selectedMonth: StateFlow<String> = _selectedMonth

    private val _selectedYear = MutableStateFlow(SimpleDateFormat("yyyy", Locale.getDefault()).format(Date()))
    val selectedYear: StateFlow<String> = _selectedYear

    init {
        viewModelScope.launch {
            repository.seedDefaultCategories()
            syncScheduler.scheduleSync() // Trigger sync for any pending offline transactions
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

        allExpenses.forEach { expense ->
            val amount = expense.amount
            val isDebit = expense.type == "Debit"
            
            if (isDebit) {
                expenseTotal += amount
                categoryMap[expense.category] = categoryMap.getOrDefault(expense.category, 0.0) + amount
                
                val date = Instant.ofEpochMilli(expense.timestamp).atZone(zoneId).toLocalDate()
                if (date.isEqual(now)) {
                    todaySpending += amount
                }
                if (!date.isBefore(startOfWeek) && !date.isAfter(now)) {
                    weekSpending += amount
                }
                if (!date.isBefore(startOfMonth) && !date.isAfter(now)) {
                    monthSpending += amount
                }
            } else {
                income += amount
            }
        }

        // 1. Balances
        val bankBalance = opBank + bankCredits - bankDebits
        val cashBalance = opCash + cashCredits - cashDebits
        val totalAssets = bankBalance + cashBalance

        // 7. Category Breakdown
        val topCategories = categoryMap.map { (cat, total) -> CategoryExpense(cat, total) }
            .sortedByDescending { it.totalAmount }
            .take(5)

        // 8. Recent Transactions
        val recentTransactions = allExpenses.take(5)

        // 9. Global Sync Status
        val isAllSynced = allExpenses.all { it.isSynced }

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
