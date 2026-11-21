package com.nothing.expensetracker.ui.history

object TransactionConstants {
    /** Re-exported so screens can name it without reaching into the repository layer. */
    const val SETTLED_IN_KIND = com.nothing.expensetracker.data.repository.SETTLED_IN_KIND

    val TRANSACTION_TYPES = listOf("Debit", "Credit")
    val STANDARD_METHODS = listOf("UPI", "Cash", "Bank")

    /**
     * What a *new* transaction should start on, given the methods that actually exist.
     *
     * Every screen used to open with the literal "UPI". That was safe only while UPI could not
     * be renamed or deleted; once it could, a screen opened on a method that no longer existed
     * and anyone who did not touch the picker saved a transaction that came off no balance.
     *
     * Only for new entries. An existing transaction keeps whatever it was recorded against, even
     * if that method has since gone — changing someone's record behind their back while they
     * edit an unrelated field is worse than showing them a name they will recognise.
     */
    fun defaultMethod(available: List<String>): String =
        available.firstOrNull { !it.equals(SETTLED_IN_KIND, ignoreCase = true) }
            ?: available.firstOrNull()
            ?: STANDARD_METHODS.first()

    /**
     * Credit categories now live in the `categories` table alongside Debit ones and are managed in
     * Category Management, so this list is only a fallback for the moment before the database Flow
     * emits. It mirrors the defaults seeded by `ExpenseRepository.seedDefaultCategories()`.
     */
    val FALLBACK_CREDIT_CATEGORIES = listOf("Friend", "Other", "Salary", "Transfer")

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
            (methods + SETTLED_IN_KIND).distinct()
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
     * Categories where money moves but nothing is spent or earned.
     *
     * "Transfer" is money between your own accounts (bank to cash). "Investment" is money put
     * into something you still own — a SIP is not an expense, and counting it as one overstates
     * every spending figure on the Dashboard and in Reports, eats into your budget, and gets
     * multiplied out by the Reports pace projection as though it were burn.
     *
     * Both still move your balance. The balance queries sum by payment method and never look at
     * the category, so money that left your bank is gone from your bank either way; what these
     * names change is only whether it is *reported* as spending.
     *
     * Lending to or borrowing from a friend is deliberately not here — that is a real expense or
     * income, and only the Friends tab nets it separately against that friend's balance.
     */
    val NON_SPENDING_CATEGORIES = setOf("Transfer", "Investment")

    fun isNonSpendingCategory(type: String, category: String): Boolean {
        return category in NON_SPENDING_CATEGORIES
    }

    private val AMOUNT_INPUT_REGEX = Regex("^\\d{0,9}(\\.\\d{0,2})?$")

    /** Restricts amount text fields to whole numbers with at most 2 decimal places. */
    fun isValidAmountInput(value: String): Boolean {
        return value.isEmpty() || AMOUNT_INPUT_REGEX.matches(value)
    }
}
