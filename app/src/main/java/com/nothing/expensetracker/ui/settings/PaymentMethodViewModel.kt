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
                val oldName = method.name
                repository.updatePaymentMethod(oldName, method.copy(name = trimmedName))
                // Pushed here rather than left to the next sync: payment methods have no
                // syncStatus, so nothing in the queue would ever carry a rename - and the old
                // row has to go, or the sheet lists the account under both names and a restore
                // recreates the one that was renamed away.
                spreadsheetManager.deletePaymentMethodFromSheet(oldName)
                spreadsheetManager.syncPaymentMethodsToSheet()
                onResult(true, null)
            }
        }
    }

    /**
     * Deletes a payment method, including the ones that ship with the app.
     *
     * The old guard refused anything marked `isSystem`, because balances were once tracked
     * against the literal names "Bank", "UPI" and "Cash" in SQL. Accounts replaced that in
     * 3.8.0, so what decides this now is not where a method came from but what still depends
     * on it — which is checked below, and reported by name rather than as a flat refusal.
     */
    fun deletePaymentMethod(method: PaymentMethod, onResult: (Boolean, String?) -> Unit) {
        viewModelScope.launch {
            if (repository.getPaymentMethodCount() <= 1) {
                onResult(false, "At least one payment method must exist.")
                return@launch
            }

            val methods = repository.getPaymentMethodsOnce()

            // Deleting the last account would leave every transaction pointing at nothing, which
            // the dashboard can only report as unassigned: money with nowhere to sit.
            if (method.isAccount && methods.count { it.isAccount } <= 1) {
                onResult(false, "At least one account has to hold your money.")
                return@launch
            }

            // A rail whose account disappears becomes an account in its own right at a zero
            // balance, and spending that used to come off this one quietly stops doing so.
            val rails = methods.filter { it.fundedById == method.id }
            if (rails.isNotEmpty()) {
                onResult(false, railsBlockMessage(rails, method.name))
                return@launch
            }

            // A rule outlives the screen it was made on. Deleting the method underneath it
            // leaves it firing every month into a name nothing matches.
            val rules = repository.autopayRulesUsing(method.name)
            if (rules.isNotEmpty()) {
                onResult(false, autopayBlockMessage(rules.size, method.name))
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

/**
 * Why an account cannot go yet, named rather than merely refused.
 *
 * "Cannot delete" with no subject leaves the user hunting; naming the rail says exactly what to
 * change and where.
 */
internal fun autopayBlockMessage(count: Int, accountName: String): String {
    val rules = if (count == 1) "An autopay rule" else "$count autopay rules"
    val verb = if (count == 1) "uses" else "use"
    return "$rules $verb $accountName. Change " +
        (if (count == 1) "it" else "them") + " under Autopay first."
}

internal fun railsBlockMessage(rails: List<PaymentMethod>, accountName: String): String {
    // Kept to two lines: a Toast clips there, and it was clipping mid-instruction.
    val names = rails.joinToString(", ") { it.name }
    return if (rails.size == 1) {
        "$names draws on $accountName. Point it elsewhere first."
    } else {
        "$names draw on $accountName. Point them elsewhere first."
    }
}
