package com.nothing.expensetracker.ui.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nothing.expensetracker.data.local.Expense
import com.nothing.expensetracker.data.repository.ExpenseRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters
import javax.inject.Inject

enum class SortOption(val label: String) {
    NEWEST("Newest First"),
    OLDEST("Oldest First"),
    HIGHEST_AMOUNT("Highest Amount"),
    LOWEST_AMOUNT("Lowest Amount"),
    CATEGORY_AZ("Category A-Z"),
    CATEGORY_ZA("Category Z-A")
}

data class HistoryFilterState(
    val dateFilter: String = "All",
    val typeFilter: String = "All",
    val methodFilter: String = "All",
    val categoryFilter: String = "All"
)

data class HistoryStatistics(
    val count: Int = 0,
    val totalIncome: Double = 0.0,
    val totalExpense: Double = 0.0
)

data class HistoryUiState(
    val expenses: List<Expense> = emptyList(),
    val statistics: HistoryStatistics = HistoryStatistics(),
    val filterState: HistoryFilterState = HistoryFilterState(),
    val sortOption: SortOption = SortOption.NEWEST,
    val searchQuery: String = "",
    val isLoading: Boolean = true
)

@HiltViewModel
class HistoryViewModel @Inject constructor(
    private val repository: ExpenseRepository
) : ViewModel() {

    private val _searchQuery = MutableStateFlow("")
    private val _filterState = MutableStateFlow(HistoryFilterState())
    private val _sortOption = MutableStateFlow(SortOption.NEWEST)

    val categories: StateFlow<List<String>> = repository.getAllCategories()
        .map { listOf("All") + it }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), listOf("All"))

    val uiState: StateFlow<HistoryUiState> = combine(
        repository.getAllExpenses(),
        _searchQuery,
        _filterState,
        _sortOption
    ) { allExpenses, query, filters, sort ->
        val filtered = filterExpenses(allExpenses, query, filters)
        val sorted = sortExpenses(filtered, sort)
        val stats = calculateStatistics(filtered)

        HistoryUiState(
            expenses = sorted,
            statistics = stats,
            filterState = filters,
            sortOption = sort,
            searchQuery = query,
            isLoading = false
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = HistoryUiState()
    )

    private fun filterExpenses(
        expenses: List<Expense>,
        query: String,
        filters: HistoryFilterState
    ): List<Expense> {
        return expenses.filter { expense ->
            val matchesQuery = if (query.isBlank()) true else {
                expense.category.contains(query, ignoreCase = true) ||
                expense.notes.contains(query, ignoreCase = true) ||
                expense.friendId?.contains(query, ignoreCase = true) == true ||
                expense.paymentMethod.contains(query, ignoreCase = true)
            }
            val matchesType = if (filters.typeFilter == "All") true else expense.type == filters.typeFilter
            val matchesMethod = if (filters.methodFilter == "All") true else expense.paymentMethod == filters.methodFilter
            val matchesCategory = if (filters.categoryFilter == "All") true else expense.category == filters.categoryFilter
            val matchesDate = if (filters.dateFilter == "All") true else {
                val expenseDate = Instant.ofEpochMilli(expense.timestamp).atZone(ZoneId.systemDefault()).toLocalDate()
                val now = LocalDate.now()
                when (filters.dateFilter) {
                    "Today" -> expenseDate.isEqual(now)
                    "Week" -> {
                        val startOfWeek = now.with(TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY))
                        !expenseDate.isBefore(startOfWeek) && !expenseDate.isAfter(now)
                    }
                    "Month" -> {
                        val startOfMonth = now.with(TemporalAdjusters.firstDayOfMonth())
                        !expenseDate.isBefore(startOfMonth) && !expenseDate.isAfter(now)
                    }
                    "Year" -> {
                        val startOfYear = now.with(TemporalAdjusters.firstDayOfYear())
                        !expenseDate.isBefore(startOfYear) && !expenseDate.isAfter(now)
                    }
                    else -> true
                }
            }
            matchesQuery && matchesType && matchesMethod && matchesCategory && matchesDate
        }
    }

    private fun sortExpenses(expenses: List<Expense>, sort: SortOption): List<Expense> {
        return when (sort) {
            SortOption.NEWEST -> expenses.sortedByDescending { it.timestamp }
            SortOption.OLDEST -> expenses.sortedBy { it.timestamp }
            SortOption.HIGHEST_AMOUNT -> expenses.sortedByDescending { it.amount }
            SortOption.LOWEST_AMOUNT -> expenses.sortedBy { it.amount }
            SortOption.CATEGORY_AZ -> expenses.sortedBy { it.category }
            SortOption.CATEGORY_ZA -> expenses.sortedByDescending { it.category }
        }
    }

    private fun calculateStatistics(expenses: List<Expense>): HistoryStatistics {
        val income = expenses.filter { it.type == "Credit" }.sumOf { it.amount }
        val expense = expenses.filter { it.type == "Debit" }.sumOf { it.amount }
        return HistoryStatistics(
            count = expenses.size,
            totalIncome = income,
            totalExpense = expense
        )
    }

    fun onSearchQueryChange(newQuery: String) {
        _searchQuery.value = newQuery
    }

    fun updateSortOption(option: SortOption) {
        _sortOption.value = option
    }

    fun updateDateFilter(filter: String) {
        _filterState.update { it.copy(dateFilter = filter) }
    }

    fun updateTypeFilter(filter: String) {
        _filterState.update { it.copy(typeFilter = filter) }
    }

    fun updateMethodFilter(filter: String) {
        _filterState.update { it.copy(methodFilter = filter) }
    }

    fun updateCategoryFilter(filter: String) {
        _filterState.update { it.copy(categoryFilter = filter) }
    }

    fun applyFilters(filters: HistoryFilterState) {
        _filterState.value = filters
    }

    fun deleteExpense(expense: Expense) {
        viewModelScope.launch {
            repository.deleteExpense(expense)
        }
    }
}
