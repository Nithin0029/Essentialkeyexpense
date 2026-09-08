package com.nothing.expensetracker.util

import java.util.Locale

/** Formats a rupee amount with the ₹ symbol, thousands separators, and exactly 2 decimal places. */
fun formatCurrency(amount: Double): String = "₹" + "%,.2f".format(Locale.getDefault(), amount)

/** Same as [formatCurrency] but without the ₹ symbol. */
fun formatAmount(amount: Double): String = "%,.2f".format(Locale.getDefault(), amount)
