package com.nothing.expensetracker.data.local

import androidx.room.Database
import androidx.room.RoomDatabase

/**
 * The Room schema version, named so the spreadsheet's App_Metadata tab can report the same
 * number without anyone having to keep a second copy of it in step by hand.
 */
const val DATABASE_VERSION = 16

@Database(
    entities = [Expense::class, Friend::class, Category::class, Budget::class, PaymentMethod::class, AutopayRule::class],
    version = DATABASE_VERSION,
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
