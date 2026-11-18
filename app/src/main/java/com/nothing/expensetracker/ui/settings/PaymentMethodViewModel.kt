package com.nothing.expensetracker.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nothing.expensetracker.data.local.PaymentMethod
import com.nothing.expensetracker.data.repository.ExpenseRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import com.nothing.expensetracker.data.repository.rollUpAccountBalances
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import com.nothing.expensetracker.sync.SpreadsheetManager
import javax.inject.Inject

data class PaymentMethodUiState(
    val methods: List<PaymentMethod> = emptyList(),
    /** Current balance per account id, so each account row can show what it holds. */
    val balances: Map<Long, Double> = emptyMap(),
    val isLoading: Boolean = true
) {
    /** The methods that hold money, which are the only ones a balance can be set on. */
    val accounts: List<PaymentMethod> get() = methods.filter { it.isAccount }

    fun nameOf(id: Long?): String? = id?.let { methods.find { m -> m.id == it }?.name }
}

@HiltViewModel
class PaymentMethodViewModel @Inject constructor(
    private val repository: ExpenseRepository,
    private val spreadsheetManager: SpreadsheetManager
) : ViewModel() {

    val uiState: StateFlow<PaymentMethodUiState> = combine(
        repository.getPaymentMethods(),
        repository.getTotalsByPaymentMethod()
    ) { methods, totals ->
        val rollUp = rollUpAccountBalances(methods, totals)
        PaymentMethodUiState(
            methods = methods,
            balances = rollUp.accounts.associate { it.id to it.balance },
            isLoading = false
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), PaymentMethodUiState())

    /**
     * Sets what an account holds today.
     *
     * The opening balance is solved for rather than typed, because today's figure is the one a
     * user can actually read off their banking app.
     */
    fun setBalanceToday(method: PaymentMethod, balanceToday: Double, onResult: (Boolean, String?) -> Unit) {
        viewModelScope.launch {
            if (!method.isAccount) {
                onResult(false, "${method.name} draws on another account, so it holds no balance of its own.")
                return@launch
            }
            val ok = repository.setAccountBalanceToday(method.id, balanceToday)
            onResult(ok, if (ok) null else "Could not update ${method.name}.")
        }
    }

    /** Points a method at the account it spends from, or makes it hold money in its own right. */
    fun setFundingAccount(method: PaymentMethod, fundedById: Long?) {
        viewModelScope.launch {
            if (fundedById == method.id) return@launch
            repository.setFundingAccount(method, fundedById)
        }
    }

    fun addPaymentMethod(
        name: String,
        fundedById: Long? = null,
        /** What the new account holds today. Ignored when it draws on another account. */
        balanceToday: Double = 0.0,
        onResult: (Boolean, String?) -> Unit
    ) {
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
                // A new account has no history, so what it holds today *is* its opening balance.
                repository.insertPaymentMethod(
                    PaymentMethod(
                        name = trimmedName,
                        fundedById = fundedById,
                        openingBalance = if (fundedById == null) balanceToday else 0.0
                    )
                )
                onResult(true, null)
            }
        }
    }

    /**
     * Renames a payment method, including the ones that ship with the app.
     *
     * Renaming used to be blocked for those because balances were tracked against the literal
     * names "Bank", "UPI" and "Cash" in SQL. They are tracked against account rows now, and the
     * rename rewrites every transaction's method in the same step, so "Bank" can become
     * "HDFC Bank" without a balance moving.
     */
    fun updatePaymentMethod(method: PaymentMethod, newName: String, onResult: (Boolean, String?) -> Unit) {
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
                // Payment methods carry no syncStatus, so the removal has to be pushed here
                // rather than queued for the next sync cycle.
                spreadsheetManager.deletePaymentMethodFromSheet(method.name)
                onResult(true, null)
            } else {
                onResult(false, "IN_USE") // Special signal for the UI to show the complex dialog
            }
        }
    }

    fun restorePaymentMethod(method: PaymentMethod) {
        viewModelScope.launch {
            repository.updatePaymentMethod(method.name, method)
        }
    }

    fun moveTransactionsAndDelete(method: PaymentMethod, replacementName: String, onResult: (Boolean, String?) -> Unit) {
        viewModelScope.launch {
            repository.deletePaymentMethodAndMoveTransactions(method, replacementName)
            spreadsheetManager.deletePaymentMethodFromSheet(method.name)
            onResult(true, null)
        }
    }

    fun deleteTransactionsAndDelete(method: PaymentMethod, onResult: (Boolean, String?) -> Unit) {
        viewModelScope.launch {
            repository.deletePaymentMethodAndTransactions(method)
            spreadsheetManager.deletePaymentMethodFromSheet(method.name)
            onResult(true, null)
        }
    }
}
