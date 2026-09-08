package com.nothing.expensetracker.data.local

import androidx.compose.runtime.Immutable
import androidx.room.Entity
import androidx.room.PrimaryKey

@Immutable
@Entity(tableName = "categories")
data class Category(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val iconName: String? = null,
    val colorHex: String? = null,
    val isSystem: Boolean = false,
    val syncStatus: String = "Synced", // "Pending", "Syncing", "Synced", "Failed", "Deleted"
    val lastSyncAttempt: Long = 0,
    val syncError: String? = null,
    /** Null for a top-level category; otherwise the [id] of the parent category it groups under
     *  (e.g. "Petrol" under "Bike"). Only one level of nesting is supported. */
    val parentId: Long? = null
)
