package com.nothing.expensetracker.feature.autopay

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.hilt.work.HiltWorker
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.nothing.expensetracker.data.local.AutopayRule
import com.nothing.expensetracker.data.local.Expense
import com.nothing.expensetracker.data.repository.AutopayRepository
import com.nothing.expensetracker.data.repository.ExpenseRepository
import com.nothing.expensetracker.sync.SyncScheduler
import com.nothing.expensetracker.util.formatCurrency
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

private const val AUTOPAY_CHANNEL_ID = "autopay_channel"
private const val AUTOPAY_DAILY_WORK_NAME = "autopay_daily_check"
private const val AUTOPAY_NOW_WORK_NAME = "autopay_immediate_check"

@Singleton
class AutopayScheduler @Inject constructor(
    @ApplicationContext private val context: Context
) {
    /** Daily fallback so autopay still fires even if the app is never opened on the due date. */
    fun scheduleDailyCheck() {
        val request = PeriodicWorkRequestBuilder<AutopayWorker>(1, TimeUnit.DAYS)
            .setConstraints(Constraints.Builder().build())
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            AUTOPAY_DAILY_WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            request
        )
    }

    /** Runs the due-check right away, e.g. on app launch, so today's autopay doesn't wait on the daily schedule. */
    fun runNow() {
        val request = OneTimeWorkRequestBuilder<AutopayWorker>().build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            AUTOPAY_NOW_WORK_NAME,
            ExistingWorkPolicy.KEEP,
            request
        )
    }
}

@HiltWorker
class AutopayWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val autopayRepository: AutopayRepository,
    private val expenseRepository: ExpenseRepository,
    private val syncScheduler: SyncScheduler
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            val today = LocalDate.now()
            val monthKey = today.format(DateTimeFormatter.ofPattern("yyyy-MM"))
            val rules = autopayRepository.getActiveAutopayRules()
            var firedAny = false

            rules.forEach { rule ->
                if (rule.lastRunMonth == monthKey) return@forEach

                val targetDay = rule.dayOfMonth.coerceAtMost(today.lengthOfMonth())
                if (today.dayOfMonth < targetDay) return@forEach

                val expense = Expense(
                    amount = rule.amount,
                    description = rule.description,
                    category = rule.category,
                    type = rule.type,
                    paymentMethod = rule.paymentMethod,
                    friendId = rule.friendId,
                    notes = "Autopay",
                    timestamp = System.currentTimeMillis(),
                    syncStatus = "Pending"
                )
                expenseRepository.insertExpense(expense)
                autopayRepository.markRun(rule.id, monthKey)
                notifyUser(rule)
                firedAny = true
            }

            if (firedAny) {
                syncScheduler.scheduleSync()
            }
            Result.success()
        } catch (e: Exception) {
            Log.e("AutopayWorker", "Autopay check failed", e)
            Result.retry()
        }
    }

    private fun notifyUser(rule: AutopayRule) {
        createChannel()
        val label = rule.description.ifBlank { rule.category }
        val notification = NotificationCompat.Builder(applicationContext, AUTOPAY_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setContentTitle("Autopay: $label")
            .setContentText("${formatCurrency(rule.amount)} added via ${rule.paymentMethod}")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .build()

        val manager = NotificationManagerCompat.from(applicationContext)
        if (manager.areNotificationsEnabled()) {
            try {
                manager.notify(rule.id.toInt(), notification)
            } catch (e: SecurityException) {
                Log.w("AutopayWorker", "Notification permission not granted", e)
            }
        }
    }

    private fun createChannel() {
        val channel = NotificationChannel(
            AUTOPAY_CHANNEL_ID,
            "Autopay",
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = "Confirms when a recurring autopay transaction is added"
        }
        val manager = applicationContext.getSystemService(NotificationManager::class.java)
        manager?.createNotificationChannel(channel)
    }
}
