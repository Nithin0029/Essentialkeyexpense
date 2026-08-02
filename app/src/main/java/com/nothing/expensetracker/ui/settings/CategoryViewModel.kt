package com.nothing.expensetracker.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nothing.expensetracker.data.local.Category
import com.nothing.expensetracker.data.repository.ExpenseRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class CategoryUiState(
    val categories: List<Category> = emptyList(),
    val isLoading: Boolean = true
)

@HiltViewModel
class CategoryViewModel @Inject constructor(
    private val repository: ExpenseRepository
) : ViewModel() {

    val uiState: StateFlow<CategoryUiState> = repository.getCategories()
        .map { CategoryUiState(it, false) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), CategoryUiState())

    fun addCategory(name: String, onResult: (Boolean, String?) -> Unit) {
        val trimmedName = name.trim()
        if (trimmedName.isBlank()) {
            onResult(false, "Name cannot be empty")
            return
        }
        viewModelScope.launch {
            val existing = repository.getCategoryByNameCaseInsensitive(trimmedName)
            if (existing != null) {
                onResult(false, "Category already exists.")
            } else {
                repository.insertCategory(Category(name = trimmedName))
                onResult(true, null)
            }
        }
    }

    fun updateCategory(category: Category, newName: String, onResult: (Boolean, String?) -> Unit) {
        val trimmedName = newName.trim()
        if (trimmedName.isBlank()) {
            onResult(false, "Name cannot be empty")
            return
        }
        viewModelScope.launch {
            val existing = repository.getCategoryByNameCaseInsensitive(trimmedName)
            if (existing != null && existing.id != category.id) {
                onResult(false, "Category already exists.")
            } else {
                repository.updateCategory(category.name, category.copy(name = trimmedName))
                onResult(true, null)
            }
        }
    }

    fun deleteCategory(category: Category, onResult: (Boolean, String?) -> Unit) {
        if (category.name == "Friends") {
            onResult(false, "The 'Friends' category cannot be deleted as it is required by the app.")
            return
        }
        
        viewModelScope.launch {
            if (repository.getCategoryCount() <= 1) {
                onResult(false, "At least one category must exist.")
                return@launch
            }

            val usageCount = repository.getCategoryUsageCount(category.name)
            if (usageCount == 0) {
                repository.deleteCategory(category)
                onResult(true, null)
            } else {
                onResult(false, "IN_USE") // Special signal for the UI to show the complex dialog
            }
        }
    }

    fun moveTransactionsAndDelete(category: Category, replacementName: String, onResult: (Boolean, String?) -> Unit) {
        viewModelScope.launch {
            repository.deleteCategoryAndMoveTransactions(category, replacementName)
            onResult(true, null)
        }
    }

    fun deleteTransactionsAndDelete(category: Category, onResult: (Boolean, String?) -> Unit) {
        viewModelScope.launch {
            repository.deleteCategoryAndTransactions(category)
            onResult(true, null)
        }
    }
}
