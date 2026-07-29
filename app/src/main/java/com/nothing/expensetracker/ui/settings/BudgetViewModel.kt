package com.nothing.expensetracker.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nothing.expensetracker.data.local.Budget
import com.nothing.expensetracker.data.local.Expense
import com.nothing.expensetracker.data.repository.ExpenseRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.util.*
import javax.inject.Inject

data class BudgetUsage(
    val budget: Budget?,
    val spent: Double,
    val remaining: Double,
    val percentage: Float
)

data class CategoryBudgetUsage(
    val categoryName: String,
    val budget: Budget?,
    val spent: Double,
    val remaining: Double,
    val percentage: Float
)

data class BudgetUiState(
    val overallUsage: BudgetUsage = BudgetUsage(null, 0.0, 0.0, 0f),
    val categoryUsages: List<CategoryBudgetUsage> = emptyList(),
    val categories: List<String> = emptyList(),
    val isLoading: Boolean = true
)

@HiltViewModel
class BudgetViewModel @Inject constructor(
    private val repository: ExpenseRepository
) : ViewModel() {

    private val now = LocalDate.now()
    private val currentMonth = now.monthValue
    private val currentYear = now.year

    val uiState: StateFlow<BudgetUiState> = combine(
        repository.getOverallBudget(currentMonth, currentYear),
        repository.getCategoryBudgets(currentMonth, currentYear),
        repository.getAllExpenses(),
        repository.getAllCategories()
    ) { overallBudget, categoryBudgets, allExpenses, categories ->
        
        val monthExpenses = allExpenses.filter { expense ->
            val date = java.time.Instant.ofEpochMilli(expense.timestamp)
                .atZone(java.time.ZoneId.systemDefault())
                .toLocalDate()
            date.monthValue == currentMonth && date.year == currentYear && expense.type == "Debit"
        }

        val totalSpent = monthExpenses.sumOf { it.amount }
        val overallUsage = calculateUsage(overallBudget, totalSpent)

        val categoryUsages = categories.map { catName ->
            val budget = categoryBudgets.find { it.categoryName == catName }
            val spent = monthExpenses.filter { it.category == catName }.sumOf { it.amount }
            calculateCategoryUsage(catName, budget, spent)
        }

        BudgetUiState(
            overallUsage = overallUsage,
            categoryUsages = categoryUsages,
            categories = categories,
            isLoading = false
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), BudgetUiState())

    private fun calculateUsage(budget: Budget?, spent: Double): BudgetUsage {
        val limit = budget?.amount ?: 0.0
        val remaining = (limit - spent).coerceAtLeast(0.0)
        val percentage = if (limit > 0) (spent / limit).toFloat().coerceIn(0f, 1f) else 0f
        return BudgetUsage(budget, spent, remaining, percentage)
    }

    private fun calculateCategoryUsage(name: String, budget: Budget?, spent: Double): CategoryBudgetUsage {
        val limit = budget?.amount ?: 0.0
        val remaining = (limit - spent).coerceAtLeast(0.0)
        val percentage = if (limit > 0) (spent / limit).toFloat().coerceIn(0f, 1f) else 0f
        return CategoryBudgetUsage(name, budget, spent, remaining, percentage)
    }

    fun setOverallBudget(amount: Double) {
        viewModelScope.launch {
            repository.insertBudget(Budget(
                categoryName = null,
                amount = amount,
                month = currentMonth,
                year = currentYear
            ))
        }
    }

    fun setCategoryBudget(categoryName: String, amount: Double) {
        viewModelScope.launch {
            repository.insertBudget(Budget(
                categoryName = categoryName,
                amount = amount,
                month = currentMonth,
                year = currentYear
            ))
        }
    }

    fun deleteBudget(budget: Budget) {
        viewModelScope.launch {
            repository.deleteBudget(budget)
        }
    }
}
