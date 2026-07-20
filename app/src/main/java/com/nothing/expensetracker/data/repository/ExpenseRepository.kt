package com.nothing.expensetracker.data.repository

import com.nothing.expensetracker.data.local.Expense
import com.nothing.expensetracker.data.local.ExpenseDao
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ExpenseRepository @Inject constructor(
    private val expenseDao: ExpenseDao
) {
    fun getAllExpenses() = expenseDao.getAllExpenses()

    suspend fun insertExpense(expense: Expense) = expenseDao.insertExpense(expense)

    suspend fun getUnsyncedExpenses() = expenseDao.getUnsyncedExpenses()

    suspend fun markAsSynced(id: Long) = expenseDao.markAsSynced(id)

    fun getExpensesByCategory() = expenseDao.getExpensesByCategory()

    fun getExpensesByCategoryFiltered(month: String, year: String) = 
        expenseDao.getExpensesByCategoryFiltered(month, year)
}
