package com.nothing.expensetracker.ui.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nothing.expensetracker.data.local.Expense
import com.nothing.expensetracker.data.repository.ExpenseRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
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

@OptIn(ExperimentalCoroutinesApi::class, kotlinx.coroutines.FlowPreview::class)
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
        _searchQuery.debounce(300),
        _filterState,
        _sortOption
    ) { query, filters, sort ->
        Triple(query, filters, sort)
    }.flatMapLatest { (query, filters, sort) ->
        val timeRange = calculateTimeRange(filters.dateFilter)
        repository.getFilteredExpenses(
            query = query,
            type = filters.typeFilter,
            method = filters.methodFilter,
            category = filters.categoryFilter,
            sort = sort.name,
            startTime = timeRange.first,
            endTime = timeRange.second
        ).map { filtered ->
            val stats = calculateStatistics(filtered)
            HistoryUiState(
                expenses = filtered,
                statistics = stats,
                filterState = filters,
                sortOption = sort,
                searchQuery = query,
                isLoading = false
            )
        }
    }.distinctUntilChanged()
    .stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = HistoryUiState()
    )

    private fun calculateTimeRange(dateFilter: String): Pair<Long, Long> {
        val now = LocalDate.now()
        val zoneId = ZoneId.systemDefault()
        val startOfDay = now.atStartOfDay(zoneId).toInstant().toEpochMilli()
        val endOfDay = now.plusDays(1).atStartOfDay(zoneId).toInstant().toEpochMilli() - 1

        return when (dateFilter) {
            "Today" -> Pair(startOfDay, endOfDay)
            "Week" -> {
                val startOfWeek = now.with(TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY))
                    .atStartOfDay(zoneId).toInstant().toEpochMilli()
                Pair(startOfWeek, endOfDay)
            }
            "Month" -> {
                val startOfMonth = now.with(TemporalAdjusters.firstDayOfMonth())
                    .atStartOfDay(zoneId).toInstant().toEpochMilli()
                Pair(startOfMonth, endOfDay)
            }
            "Year" -> {
                val startOfYear = now.with(TemporalAdjusters.firstDayOfYear())
                    .atStartOfDay(zoneId).toInstant().toEpochMilli()
                Pair(startOfYear, endOfDay)
            }
            else -> Pair(0L, Long.MAX_VALUE)
        }
    }

    private fun calculateStatistics(expenses: List<Expense>): HistoryStatistics {
        var income = 0.0
        var expense = 0.0
        expenses.forEach { 
            if (it.type == "Credit") income += it.amount 
            else expense += it.amount 
        }
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
