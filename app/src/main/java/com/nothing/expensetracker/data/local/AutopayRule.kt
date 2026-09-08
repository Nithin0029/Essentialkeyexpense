package com.nothing.expensetracker.data.local

import androidx.compose.runtime.Immutable
import androidx.room.Entity
import androidx.room.PrimaryKey

@Immutable
@Entity(tableName = "autopay_rules")
data class AutopayRule(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val amount: Double,
    val description: String = "",
    val category: String,
    val type: String, // "Debit" or "Credit"
    val paymentMethod: String,
    val friendId: String? = null,
    val dayOfMonth: Int, // 1-31; clamped to the last day of shorter months
    val isActive: Boolean = true,
    val lastRunMonth: String? = null, // "yyyy-MM" of the month this last fired, to prevent double-firing
    val createdAt: Long = System.currentTimeMillis()
)
