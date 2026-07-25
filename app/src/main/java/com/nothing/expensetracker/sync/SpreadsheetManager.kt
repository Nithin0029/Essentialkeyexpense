package com.nothing.expensetracker.sync

import android.content.Context
import android.util.Log
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.sheets.v4.Sheets
import com.google.api.services.sheets.v4.SheetsScopes
import com.google.api.services.sheets.v4.model.*
import com.nothing.expensetracker.data.local.AppPrefs
import com.nothing.expensetracker.data.repository.ExpenseRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.util.*
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SpreadsheetManager @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val appPrefs: AppPrefs,
    private val repository: ExpenseRepository,
) {
    private val tag = "SpreadsheetManager"

    @Suppress("DEPRECATION")
    private fun getSheetsService(): Sheets? {
        val account = GoogleSignIn.getLastSignedInAccount(context) ?: return null
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

    /**
     * Ensures a spreadsheet exists and is accessible.
     * Returns the Spreadsheet ID or null if failed.
     */
    suspend fun getOrCreateSpreadsheet(): String? = withContext(Dispatchers.IO) {
        val service = getSheetsService() ?: return@withContext null
        
        val existingId = appPrefs.spreadsheetId.value
        val spreadsheetId = if (existingId != null) {
            try {
                Log.d(tag, "Verifying existing spreadsheet: $existingId")
                service.spreadsheets()[existingId].execute()
                existingId
            } catch (e: Exception) {
                Log.w(tag, "Existing spreadsheet inaccessible. Clearing ID.", e)
                appPrefs.setSpreadsheetId(null)
                createNewSpreadsheet(service)
            }
        } else {
            createNewSpreadsheet(service)
        }

        spreadsheetId?.let { id ->
            ensureRequiredSheetsExist(service, id)
            ensureHeadersExist(service, id)
            ensureInitialDataExists(service, id)
            ensureMasterDataExists(service, id)
        }

        return@withContext spreadsheetId
    }

    private suspend fun ensureMasterDataExists(service: Sheets, spreadsheetId: String) {
        // 1. Initialize Categories
        val defaultCategories = listOf("Food", "Snack", "Home", "Petrol", "Friends", "Income", "Others")
        val localCategories = repository.getAllCategories().first()
        val allCategories = (defaultCategories + localCategories).distinct()
        
        ensureListData(
            service = service,
            spreadsheetId = spreadsheetId,
            sheetName = "Categories",
            data = allCategories.mapIndexed { index, name -> listOf((index + 1).toString(), name) }
        )

        // 2. Initialize Friends
        val localFriends = repository.getAllFriends().first()
        if (localFriends.isNotEmpty()) {
            ensureListData(
                service = service,
                spreadsheetId = spreadsheetId,
                sheetName = "Friends",
                data = localFriends.mapIndexed { index, name -> listOf((index + 1).toString(), name) }
            )
        }
    }

    private fun ensureListData(service: Sheets, spreadsheetId: String, sheetName: String, data: List<List<String>>) {
        try {
            val response = service.spreadsheets().values()[spreadsheetId, "$sheetName!B2:B"]
                .execute()
            val existingNames = response.getValues()?.asSequence()?.map { it.firstOrNull()?.toString() }?.toSet() ?: emptySet()

            val newRows = data.filter { it[1] !in existingNames }

            if (newRows.isNotEmpty()) {
                val valueRange = ValueRange().setValues(newRows)
                service.spreadsheets().values()
                    .append(spreadsheetId, "$sheetName!A2", valueRange)
                    .setValueInputOption("RAW")
                    .execute()
                Log.d(tag, "Appended ${newRows.size} rows to $sheetName")
            }
        } catch (e: Exception) {
            Log.e(tag, "Failed to ensure data for sheet: $sheetName", e)
        }
    }

    private fun ensureInitialDataExists(service: Sheets, spreadsheetId: String) {
        val now = java.text.SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault()).format(Date())

        // 1. Initial Settings
        val settings = mapOf(
            "Opening Balance" to "₹${appPrefs.openingBalance.value}",
            "Currency" to "INR",
            "Currency Symbol" to "₹",
            "Date Format" to "DD/MM/YYYY",
            "Last Sync" to "Never"
        )
        ensureSheetData(service, spreadsheetId, "Settings", settings)

        // 2. Initial Metadata
        val metadata = mapOf(
            "Schema Version" to "1",
            "App Version" to "2.0.0",
            "Database Version" to "1",
            "Created Date" to now,
            "Last Updated" to now
        )
        ensureSheetData(service, spreadsheetId, "App_Metadata", metadata)
    }

    private fun ensureSheetData(service: Sheets, spreadsheetId: String, sheetName: String, data: Map<String, String>) {
        try {
            val response = service.spreadsheets().values()[spreadsheetId, "$sheetName!A2:B"]
                .execute()
            val existingValues = response.getValues() ?: emptyList<List<Any>>()
            val existingKeys = existingValues.asSequence().map { it.firstOrNull()?.toString() }.toSet()

            val newRows = data.filter { it.key !in existingKeys }.map { listOf(it.key, it.value) }

            if (newRows.isNotEmpty()) {
                val valueRange = ValueRange().setValues(newRows)
                service.spreadsheets().values()
                    .append(spreadsheetId, "$sheetName!A2:B", valueRange)
                    .setValueInputOption("RAW")
                    .execute()
                Log.d(tag, "Initialized data for sheet: $sheetName")
            }
        } catch (e: Exception) {
            Log.e(tag, "Failed to ensure initial data for sheet: $sheetName", e)
        }
    }

    private fun ensureHeadersExist(service: Sheets, spreadsheetId: String) {
        val headerMap = mapOf(
            "Transactions" to listOf(
                "Transaction ID", "Date", "Type", "Category", "Amount",
                "Payment Method", "Friend Name", "Notes", "Created At",
                "Updated At", "Sync Status"
            ),
            "Categories" to listOf("Category ID", "Category Name"),
            "Friends" to listOf("Friend ID", "Friend Name"),
            "Settings" to listOf("Setting", "Value"),
            "App_Metadata" to listOf("Key", "Value")
        )

        headerMap.forEach { (sheetName, headers) ->
            try {
                val range = "$sheetName!A1:Z1"
                val response = service.spreadsheets().values()
                    .get(spreadsheetId, range)
                    .execute()
                val currentHeaders = response.getValues()?.firstOrNull()?.map { it.toString() }

                if ((currentHeaders == null) || (currentHeaders != headers)) {
                    val valueRange = ValueRange().setValues(listOf(headers))
                    service.spreadsheets().values()
                        .update(spreadsheetId, "$sheetName!A1", valueRange)
                        .setValueInputOption("RAW")
                        .execute()
                    Log.d(tag, "Headers initialized for sheet: $sheetName")
                }
            } catch (e: Exception) {
                Log.e(tag, "Failed to ensure headers for sheet: $sheetName", e)
            }
        }
    }

    private fun ensureRequiredSheetsExist(service: Sheets, spreadsheetId: String) {
        try {
            val spreadsheet = service.spreadsheets()[spreadsheetId].execute()
            val existingSheets = spreadsheet.sheets
            val existingSheetNames = existingSheets.map { it.properties.title }
            
            val requiredSheets = listOf("Transactions", "Categories", "Friends", "Settings", "App_Metadata")
            val requests = mutableListOf<Request>()

            // 1. Handle "Sheet1" - rename to "Transactions" if missing, otherwise mark for deletion
            if ("Sheet1" in existingSheetNames) {
                val sheet1Id = existingSheets.find { it.properties.title == "Sheet1" }?.properties?.sheetId
                if ("Transactions" !in existingSheetNames) {
                    requests.add(
                        Request().setUpdateSheetProperties(
                            UpdateSheetPropertiesRequest()
                                .setProperties(SheetProperties().setSheetId(sheet1Id).setTitle("Transactions"))
                                .setFields("title")
                        )
                    )
                    Log.d(tag, "Renaming Sheet1 to Transactions")
                } else {
                    // If Transactions already exists, we will delete Sheet1 later in the batch
                    // but only if we have at least one other sheet staying (which we will).
                    requests.add(Request().setDeleteSheet(DeleteSheetRequest().setSheetId(sheet1Id)))
                    Log.d(tag, "Deleting redundant Sheet1")
                }
            }

            // 2. Identify which sheets still need to be created
            // We assume "Transactions" is handled if "Sheet1" was renamed above
            val namesAfterRename = if ("Sheet1" in existingSheetNames && "Transactions" !in existingSheetNames) {
                existingSheetNames.map { if (it == "Sheet1") "Transactions" else it }
            } else {
                existingSheetNames
            }

            requiredSheets.forEach { name ->
                if (name !in namesAfterRename) {
                    requests.add(
                        Request().setAddSheet(
                            AddSheetRequest().setProperties(SheetProperties().setTitle(name))
                        )
                    )
                    Log.d(tag, "Adding missing sheet: $name")
                }
            }

            if (requests.isEmpty()) return

            val batchUpdate = BatchUpdateSpreadsheetRequest().setRequests(requests)
            service.spreadsheets().batchUpdate(spreadsheetId, batchUpdate).execute()
            Log.d(tag, "Spreadsheet structure synchronized successfully")
        } catch (e: Exception) {
            Log.e(tag, "Failed to ensure required sheets exist", e)
        }
    }

    private fun createNewSpreadsheet(service: Sheets): String? {
        return try {
            Log.d(tag, "Creating new spreadsheet: Expense Tracker Database")
            val spreadsheet = Spreadsheet().setProperties(
                SpreadsheetProperties().setTitle("Expense Tracker Database")
            )
            val created = service.spreadsheets().create(spreadsheet).execute()
            val newId = created.spreadsheetId
            
            Log.d(tag, "New spreadsheet created with ID: $newId")
            appPrefs.setSpreadsheetId(newId)
            newId
        } catch (e: Exception) {
            Log.e(tag, "Failed to create new spreadsheet", e)
            null
        }
    }
}
