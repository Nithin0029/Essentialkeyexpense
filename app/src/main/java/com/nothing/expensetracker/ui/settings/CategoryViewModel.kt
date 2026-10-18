package com.nothing.expensetracker.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nothing.expensetracker.data.local.Category
import com.nothing.expensetracker.data.repository.ExpenseRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Which side of the ledger the Category Management screen is currently showing. */
enum class CategoryScope(val label: String, val transactionType: String) {
    DEBIT("Debit", "Debit"),
    CREDIT("Credit", "Credit")
}

data class CategoryUiState(
    val categories: List<Category> = emptyList(),
    val isLoading: Boolean = true,
    val scope: CategoryScope = CategoryScope.DEBIT
) {
    /** Top-level categories on the currently selected side. Credit has no subcategories. */
    val topLevelCategories: List<Category>
        get() = categories.filter { it.parentId == null && it.isInScope(scope) }

    fun subcategoriesOf(parentId: Long): List<Category> =
        categories.filter { it.parentId == parentId && it.isInScope(scope) }

    /** Top-level categories on the *other* side, offered as link targets when adding. */
    fun linkableCandidates(): List<Category> = categories.filter {
        it.parentId == null && !it.isInScope(scope) && it.isInScope(otherScope())
    }

    private fun otherScope() =
        if (scope == CategoryScope.DEBIT) CategoryScope.CREDIT else CategoryScope.DEBIT

    private fun Category.isInScope(scope: CategoryScope) =
        if (scope == CategoryScope.DEBIT) usedForDebit else usedForCredit
}

@HiltViewModel
class CategoryViewModel @Inject constructor(
    private val repository: ExpenseRepository
) : ViewModel() {

    private val _scope = MutableStateFlow(CategoryScope.DEBIT)

    val uiState: StateFlow<CategoryUiState> =
        combine(repository.getCategories(), _scope) { categories, scope ->
            CategoryUiState(categories, isLoading = false, scope = scope)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), CategoryUiState())

    fun setScope(scope: CategoryScope) {
        _scope.value = scope
    }

    /**
     * Adds [name] to the currently selected side of the ledger.
     *
     * If a category with that name already exists on the *other* side, it isn't duplicated — the
     * existing row is switched on for this side as well, so "Home" on Debit and "Home" on Credit
     * are one and the same category and their totals net against each other in Reports. [onResult]
     * reports that case back with a message so the UI can tell the user what happened.
     */
    fun addCategory(name: String, parentId: Long? = null, onResult: (Boolean, String?) -> Unit) {
        val trimmedName = name.trim()
        if (trimmedName.isBlank()) {
            onResult(false, "Name cannot be empty")
            return
        }
        val scope = _scope.value
        viewModelScope.launch {
            val existing = repository.getCategoryByNameCaseInsensitive(trimmedName)
            when {
                existing == null -> {
                    repository.insertCategory(
                        Category(
                            name = trimmedName,
                            parentId = parentId,
                            usedForDebit = scope == CategoryScope.DEBIT,
                            usedForCredit = scope == CategoryScope.CREDIT
                        )
                    )
                    onResult(true, null)
                }
                existing.isInScope(scope) -> {
                    onResult(false, "\"${existing.name}\" is already in your ${scope.label} categories.")
                }
                else -> {
                    // Same name on the other side: link the two instead of creating a duplicate.
                    linkToScope(existing, scope)
                    val other = if (scope == CategoryScope.DEBIT) "Credit" else "Debit"
                    onResult(true, "Linked to your existing $other category \"${existing.name}\".")
                }
            }
        }
    }

    /** Adds an existing category from the other side to this side, linking the two. */
    fun linkExistingCategory(category: Category, onResult: (Boolean, String?) -> Unit) {
        val scope = _scope.value
        viewModelScope.launch {
            linkToScope(category, scope)
            onResult(true, "\"${category.name}\" is now linked across Debit and Credit.")
        }
    }

    private suspend fun linkToScope(category: Category, scope: CategoryScope) {
        repository.setCategoryScope(
            category,
            usedForDebit = category.usedForDebit || scope == CategoryScope.DEBIT,
            usedForCredit = category.usedForCredit || scope == CategoryScope.CREDIT
        )
    }

    /**
     * Removes a category from the current side only, leaving it in place on the other side. Used
     * to unlink "Home" from Credit while keeping it as a Debit category. Refuses when transactions
     * of that type already use it, so nothing is orphaned.
     */
    fun removeFromScope(category: Category, onResult: (Boolean, String?) -> Unit) {
        val scope = _scope.value
        viewModelScope.launch {
            if (!category.isLinked) {
                onResult(false, "\"${category.name}\" is only a ${scope.label} category — delete it instead.")
                return@launch
            }
            if (category.name in ExpenseRepository.PROTECTED_CATEGORIES) {
                onResult(false, "\"${category.name}\" is required by the app and can't be removed.")
                return@launch
            }
            val inUse = repository.getCategoryUsageCount(category.name, scope.transactionType)
            if (inUse > 0) {
                onResult(false, "$inUse ${scope.label} transaction(s) still use \"${category.name}\".")
                return@launch
            }
            repository.setCategoryScope(
                category,
                usedForDebit = category.usedForDebit && scope != CategoryScope.DEBIT,
                usedForCredit = category.usedForCredit && scope != CategoryScope.CREDIT
            )
            onResult(true, "Unlinked \"${category.name}\" from ${scope.label}.")
        }
    }

    private fun Category.isInScope(scope: CategoryScope) =
        if (scope == CategoryScope.DEBIT) usedForDebit else usedForCredit

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
        if (category.name in ExpenseRepository.PROTECTED_CATEGORIES) {
            onResult(false, "The '${category.name}' category cannot be deleted as it is required by the app.")
            return
        }

        val scope = _scope.value
        viewModelScope.launch {
            val remaining = if (scope == CategoryScope.DEBIT) {
                repository.countDebitCategories()
            } else {
                repository.countCreditCategories()
            }
            if (remaining <= 1) {
                onResult(false, "At least one ${scope.label} category must exist.")
                return@launch
            }

            if (repository.countSubcategories(category.id) > 0) {
                onResult(false, "Delete or move its subcategories first.")
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

    fun restoreCategory(category: Category) {
        viewModelScope.launch {
            repository.updateCategory(category.name, category)
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
