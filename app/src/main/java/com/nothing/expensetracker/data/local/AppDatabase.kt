package com.nothing.expensetracker.data.local

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(entities = [Expense::class, Friend::class, Category::class, Budget::class], version = 10, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun expenseDao(): ExpenseDao
    abstract fun friendDao(): FriendDao
    abstract fun categoryDao(): CategoryDao
    abstract fun budgetDao(): BudgetDao
}
