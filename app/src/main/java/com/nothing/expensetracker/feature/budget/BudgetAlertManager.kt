package com.nothing.expensetracker.feature.budget

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.nothing.expensetracker.data.local.Budget
import com.nothing.expensetracker.data.local.BudgetDao
import com.nothing.expensetracker.data.local.Expense
import com.nothing.expensetracker.data.local.ExpenseDao
import com.nothing.expensetracker.ui.history.TransactionConstants
import com.nothing.expensetracker.util.formatCurrency
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Instant
import java.time.ZoneId
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

private const val BUDGET_CHANNEL_ID = "budget_alert_channel"

/** Checks a just-saved expense against its overall/category budget and notifies once per threshold crossed. */
@Singleton
class BudgetAlertManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val expenseDao: ExpenseDao,
    private val budgetDao: BudgetDao
) {
    suspend fun checkThresholds(expense: Expense) {
        if (expense.type != "Debit" || TransactionConstants.isNonSpendingCategory(expense.type, expense.category)) return

        val date = Instant.ofEpochMilli(expense.timestamp).atZone(ZoneId.systemDefault()).toLocalDate()
        val monthStr = String.format(Locale.ROOT, "%02d", date.monthValue)
        val yearStr = date.year.toString()

        budgetDao.findExistingBudget(null, date.monthValue, date.year)?.let { budget ->
            val spent = expenseDao.getTotalDebitForMonth(monthStr, yearStr) ?: 0.0
            evaluateAndNotify(budget, spent, label = "Overall Budget")
        }

        budgetDao.findExistingBudget(expense.category, date.monthValue, date.year)?.let { budget ->
            val spent = expenseDao.getTotalDebitForCategoryMonth(expense.category, monthStr, yearStr) ?: 0.0
            evaluateAndNotify(budget, spent, label = "${expense.category} Budget")
        }
    }

    private suspend fun evaluateAndNotify(budget: Budget, spent: Double, label: String) {
        if (budget.amount <= 0) return
        val percentage = (spent / budget.amount) * 100
        val newThreshold = when {
            percentage >= 100 -> 100
            percentage >= 80 -> 80
            else -> 0
        }
        if (newThreshold > 0 && newThreshold > budget.lastAlertThreshold) {
            notify(budget, label, newThreshold, spent)
            budgetDao.updateBudget(budget.copy(lastAlertThreshold = newThreshold))
        }
    }

    private fun notify(budget: Budget, label: String, threshold: Int, spent: Double) {
        createChannel()
        val title = if (threshold >= 100) "$label exceeded" else "$label at $threshold%"
        val text = "${formatCurrency(spent)} of ${formatCurrency(budget.amount)} spent this month"
        val notification = NotificationCompat.Builder(context, BUDGET_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_warning)
            .setContentTitle(title)
            .setContentText(text)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .build()

        val manager = NotificationManagerCompat.from(context)
        if (manager.areNotificationsEnabled()) {
            try {
                manager.notify(budget.id.toInt(), notification)
            } catch (e: SecurityException) {
                Log.w("BudgetAlertManager", "Notification permission not granted", e)
            }
        }
    }

    private fun createChannel() {
        val channel = NotificationChannel(
            BUDGET_CHANNEL_ID,
            "Budget Alerts",
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = "Notifies when you're close to or over a budget limit"
        }
        context.getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
    }
}
