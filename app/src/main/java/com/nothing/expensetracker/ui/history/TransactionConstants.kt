package com.nothing.expensetracker.ui.history

object TransactionConstants {
    val TRANSACTION_TYPES = listOf("Debit", "Credit")
    val STANDARD_METHODS = listOf("UPI", "Cash", "Bank")
    val CREDIT_CATEGORIES = listOf("Salary", "Friend", "Refund", "Investment Return", "Other")
    val FRIEND_CREDIT_METHODS = listOf("Bank", "UPI", "Cash", "RAS")

    fun isFriendCategory(type: String, category: String): Boolean {
        return (type == "Debit" && category == "Friends") || (type == "Credit" && category == "Friend")
    }

    fun getAvailableMethods(type: String, category: String): List<String> {
        return if (type == "Credit") {
            if (category == "Friend") FRIEND_CREDIT_METHODS else STANDARD_METHODS
        } else {
            STANDARD_METHODS
        }
    }

    fun getInitialCategory(type: String, debitCategories: List<String>): String {
        return if (type == "Credit") {
            CREDIT_CATEGORIES.first()
        } else {
            debitCategories.firstOrNull() ?: "Other"
        }
    }
}
