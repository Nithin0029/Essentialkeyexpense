package com.nothing.expensetracker.feature.autopay

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nothing.expensetracker.data.local.AutopayRule
import com.nothing.expensetracker.data.repository.AutopayRepository
import com.nothing.expensetracker.data.repository.ExpenseRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AddEditAutopayViewModel @Inject constructor(
    private val autopayRepository: AutopayRepository,
    private val expenseRepository: ExpenseRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val ruleId: Long = checkNotNull(savedStateHandle["ruleId"])

    private val _rule = MutableStateFlow<AutopayRule?>(null)
    val rule: StateFlow<AutopayRule?> = _rule.asStateFlow()

    private val _saved = MutableStateFlow(false)
    val saved: StateFlow<Boolean> = _saved.asStateFlow()

    init {
        viewModelScope.launch {
            if (ruleId == 0L) {
                val firstCategory = expenseRepository.getAllCategories().first().firstOrNull() ?: "Other"
                _rule.value = AutopayRule(
                    amount = 0.0,
                    category = firstCategory,
                    type = "Debit",
                    paymentMethod = "UPI",
                    dayOfMonth = 1
                )
            } else {
                _rule.value = autopayRepository.getAutopayRuleById(ruleId)
            }
        }
    }

    fun getAllFriends() = expenseRepository.getAllFriends()
    fun getAllCategories() = expenseRepository.getAllCategories()
    fun getAllPaymentMethods() = expenseRepository.getPaymentMethodNames()

    fun save(updatedRule: AutopayRule) {
        viewModelScope.launch {
            if (updatedRule.id == 0L) {
                autopayRepository.insertAutopayRule(updatedRule)
            } else {
                autopayRepository.updateAutopayRule(updatedRule)
            }
            _saved.value = true
        }
    }
}
