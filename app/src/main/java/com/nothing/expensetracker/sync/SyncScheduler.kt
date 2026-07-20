package com.nothing.expensetracker.sync

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.*
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.sheets.v4.Sheets
import com.google.api.services.sheets.v4.SheetsScopes
import com.google.api.services.sheets.v4.model.ValueRange
import com.nothing.expensetracker.data.local.Expense
import com.nothing.expensetracker.data.repository.ExpenseRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*
import android.util.Log
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SyncScheduler @Inject constructor(
    @ApplicationContext private val context: Context
) {
    fun scheduleSync() {
        Log.d("SyncScheduler", "Scheduling sync...")
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val syncRequest = OneTimeWorkRequestBuilder<SyncWorker>()
            .setConstraints(constraints)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 1, java.util.concurrent.TimeUnit.MINUTES)
            .build()

        WorkManager.getInstance(context).enqueueUniqueWork(
            "expense_sync",
            ExistingWorkPolicy.APPEND_OR_REPLACE,
            syncRequest
        )
    }
}

@HiltWorker
class SyncWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val repository: ExpenseRepository
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        Log.d("SyncWorker", "Work started")
        val spreadsheetId = SyncPrefs.getSpreadsheetId(applicationContext) ?: run {
            Log.e("SyncWorker", "No spreadsheet ID found in SyncPrefs")
            return@withContext Result.failure()
        }
        
        val authManager = SheetsAuthManager(applicationContext)
        val account = authManager.getSignedInAccount() ?: run {
            Log.e("SyncWorker", "No Google account signed in")
            return@withContext Result.failure()
        }

        try {
            val unsyncedExpenses = repository.getUnsyncedExpenses()
            Log.d("SyncWorker", "Found ${unsyncedExpenses.size} unsynced expenses")
            if (unsyncedExpenses.isEmpty()) return@withContext Result.success()

            val credential = GoogleAccountCredential.usingOAuth2(
                applicationContext,
                Collections.singleton(SheetsScopes.SPREADSHEETS)
            ).setSelectedAccount(account.account)

            val sheetsService = Sheets.Builder(
                NetHttpTransport(),
                GsonFactory.getDefaultInstance(),
                credential
            ).setApplicationName("Essential Expense Tracker").build()

            for (expense in unsyncedExpenses) {
                val values = listOf(
                    listOf(
                        SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date(expense.timestamp)),
                        expense.amount.toString(),
                        expense.description,
                        expense.category,
                        expense.colorCode
                    )
                )
                val body = ValueRange().setValues(values)
                
                Log.d("SyncWorker", "Appending expense to sheet: ${expense.description}")
                sheetsService.spreadsheets().values().append(spreadsheetId, "Expenses!A1", body)
                    .setValueInputOption("USER_ENTERED")
                    .execute()

                repository.markAsSynced(expense.id)
                Log.d("SyncWorker", "Expense marked as synced")
            }

            Log.d("SyncWorker", "Sync completed successfully")
            Result.success()
        } catch (e: Exception) {
            Log.e("SyncWorker", "Error during sync", e)
            Result.retry()
        }
    }
}
