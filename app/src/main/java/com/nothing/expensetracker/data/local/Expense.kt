package com.nothing.expensetracker.data.local

import androidx.compose.runtime.Immutable
import androidx.room.Entity
import androidx.room.PrimaryKey

@Immutable
@Entity(
    tableName = "expenses",
    indices = [
        androidx.room.Index(value = ["friendId"]),
        androidx.room.Index(value = ["timestamp"]),
        androidx.room.Index(value = ["category"]),
        androidx.room.Index(value = ["type"])
    ]
)
data class Expense(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val amount: Double,
    val description: String,
    val category: String,
    val type: String, // "Debit" or "Credit"
    val paymentMethod: String, // "UPI", "Cash", "Bank"
    val friendId: String? = null,
    val notes: String = "",
    val colorCode: String = "GENERAL",
    val timestamp: Long = System.currentTimeMillis(),
    val syncStatus: String = "Pending", // "Pending", "Syncing", "Synced", "Failed"
    val lastSyncAttempt: Long = 0,
    val syncError: String? = null
)
