package com.nothing.expensetracker.data.local

import androidx.compose.runtime.Immutable
import androidx.room.Entity
import androidx.room.PrimaryKey

@Immutable
@Entity(tableName = "payment_methods")
data class PaymentMethod(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val isSystem: Boolean = false,
    /**
     * What this account held before the first recorded transaction. Only meaningful when
     * [fundedById] is null, since a method that draws on another account holds nothing itself.
     */
    val openingBalance: Double = 0.0,
    /**
     * The account this method draws on, or null when the method *is* the account.
     *
     * UPI is the reason this exists: it is a way of paying, not a place money sits, and spending
     * by UPI comes out of a bank account. Before accounts existed the balance queries simply
     * matched 'Bank' OR 'UPI' in SQL, which worked for exactly one bank account and silently
     * dropped every transaction on any other method.
     */
    val fundedById: Long? = null
) {
    /** Whether this method holds money, rather than drawing on something that does. */
    val isAccount: Boolean get() = fundedById == null
}
