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
import java.time.LocalTime
import java.time.ZoneId
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
            val zone = ZoneId.systemDefault()
            // The time of day is the moment it fired, so several rules landing at once keep an
            // order; the *date* is the day each payment was owed for, which is the part that
            // matters. Dating a day-31 rule that fires on the 1st "today" filed it in the next
            // month, where every report then counted it.
            val timeOfDay = LocalTime.now(zone)
            val rules = autopayRepository.getActiveAutopayRules()
            var firedAny = false

            rules.forEach { rule ->
                // One date per month still owed, so a month the worker never ran in is caught up
                // rather than skipped.
                val dueDates = autopayDueDates(rule, today, zone)
                if (dueDates.isEmpty()) return@forEach

                dueDates.forEach { dueDate ->
                    val expense = Expense(
                        amount = rule.amount,
                        description = rule.description,
                        category = rule.category,
                        type = rule.type,
                        paymentMethod = rule.paymentMethod,
                        friendId = rule.friendId,
                        notes = "Autopay",
                        timestamp = dueDate.atTime(timeOfDay).atZone(zone).toInstant().toEpochMilli(),
                        syncStatus = "Pending"
                    )
                    expenseRepository.insertExpense(expense)
                }
                // Only the newest month is stamped; the ones before it are implied by it.
                autopayRepository.markRun(rule.id, monthKeyOf(dueDates.last()))
                notifyUser(rule, dueDates)
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

    /** Says plainly when more than one month went in at once, rather than one quiet line. */
    private fun notifyUser(rule: AutopayRule, dueDates: List<LocalDate>) {
        createChannel()
        val label = rule.description.ifBlank { rule.category }
        val text = if (dueDates.size == 1) {
            "${formatCurrency(rule.amount)} added via ${rule.paymentMethod}"
        } else {
            val months = dueDates.joinToString(", ") {
                it.format(DateTimeFormatter.ofPattern("MMM"))
            }
            "${formatCurrency(rule.amount * dueDates.size)} via ${rule.paymentMethod} - " +
                "${dueDates.size} months caught up ($months)"
        }
        val notification = NotificationCompat.Builder(applicationContext, AUTOPAY_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setContentTitle("Autopay: $label")
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
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
