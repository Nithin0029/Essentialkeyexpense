package com.nothing.expensetracker.data.local

import androidx.compose.runtime.Immutable
import androidx.room.Entity
import androidx.room.PrimaryKey

@Immutable
@Entity(tableName = "budgets")
data class Budget(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val categoryName: String? = null, // null for overall budget
    val amount: Double,
    val month: Int, // 1-12
    val year: Int,
    val lastAlertThreshold: Int = 0, // highest of 0/80/100 already notified for this budget row
    val syncStatus: String = "Synced", // "Pending", "Syncing", "Synced", "Failed", "Deleted"
    val lastSyncAttempt: Long = 0,
    val syncError: String? = null
)
