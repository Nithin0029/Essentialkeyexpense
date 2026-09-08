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
    private val syncScheduler: com.nothing.expensetracker.sync.SyncScheduler,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val expenseId: Long = checkNotNull(savedStateHandle["expenseId"])
    private val initialTimestamp: Long = savedStateHandle["initialTimestamp"] ?: 0L

    private val _expense = MutableStateFlow<Expense?>(null)
    val expense: StateFlow<Expense?> = _expense.asStateFlow()

    private val _uiState = MutableStateFlow(EditTransactionUiState())
    val uiState = _uiState.asStateFlow()

    private val _uiEvent = MutableSharedFlow<UiEvent>()
    val uiEvent = _uiEvent.asSharedFlow()

    data class EditTransactionUiState(
        val isSaving: Boolean = false
    )

    sealed class UiEvent {
        object Success : UiEvent()
        data class Info(val message: String) : UiEvent()
    }

    init {
        viewModelScope.launch {
            if (expenseId == 0L) {
                val firstCategory = repository.getCategories().first()
                    .firstOrNull { it.parentId == null }?.name ?: "Other"
                _expense.value = Expense(
                    amount = 0.0,
                    description = "",
                    category = firstCategory,
                    type = "Debit",
                    paymentMethod = "UPI",
                    timestamp = if (initialTimestamp > 0L) initialTimestamp else System.currentTimeMillis()
                )
            } else {
                repository.getExpenseById(expenseId).collect {
                    _expense.value = it
                }
            }
        }
    }

    fun getAllFriends() = repository.getAllFriends()

    /** Full category rows (including parentId) so the picker can group subcategories under
     *  their parent instead of showing one flat list. */
    fun getCategories() = repository.getCategories()

    fun getAllPaymentMethods() = repository.getPaymentMethodNames()

    fun updateExpense(updatedExpense: Expense) {
        if (_uiState.value.isSaving) return
        _uiState.value = _uiState.value.copy(isSaving = true)

        viewModelScope.launch {
            try {
                if (updatedExpense.id == 0L) {
                    repository.insertExpense(updatedExpense)
                } else {
                    repository.updateExpense(updatedExpense)
                }
                
                // Professional Sync: Just schedule and notify
                syncScheduler.scheduleSync()
                _uiEvent.emit(UiEvent.Info("Transaction saved locally and queued for sync."))
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isSaving = false)
                _uiEvent.emit(UiEvent.Info("Error saving transaction: ${e.message}"))
            }
        }
    }
}
