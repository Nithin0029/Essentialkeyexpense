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
    val parentId: Long? = null,
    /** Whether this category is offered when adding a Debit (money out) transaction. */
    val usedForDebit: Boolean = true,
    /** Whether this category is offered when adding a Credit (money in) transaction. */
    val usedForCredit: Boolean = false
) {
    /**
     * A category is "linked" when the same row serves both sides of the ledger — e.g. "Home"
     * used for both Debit and Credit. Because a name maps to exactly one row, adding an existing
     * debit category to the credit list simply turns [usedForCredit] on, and the two sides are
     * then netted against each other in Reports (Home debit − Home credit).
     */
    val isLinked: Boolean get() = usedForDebit && usedForCredit
}
