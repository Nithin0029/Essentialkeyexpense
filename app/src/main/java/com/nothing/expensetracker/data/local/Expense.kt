package com.nothing.expensetracker.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "expenses")
data class Expense(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val amount: Double,
    val description: String,
    val category: String,
    val type: String, // "Debit" or "Credit"
    val paymentMethod: String, // "UPI", "Cash", "Bank"
    val friendId: String = "",
    val notes: String = "",
    val colorCode: String = "GENERAL",
    val timestamp: Long = System.currentTimeMillis(),
    val isSynced: Boolean = false
)
