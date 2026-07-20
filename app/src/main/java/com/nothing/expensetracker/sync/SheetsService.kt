package com.nothing.expensetracker.sync

import android.content.Context
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.sheets.v4.Sheets
import com.google.api.services.sheets.v4.SheetsScopes
import java.util.*

class SheetsService(private val context: Context) {

    private fun getSheetsService(): Sheets? {
        val authManager = SheetsAuthManager(context)
        val account = authManager.getSignedInAccount() ?: return null

        val credential = GoogleAccountCredential.usingOAuth2(
            context,
            Collections.singleton(SheetsScopes.SPREADSHEETS)
        ).setSelectedAccount(account.account)

        return Sheets.Builder(
            NetHttpTransport(),
            GsonFactory.getDefaultInstance(),
            credential
        ).setApplicationName("Essential Expense Tracker").build()
    }

    fun fetchCategoriesFromSettings(spreadsheetId: String): List<String> {
        return try {
            val service = getSheetsService() ?: return listOf("Food", "Shopping", "Bills", "Others")
            val response = service.spreadsheets().values()
                .get(spreadsheetId, "Settings!A2:A")
                .execute()
            val values = response.getValues()
            values?.mapNotNull { it.firstOrNull()?.toString() } ?: listOf("Food", "Shopping", "Bills", "Others")
        } catch (e: Exception) {
            listOf("Food", "Shopping", "Bills", "Others")
        }
    }
}
