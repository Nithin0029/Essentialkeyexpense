package com.nothing.expensetracker.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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
    val currentBalance: Double = 0.0,
    val income: Double = 0.0,
    val expense: Double = 0.0,
    val todaySpending: Double = 0.0,
    val weekSpending: Double = 0.0,
    val monthSpending: Double = 0.0,
    val topCategories: List<CategoryExpense> = emptyList(),
    val recentTransactions: List<Expense> = emptyList(),
    val hasTransactions: Boolean = false
)

sealed class DashboardUiState {
    object Loading : DashboardUiState()
    data class Success(val data: DashboardData) : DashboardUiState()
    object Empty : DashboardUiState()
}

@HiltViewModel
class MainViewModel @Inject constructor(
    private val repository: ExpenseRepository
) : ViewModel() {

    private val OPENING_BALANCE = 15000.0

    private val _selectedMonth = MutableStateFlow(SimpleDateFormat("MM", Locale.getDefault()).format(Date()))
    val selectedMonth: StateFlow<String> = _selectedMonth

    private val _selectedYear = MutableStateFlow(SimpleDateFormat("yyyy", Locale.getDefault()).format(Date()))
    val selectedYear: StateFlow<String> = _selectedYear

    val expenses: StateFlow<List<Expense>> = repository.getAllExpenses()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val uiState: StateFlow<DashboardUiState> = combine(
        repository.getAllExpenses(),
        repository.getTotalUpiBankCredits(),
        repository.getTotalUpiBankDebits()
    ) { allExpenses, credits, debits ->
        if (allExpenses.isEmpty()) {
            return@combine DashboardUiState.Empty
        }

        val now = LocalDate.now()
        val zoneId = ZoneId.systemDefault()

        // 1. Current Balance
        val currentBalance = OPENING_BALANCE + (credits ?: 0.0) - (debits ?: 0.0)

        // 2. Income
        val income = allExpenses.filter { it.type == "Credit" }.sumOf { it.amount }

        // 3. Expense
        val expenseTotal = allExpenses.filter { it.type == "Debit" }.sumOf { it.amount }

        // 4. Today
        val todaySpending = allExpenses.filter {
            it.type == "Debit" && 
            Instant.ofEpochMilli(it.timestamp).atZone(zoneId).toLocalDate().isEqual(now)
        }.sumOf { it.amount }

        // 5. Week
        val startOfWeek = now.with(TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY))
        val weekSpending = allExpenses.filter {
            val date = Instant.ofEpochMilli(it.timestamp).atZone(zoneId).toLocalDate()
            it.type == "Debit" && (date.isEqual(startOfWeek) || date.isAfter(startOfWeek)) && (date.isEqual(now) || date.isBefore(now))
        }.sumOf { it.amount }

        // 6. Month
        val startOfMonth = now.with(TemporalAdjusters.firstDayOfMonth())
        val monthSpending = allExpenses.filter {
            val date = Instant.ofEpochMilli(it.timestamp).atZone(zoneId).toLocalDate()
            it.type == "Debit" && (date.isEqual(startOfMonth) || date.isAfter(startOfMonth)) && (date.isEqual(now) || date.isBefore(now))
        }.sumOf { it.amount }

        // 7. Category Breakdown
        val topCategories = allExpenses.filter { it.type == "Debit" }
            .groupBy { it.category }
            .map { (category, expenses) -> CategoryExpense(category, expenses.sumOf { it.amount }) }
            .sortedByDescending { it.totalAmount }
            .take(5)

        // 8. Recent Transactions
        val recentTransactions = allExpenses.take(5)

        DashboardUiState.Success(
            DashboardData(
                currentBalance = currentBalance,
                income = income,
                expense = expenseTotal,
                todaySpending = todaySpending,
                weekSpending = weekSpending,
                monthSpending = monthSpending,
                topCategories = topCategories,
                recentTransactions = recentTransactions,
                hasTransactions = true
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
