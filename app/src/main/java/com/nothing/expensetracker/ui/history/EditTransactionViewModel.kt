package com.nothing.expensetracker.ui.history

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nothing.expensetracker.data.local.Expense
import com.nothing.expensetracker.data.repository.ExpenseRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class EditTransactionViewModel @Inject constructor(
    private val repository: ExpenseRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val expenseId: Long = checkNotNull(savedStateHandle["expenseId"])

    private val _expense = MutableStateFlow<Expense?>(null)
    val expense: StateFlow<Expense?> = _expense.asStateFlow()

    init {
        viewModelScope.launch {
            repository.getExpenseById(expenseId).collect {
                _expense.value = it
            }
        }
    }

    fun getAllFriends() = repository.getAllFriends()

    fun updateExpense(updatedExpense: Expense) {
        viewModelScope.launch {
            repository.updateExpense(updatedExpense)
        }
    }
}
