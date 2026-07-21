package com.nothing.expensetracker.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nothing.expensetracker.data.local.CategoryExpense
import com.nothing.expensetracker.data.local.Expense
import com.nothing.expensetracker.data.repository.ExpenseRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(
    private val repository: ExpenseRepository
) : ViewModel() {

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

    val categoryExpenses: StateFlow<List<CategoryExpense>> = combine(_selectedMonth, _selectedYear) { month, year ->
        month to year
    }.flatMapLatest { (month, year) ->
        repository.getExpensesByCategoryFiltered(month, year)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    fun updateMonth(month: String) {
        _selectedMonth.value = month
    }

    fun updateYear(year: String) {
        _selectedYear.value = year
    }

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
