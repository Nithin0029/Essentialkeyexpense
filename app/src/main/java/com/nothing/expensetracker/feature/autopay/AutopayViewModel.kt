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
import com.nothing.expensetracker.sync.SpreadsheetManager
import java.time.LocalDate
import javax.inject.Inject

data class AutopayListUiState(
    val rules: List<AutopayRule> = emptyList(),
    val isLoading: Boolean = true
)

@HiltViewModel
class AutopayViewModel @Inject constructor(
    private val repository: AutopayRepository,
    private val spreadsheetManager: SpreadsheetManager
) : ViewModel() {

    val uiState: StateFlow<AutopayListUiState> = repository.getAllAutopayRules()
        .map { AutopayListUiState(it, false) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AutopayListUiState())

    fun setActive(rule: AutopayRule, active: Boolean) {
        viewModelScope.launch {
            val updated = if (active && !rule.isActive) {
                // Switching a rule back on resumes it from now. Without this the catch-up would
                // read every month it spent switched off as a month it owes, and create them all
                // at once - a rule paused in January would bill ten months in November.
                rule.copy(
                    isActive = true,
                    lastRunMonth = resumeMonthKey(rule.lastRunMonth, LocalDate.now())
                )
            } else {
                rule.copy(isActive = active)
            }
            repository.updateAutopayRule(updated)
        }
    }

    fun deleteRule(rule: AutopayRule) {
        viewModelScope.launch {
            repository.deleteAutopayRule(rule)
            // No syncStatus on autopay rules either, so push the removal immediately.
            spreadsheetManager.deleteAutopayRuleFromSheet(rule.id)
        }
    }

    fun restoreRule(rule: AutopayRule) {
        viewModelScope.launch {
            repository.insertAutopayRule(rule)
        }
    }
}
