package com.nothing.expensetracker.ui.history

object TransactionConstants {
    val TRANSACTION_TYPES = listOf("Debit", "Credit")
    val STANDARD_METHODS = listOf("UPI", "Cash", "Bank")
    /**
     * Credit categories now live in the `categories` table alongside Debit ones and are managed in
     * Category Management, so this list is only a fallback for the moment before the database Flow
     * emits. It mirrors the defaults seeded by `ExpenseRepository.seedDefaultCategories()`.
     */
    val FALLBACK_CREDIT_CATEGORIES = listOf("Friend", "Other", "Refund", "Salary", "Transfer")

    /**
     * The friend category, on either side of the ledger.
     *
     * Fresh installs seed a single linked "Friend" category. Installs created before that shipped
     * have "Friends" on the Debit side and "Friend" on Credit, and their existing transactions
     * still carry those names, so both spellings are accepted on both sides.
     */
    fun isFriendCategory(type: String, category: String): Boolean {
        return category == "Friend" || category == "Friends"
    }

    /**
     * [availableMethods] is the user's current payment method list (from PaymentMethodManagement);
     * falls back to [STANDARD_METHODS] when it hasn't loaded yet. "RAS" is appended for friend
     * settlements regardless of the user's list since it's a friend-specific reimbursement marker.
     */
    fun getAvailableMethods(type: String, category: String, availableMethods: List<String> = STANDARD_METHODS): List<String> {
        val methods = availableMethods.ifEmpty { STANDARD_METHODS }
        return if (type == "Credit" && category == "Friend") {
            (methods + "RAS").distinct()
        } else {
            methods
        }
    }

    fun getInitialCategory(
        type: String,
        debitCategories: List<String>,
        creditCategories: List<String> = FALLBACK_CREDIT_CATEGORIES
    ): String {
        return if (type == "Credit") {
            creditCategories.firstOrNull() ?: FALLBACK_CREDIT_CATEGORIES.first()
        } else {
            debitCategories.firstOrNull() ?: "Other"
        }
    }

    /**
     * True only for "Transfer" — money moved between your own accounts (e.g. bank to cash),
     * which isn't real income/expense. Lending to or borrowing from a friend still counts as
     * a real expense/income; only the Friends tab nets it separately against that friend's balance.
     */
    fun isNonSpendingCategory(type: String, category: String): Boolean {
        return category == "Transfer"
    }

    private val AMOUNT_INPUT_REGEX = Regex("^\\d{0,9}(\\.\\d{0,2})?$")

    /** Restricts amount text fields to whole numbers with at most 2 decimal places. */
    fun isValidAmountInput(value: String): Boolean {
        return value.isEmpty() || AMOUNT_INPUT_REGEX.matches(value)
    }
}
