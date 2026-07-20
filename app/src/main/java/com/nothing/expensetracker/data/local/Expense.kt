package com.nothing.expensetracker.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "expenses")
data class Expense(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val amount: Double,
    val description: String,
    val category: String, // e.g., "Food", "Travel"
    val colorCode: String, // e.g., "GREEN", "YELLOW"
    val timestamp: Long = System.currentTimeMillis(),
    val isSynced: Boolean = false
)
