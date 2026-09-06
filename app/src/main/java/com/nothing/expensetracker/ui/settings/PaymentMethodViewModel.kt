package com.nothing.expensetracker.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nothing.expensetracker.data.local.PaymentMethod
import com.nothing.expensetracker.data.repository.ExpenseRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class PaymentMethodUiState(
    val methods: List<PaymentMethod> = emptyList(),
    val isLoading: Boolean = true
)

@HiltViewModel
class PaymentMethodViewModel @Inject constructor(
    private val repository: ExpenseRepository
) : ViewModel() {

    val uiState: StateFlow<PaymentMethodUiState> = repository.getPaymentMethods()
        .map { PaymentMethodUiState(it, false) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), PaymentMethodUiState())

    fun addPaymentMethod(name: String, onResult: (Boolean, String?) -> Unit) {
        val trimmedName = name.trim()
        if (trimmedName.isBlank()) {
            onResult(false, "Name cannot be empty")
            return
        }
        viewModelScope.launch {
            val existing = repository.getPaymentMethodByNameCaseInsensitive(trimmedName)
            if (existing != null) {
                onResult(false, "Payment method already exists.")
            } else {
                repository.insertPaymentMethod(PaymentMethod(name = trimmedName))
                onResult(true, null)
            }
        }
    }

    fun updatePaymentMethod(method: PaymentMethod, newName: String, onResult: (Boolean, String?) -> Unit) {
        if (method.isSystem) {
            onResult(false, "Default payment methods can't be renamed since balances are tracked against them.")
            return
        }
        val trimmedName = newName.trim()
        if (trimmedName.isBlank()) {
            onResult(false, "Name cannot be empty")
            return
        }
        viewModelScope.launch {
            val existing = repository.getPaymentMethodByNameCaseInsensitive(trimmedName)
            if (existing != null && existing.id != method.id) {
                onResult(false, "Payment method already exists.")
            } else {
                repository.updatePaymentMethod(method.name, method.copy(name = trimmedName))
                onResult(true, null)
            }
        }
    }

    fun deletePaymentMethod(method: PaymentMethod, onResult: (Boolean, String?) -> Unit) {
        if (method.isSystem) {
            onResult(false, "Default payment methods can't be deleted since balances are tracked against them.")
            return
        }
        viewModelScope.launch {
            if (repository.getPaymentMethodCount() <= 1) {
                onResult(false, "At least one payment method must exist.")
                return@launch
            }

            val usageCount = repository.getPaymentMethodUsageCount(method.name)
            if (usageCount == 0) {
                repository.deletePaymentMethod(method)
                onResult(true, null)
            } else {
                onResult(false, "IN_USE") // Special signal for the UI to show the complex dialog
            }
        }
    }

    fun moveTransactionsAndDelete(method: PaymentMethod, replacementName: String, onResult: (Boolean, String?) -> Unit) {
        viewModelScope.launch {
            repository.deletePaymentMethodAndMoveTransactions(method, replacementName)
            onResult(true, null)
        }
    }

    fun deleteTransactionsAndDelete(method: PaymentMethod, onResult: (Boolean, String?) -> Unit) {
        viewModelScope.launch {
            repository.deletePaymentMethodAndTransactions(method)
            onResult(true, null)
        }
    }
}
