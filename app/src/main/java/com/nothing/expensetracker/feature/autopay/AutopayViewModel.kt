package com.nothing.expensetracker.feature.autopay

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nothing.expensetracker.data.local.AutopayRule
import com.nothing.expensetracker.data.repository.AutopayRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AutopayListUiState(
    val rules: List<AutopayRule> = emptyList(),
    val isLoading: Boolean = true
)

@HiltViewModel
class AutopayViewModel @Inject constructor(
    private val repository: AutopayRepository
) : ViewModel() {

    val uiState: StateFlow<AutopayListUiState> = repository.getAllAutopayRules()
        .map { AutopayListUiState(it, false) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AutopayListUiState())

    fun setActive(rule: AutopayRule, active: Boolean) {
        viewModelScope.launch {
            repository.updateAutopayRule(rule.copy(isActive = active))
        }
    }

    fun deleteRule(rule: AutopayRule) {
        viewModelScope.launch {
            repository.deleteAutopayRule(rule)
        }
    }
}
