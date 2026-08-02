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

    private val _uiEvent = MutableSharedFlow<UiEvent>()
    val uiEvent = _uiEvent.asSharedFlow()

    sealed class UiEvent {
        object Success : UiEvent()
        data class Info(val message: String) : UiEvent()
    }

    init {
        viewModelScope.launch {
            if (expenseId == 0L) {
                val firstCategory = repository.getAllCategories().first().firstOrNull() ?: "Other"
                _expense.value = Expense(
                    amount = 0.0,
                    description = "",
                    category = firstCategory,
                    type = "Debit",
                    paymentMethod = "UPI"
                )
            } else {
                repository.getExpenseById(expenseId).collect {
                    _expense.value = it
                }
            }
        }
    }

    fun getAllFriends() = repository.getAllFriends()

    fun getAllCategories() = repository.getAllCategories()

    fun updateExpense(updatedExpense: Expense) {
        viewModelScope.launch {
            val id = if (updatedExpense.id == 0L) {
                repository.insertExpense(updatedExpense)
            } else {
                repository.updateExpense(updatedExpense)
                updatedExpense.id
            }
            
            // Check if it was synced
            val latest = repository.getExpenseById(id).firstOrNull()
            if (latest?.syncStatus == "Synced") {
                _uiEvent.emit(UiEvent.Success)
            } else {
                _uiEvent.emit(UiEvent.Info("Transaction saved locally. It will automatically synchronize when internet becomes available."))
            }
        }
    }
}
