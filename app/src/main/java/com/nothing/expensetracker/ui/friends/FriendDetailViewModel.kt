package com.nothing.expensetracker.ui.friends

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nothing.expensetracker.data.local.Expense
import com.nothing.expensetracker.data.local.FriendBalance
import com.nothing.expensetracker.data.repository.ExpenseRepository
import com.nothing.expensetracker.data.repository.FriendRepository
import com.nothing.expensetracker.sync.SyncScheduler
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class FriendDetailUiState(
    val friendName: String = "",
    val balance: FriendBalance? = null,
    val transactions: List<Expense> = emptyList(),
    val isLoading: Boolean = true
)

@HiltViewModel
class FriendDetailViewModel @Inject constructor(
    private val repository: FriendRepository,
    private val expenseRepository: ExpenseRepository,
    private val syncScheduler: SyncScheduler,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val friendName: String = checkNotNull(savedStateHandle["friendName"])

    val uiState: StateFlow<FriendDetailUiState> = combine(
        repository.getFriendBalances(),
        repository.getTransactionsByFriend(friendName)
    ) { balances, transactions ->
        val balance = balances.find { it.friendName == friendName }
            ?: FriendBalance(friendName, 0.0, 0.0, 0.0)
            
        FriendDetailUiState(
            friendName = friendName,
            balance = balance,
            transactions = transactions,
            isLoading = false
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = FriendDetailUiState(friendName = friendName)
    )

    fun settleUp(amount: Double, paymentMethod: String, notes: String) {
        val currentBalance = uiState.value.balance?.outstandingBalance ?: 0.0
        if (currentBalance == 0.0) return

        val type = if (currentBalance > 0) "Credit" else "Debit"

        viewModelScope.launch {
            val expense = Expense(
                amount = amount,
                description = "Settlement: $friendName",
                category = if (type == "Credit") "Friend" else "Friends",
                type = type,
                paymentMethod = paymentMethod,
                friendId = friendName,
                notes = notes,
                timestamp = System.currentTimeMillis(),
                syncStatus = "Pending"
            )
            expenseRepository.insertExpense(expense)
            syncScheduler.scheduleSync()
        }
    }
}
