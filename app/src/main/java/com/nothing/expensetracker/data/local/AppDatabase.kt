package com.nothing.expensetracker.data.local

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(entities = [Expense::class, Friend::class], version = 5, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun expenseDao(): ExpenseDao
    abstract fun friendDao(): FriendDao
}
