package com.nothing.expensetracker.data.local

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [Expense::class, Friend::class, Category::class, Budget::class, PaymentMethod::class, AutopayRule::class],
    version = 15,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun expenseDao(): ExpenseDao
    abstract fun friendDao(): FriendDao
    abstract fun categoryDao(): CategoryDao
    abstract fun budgetDao(): BudgetDao
    abstract fun paymentMethodDao(): PaymentMethodDao
    abstract fun autopayDao(): AutopayDao
}
