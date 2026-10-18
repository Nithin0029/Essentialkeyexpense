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
import com.google.api.services.drive.Drive
import com.google.api.services.drive.model.FileList
import com.nothing.expensetracker.data.local.AppPrefs
import com.nothing.expensetracker.data.repository.ExpenseRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.util.*
import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Singleton

/** What a restore pulled down from the spreadsheet. All zeros means the device was already
 *  up to date, which is the normal case on every sign-in after the first. */
data class RestoreSummary(
    val transactions: Int = 0,
    val categories: Int = 0,
    val friends: Int = 0,
    val budgets: Int = 0,
    val paymentMethods: Int = 0,
    val autopayRules: Int = 0,
    val removedTransactions: Int = 0,
    val removedCategories: Int = 0,
    val removedFriends: Int = 0,
    val removedBudgets: Int = 0
) {
    val added: Int get() =
        transactions + categories + friends + budgets + paymentMethods + autopayRules
    val removed: Int get() =
        removedTransactions + removedCategories + removedFriends + removedBudgets
    val total: Int get() = added + removed
    val isEmpty: Boolean get() = total == 0
}

/**
 * Guards against a mass delete driven by a bad read. Below [PRUNE_SAFETY_MINIMUM] rows the ratio
 * is meaningless, so the check only applies once there is enough history for it to mean something.
 */
private const val PRUNE_SAFETY_RATIO = 0.5
private const val PRUNE_SAFETY_MINIMUM = 20

/** Settings-tab row keys, shared between the write and restore paths. */
private const val OPENING_BANK_KEY = "Opening Bank Balance"
private const val OPENING_CASH_KEY = "Opening Cash Balance"

@Singleton
class SpreadsheetManager @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val appPrefs: AppPrefs,
    private val repositoryProvider: Provider<ExpenseRepository>,
) {
    private val tag = "SpreadsheetManager"
    private var isInitialized = false

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
        val sheetsService = getSheetsService() ?: return@withContext null
        
        val savedId = appPrefs.spreadsheetId.value
        Log.d(tag, "Loaded Spreadsheet ID: $savedId")

        val verifiedId = if (savedId != null) {
            try {
                Log.d(tag, "Verifying Spreadsheet: $savedId")
                sheetsService.spreadsheets().get(savedId).execute()
                Log.d(tag, "Verifying Spreadsheet: SUCCESS")
                savedId
            } catch (e: com.google.api.client.googleapis.json.GoogleJsonResponseException) {
                if (e.statusCode == 404) {
                    Log.w(tag, "Verifying Spreadsheet: NOT FOUND (404)")
                    null
                } else {
                    Log.e(tag, "Verifying Spreadsheet: API ERROR ${e.statusCode}", e)
                    savedId
                }
            } catch (e: Exception) {
                Log.e(tag, "Verifying Spreadsheet: NETWORK OR UNKNOWN ERROR", e)
                savedId
            }
        } else {
            null
        }

        if (verifiedId != null) {
            Log.d(tag, "Reusing Spreadsheet: $verifiedId")
            initializeSpreadsheet(sheetsService, verifiedId)
            return@withContext verifiedId
        }

        val driveService = getDriveService()
        if (driveService != null) {
            Log.d(tag, "Google Drive Search: Starting search for existing file...")
            val searchedId = findExistingSpreadsheetOnDrive(driveService)
            if (searchedId != null) {
                Log.d(tag, "Google Drive Search Result: FOUND $searchedId")
                appPrefs.setSpreadsheetId(searchedId)
                initializeSpreadsheet(sheetsService, searchedId)
                return@withContext searchedId
            }
            Log.d(tag, "Google Drive Search Result: NOT FOUND")
        }

        Log.d(tag, "Creating Spreadsheet: No existing found. Creating new...")
        val newId = createNewSpreadsheet(sheetsService)
        newId?.let { id ->
            Log.d(tag, "New Spreadsheet Created: $id")
            appPrefs.setSpreadsheetId(id)
            try {
                initializeSpreadsheet(sheetsService, id)
            } catch (e: Exception) {
                Log.e(tag, "Failed to initialize new spreadsheet. Reverting state.", e)
                appPrefs.setSpreadsheetId(null)
                return@withContext null
            }
        }
        return@withContext newId
    }

    private suspend fun initializeSpreadsheet(service: Sheets, id: String) {
        if (!isInitialized) {
            ensureRequiredSheetsExist(service, id)
            ensureHeadersExist(service, id)
            applyHeaderFormatting(service, id)
            applyColumnWidths(service, id)
            applyAlternatingRowColors(service, id)
            applyValueFormatting(service, id)
            applyConditionalFormatting(service, id)
            applyFilters(service, id)
            applyCellAlignment(service, id)
            applyFinalPolish(service, id)
            ensureInitialDataExists(service, id)
            repositoryProvider.get().seedDefaultCategories()
            ensureMasterDataExists(service, id)
            isInitialized = true
            Log.d(tag, "Spreadsheet structure initialized and cached.")
        }
    }

    private fun getDriveService(): Drive? {
        val account = GoogleSignIn.getLastSignedInAccount(context) ?: return null
        val credential = GoogleAccountCredential.usingOAuth2(
            context,
            listOf(SheetsScopes.SPREADSHEETS, "https://www.googleapis.com/auth/drive.file")
        ).setSelectedAccount(account.account)

        return Drive.Builder(
            NetHttpTransport(),
            GsonFactory.getDefaultInstance(),
            credential
        ).setApplicationName("Essential Expense Tracker").build()
    }

    private fun findExistingSpreadsheetOnDrive(service: Drive): String? {
        return try {
            val result = service.files().list()
                .setQ("name = 'Expense Tracker Database' and mimeType = 'application/vnd.google-apps.spreadsheet' and trashed = false")
                .setSpaces("drive")
                .setFields("files(id, name)")
                .execute()
            
            val file = result.files?.firstOrNull()
            file?.id
        } catch (e: Exception) {
            Log.e(tag, "Failed to search Drive", e)
            null
        }
    }

    private suspend fun ensureMasterDataExists(service: Sheets, spreadsheetId: String) {
        Log.d(tag, "[SYNC] Verifying Master Data (ID: $spreadsheetId)")
        
        // 1. Initialize Categories
        val localCategories = repositoryProvider.get().getCategories().first()
        localCategories.forEach { category ->
            addCategoryToSheetInternal(service, spreadsheetId, category)
        }

        // 2. Initialize Friends
        val localFriends = repositoryProvider.get().getAllFriends().first()
        localFriends.forEach { friendName ->
            val friendObj = repositoryProvider.get().getFriendByName(friendName)
            if (friendObj != null) {
                updateFriendSummaryInSheetInternal(service, spreadsheetId, friendObj)
            }
        }
        
        // Verification: Ensure the required sheets exist after operations
        val spreadsheet = service.spreadsheets().get(spreadsheetId).execute()
        val existingNames = spreadsheet.sheets.map { it.properties.title }
        val required = listOf("Transactions", "Categories", "Friends", "Budgets", "Settings", "App_Metadata", "Payment_Methods", "Autopay")
        val missing = required.filter { it !in existingNames }
        
        if (missing.isNotEmpty()) {
            throw IllegalStateException("Spreadsheet initialization failed. Missing sheets: $missing")
        }
        
        Log.i(tag, "[SYNC] Master data verification completed.")
    }

    /** Spreadsheet-friendly booleans: the Categories tab is something the user reads. */
    private fun Boolean.asSheetFlag(): String = if (this) "Yes" else "No"

    /**
     * One row of the Categories tab: id, name, parent name, and the two scope flags.
     *
     * The parent is stored by *name* rather than id, because ids are device-local — a restore
     * onto a fresh install generates new ones, and a name is what survives the round trip.
     */
    private suspend fun categoryRowValues(
        category: com.nothing.expensetracker.data.local.Category
    ): List<String> {
        val parentName = category.parentId
            ?.let { repositoryProvider.get().getCategoryById(it)?.name }
            .orEmpty()
        return listOf(
            category.id.toString(),
            category.name,
            parentName,
            category.usedForDebit.asSheetFlag(),
            category.usedForCredit.asSheetFlag()
        )
    }

    private suspend fun backfillCategoryMetadata(
        service: Sheets,
        spreadsheetId: String,
        rowIndex: Int,
        category: com.nothing.expensetracker.data.local.Category
    ) {
        try {
            val values = categoryRowValues(category).drop(2) // C..E only; leave id and name alone
            service.spreadsheets().values()
                .update(spreadsheetId, "Categories!C$rowIndex:E$rowIndex", ValueRange().setValues(listOf(values)))
                .setValueInputOption("RAW")
                .execute()
        } catch (e: Exception) {
            Log.e(tag, "[CATEGORY_SYNC] Could not backfill metadata for ${category.name}", e)
        }
    }

    /**
     * Internal implementation of category addition with shared service instance
     */
    private suspend fun addCategoryToSheetInternal(
        service: Sheets, 
        spreadsheetId: String, 
        category: com.nothing.expensetracker.data.local.Category
    ): Boolean {
        try {
            var existingRow = findRowIndex(service, spreadsheetId, "Categories", category.id.toString(), "A")
            if (existingRow == null) {
                existingRow = findRowIndex(service, spreadsheetId, "Categories", category.name, "B")
            }

            if (existingRow != null) {
                // The row is already there, but on a sheet written by an older version columns
                // C..E are blank. Backfill them so hierarchy and Debit/Credit scope survive a
                // restore instead of silently defaulting.
                backfillCategoryMetadata(service, spreadsheetId, existingRow, category)
                Log.d(tag, "[CATEGORY_SYNC] Skipped Duplicate | ID: ${category.id} | Name: ${category.name} | Row: $existingRow")
                return true
            }

            Log.i(tag, "[CATEGORY_SYNC] Operation: CREATE | ID: ${category.id} | Name: ${category.name}")
            val rowValues = categoryRowValues(category)
            val body = ValueRange().setValues(listOf(rowValues))
            val response = service.spreadsheets().values().append(spreadsheetId, "Categories!A2", body)
                .setValueInputOption("RAW")
                .execute()
            
            Log.i(tag, "[CATEGORY_SYNC] Google API Response: Success | Created Range: ${response.updates?.updatedRange}")
            return true
        } catch (e: Exception) {
            Log.e(tag, "[CATEGORY_SYNC] Operation: CREATE | Failed to add category: ${category.name}", e)
            return false
        }
    }

    private suspend fun updateFriendSummaryInSheetInternal(
        service: Sheets,
        spreadsheetId: String,
        friend: com.nothing.expensetracker.data.local.Friend,
        triggeredByTransactionId: Long? = null
    ): Boolean {
        val triggerInfo = if (triggeredByTransactionId != null) " | Triggered By Transaction ID: $triggeredByTransactionId" else ""
        Log.i(tag, "[FRIEND_SYNC] Operation: UPDATE | Friend ID: ${friend.id} | Name: ${friend.name}$triggerInfo")
        try {
            val balances = repositoryProvider.get().getFriendBalances().first()
            val balance = balances.find { it.friendName == friend.name } 
                ?: com.nothing.expensetracker.data.local.FriendBalance(friend.name, 0.0, 0.0, 0.0)

            val status = when {
                balance.outstandingBalance > 0 -> "Friend Owes You"
                balance.outstandingBalance < 0 -> "You Owe Friend"
                else -> "Settled"
            }

            val now = java.text.SimpleDateFormat("dd-MMM-yyyy HH:mm", Locale.getDefault()).format(Date())
            val rowValues = listOf(
                friend.id.toString(),
                friend.name,
                balance.totalDebit.toString(),
                balance.totalCredit.toString(),
                balance.outstandingBalance.toString(),
                status,
                now
            )

            val body = ValueRange().setValues(listOf(rowValues))
            var rowIndex = findRowIndex(service, spreadsheetId, "Friends", friend.id.toString(), "A")
            if (rowIndex == null) {
                rowIndex = findRowIndex(service, spreadsheetId, "Friends", friend.name, "B")
            }

            return if (rowIndex != null) {
                val range = "Friends!A$rowIndex:G$rowIndex"
                service.spreadsheets().values().update(spreadsheetId, range, body)
                    .setValueInputOption("USER_ENTERED")
                    .execute()
                true
            } else {
                addFriendToSheetInternal(service, spreadsheetId, friend)
                true
            }
        } catch (e: Exception) {
            Log.e(tag, "[FRIEND_SYNC] Failed to update friend summary: ${friend.name}", e)
            return false
        }
    }

    private suspend fun addFriendToSheetInternal(
        service: Sheets,
        spreadsheetId: String,
        friend: com.nothing.expensetracker.data.local.Friend
    ): Boolean {
        try {
            val existingRow = findRowIndex(service, spreadsheetId, "Friends", friend.name, "B")
            if (existingRow != null) return true

            val now = java.text.SimpleDateFormat("dd-MMM-yyyy HH:mm", Locale.getDefault()).format(Date())
            val rowValues = listOf(
                friend.id.toString(),
                friend.name,
                "0", "0", "0", "Settled", now
            )
            val body = ValueRange().setValues(listOf(rowValues))
            service.spreadsheets().values().append(spreadsheetId, "Friends!A2", body)
                .setValueInputOption("USER_ENTERED")
                .execute()
            return true
        } catch (e: Exception) {
            Log.e(tag, "[FRIEND_SYNC] Failed to add friend: ${friend.name}", e)
            return false
        }
    }

    private fun ensureInitialDataExists(service: Sheets, spreadsheetId: String) {
        val now = java.text.SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault()).format(Date())

        val settings = mapOf(
            // Written as a bare number so it round-trips cleanly; syncOpeningBalancesToSheet
            // keeps these rows up to date from here on.
            OPENING_BANK_KEY to appPrefs.openingBankBalance.value.toString(),
            OPENING_CASH_KEY to appPrefs.openingCashBalance.value.toString(),
            "Currency" to "INR",
            "Currency Symbol" to "₹",
            "Date Format" to "DD/MM/YYYY",
            "Last Sync" to "Never"
        )
        ensureSheetData(service, spreadsheetId, "Settings", settings)

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
            Log.e(tag, "Failed to ensure data for sheet: $sheetName", e)
        }
    }

    private fun ensureHeadersExist(service: Sheets, spreadsheetId: String) {
        val headerMap = mapOf(
            "Transactions" to listOf(
                // "Description" is appended last on purpose: existing sheets already have data
                // under A..K, so adding the column at the end leaves every old row aligned.
                "Transaction ID", "Date", "Type", "Category", "Amount",
                "Payment Method", "Friend Name", "Notes", "Created At",
                "Updated At", "Sync Status", "Description"
            ),
            // Columns C..E were appended after the fact, so existing sheets keep A..B aligned
            // and simply get the new cells backfilled on the next sync.
            "Categories" to listOf(
                "Category ID", "Category Name", "Parent Category",
                "Used For Debit", "Used For Credit"
            ),
            "Friends" to listOf(
                "Friend ID", "Friend Name", "Total Debit", "Total Credit", 
                "Outstanding Balance", "Status", "Last Updated"
            ),
            // "Alert Threshold" appended last: existing sheets keep A..E aligned.
            "Budgets" to listOf("Budget ID", "Category", "Amount", "Month", "Year", "Alert Threshold"),
            "Settings" to listOf("Setting", "Value"),
            "App_Metadata" to listOf("Key", "Value"),
            "Payment_Methods" to listOf("Name", "Is System"),
            "Autopay" to listOf(
                "Rule ID", "Description", "Category", "Type", "Amount",
                "Payment Method", "Friend Name", "Day Of Month", "Active", "Last Run Month"
            )
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

    private fun applyHeaderFormatting(service: Sheets, spreadsheetId: String) {
        try {
            val spreadsheet = service.spreadsheets().get(spreadsheetId).execute()
            val sheets = spreadsheet.sheets
            val requests = mutableListOf<Request>()

            sheets.forEach { sheet ->
                val sheetId = sheet.properties.sheetId
                
                requests.add(Request().setRepeatCell(
                    RepeatCellRequest()
                        .setRange(GridRange()
                            .setSheetId(sheetId)
                            .setStartRowIndex(0)
                            .setEndRowIndex(1)
                        )
                        .setCell(CellData()
                            .setUserEnteredFormat(CellFormat()
                                .setBackgroundColor(Color().setRed(0.12f).setGreen(0.23f).setBlue(0.54f))
                                .setTextFormat(TextFormat()
                                    .setForegroundColor(Color().setRed(1.0f).setGreen(1.0f).setBlue(1.0f))
                                    .setBold(true)
                                    .setFontSize(11)
                                )
                                .setHorizontalAlignment("CENTER")
                                .setVerticalAlignment("MIDDLE")
                            )
                        )
                        .setFields("userEnteredFormat(backgroundColor,textFormat,horizontalAlignment,verticalAlignment)")
                ))

                requests.add(Request().setUpdateSheetProperties(
                    UpdateSheetPropertiesRequest()
                        .setProperties(SheetProperties()
                            .setSheetId(sheetId)
                            .setGridProperties(GridProperties().setFrozenRowCount(1))
                        )
                        .setFields("gridProperties.frozenRowCount")
                ))
            }

            if (requests.isNotEmpty()) {
                val batchUpdate = BatchUpdateSpreadsheetRequest().setRequests(requests)
                service.spreadsheets().batchUpdate(spreadsheetId, batchUpdate).execute()
            }
        } catch (e: Exception) {
            Log.e(tag, "Failed to apply header formatting", e)
        }
    }

    private fun applyColumnWidths(service: Sheets, spreadsheetId: String) {
        try {
            val spreadsheet = service.spreadsheets().get(spreadsheetId).execute()
            val sheets = spreadsheet.sheets
            val requests = mutableListOf<Request>()

            sheets.forEach { sheet ->
                val sheetId = sheet.properties.sheetId
                val sheetTitle = sheet.properties.title
                
                val widths = when (sheetTitle) {
                    "Transactions" -> listOf(120, 120, 90, 140, 100, 140, 160, 250, 180, 180, 120, 250)
                    "Categories" -> listOf(100, 220, 180, 130, 130)
                    "Friends" -> listOf(90, 220, 130, 130, 170, 180, 180)
                    "Budgets" -> listOf(120, 160, 120, 80, 80)
                    "Settings", "App_Metadata" -> listOf(220, 220)
                    "Payment_Methods" -> listOf(220, 110)
                    "Autopay" -> listOf(100, 220, 140, 90, 110, 140, 160, 120, 90, 140)
                    else -> emptyList()
                }

                widths.forEachIndexed { index, width ->
                    requests.add(Request().setUpdateDimensionProperties(
                        UpdateDimensionPropertiesRequest()
                            .setRange(DimensionRange()
                                .setSheetId(sheetId)
                                .setDimension("COLUMNS")
                                .setStartIndex(index)
                                .setEndIndex(index + 1)
                            )
                            .setProperties(DimensionProperties().setPixelSize(width))
                            .setFields("pixelSize")
                    ))
                }
            }

            if (requests.isNotEmpty()) {
                val batchUpdate = BatchUpdateSpreadsheetRequest().setRequests(requests)
                service.spreadsheets().batchUpdate(spreadsheetId, batchUpdate).execute()
            }
        } catch (e: Exception) {
            Log.e(tag, "Failed to apply column widths", e)
        }
    }

    private fun applyAlternatingRowColors(service: Sheets, spreadsheetId: String) {
        try {
            val spreadsheet = service.spreadsheets().get(spreadsheetId).execute()
            val sheets = spreadsheet.sheets
            val requests = mutableListOf<Request>()

            sheets.forEach { sheet ->
                val sheetId = sheet.properties.sheetId
                requests.add(Request().setAddBanding(
                    AddBandingRequest().setBandedRange(
                        BandedRange()
                            .setRange(GridRange()
                                .setSheetId(sheetId)
                                .setStartRowIndex(1)
                                .setStartColumnIndex(0)
                                .setEndColumnIndex(26)
                            )
                            .setRowProperties(BandingProperties()
                                .setFirstBandColor(Color().setRed(1.0f).setGreen(1.0f).setBlue(1.0f))
                                .setSecondBandColor(Color().setRed(0.95f).setGreen(0.95f).setBlue(0.95f))
                            )
                    )
                ))
            }

            if (requests.isNotEmpty()) {
                val batchUpdate = BatchUpdateSpreadsheetRequest().setRequests(requests)
                service.spreadsheets().batchUpdate(spreadsheetId, batchUpdate).execute()
            }
        } catch (e: Exception) {
            Log.e(tag, "Failed to apply alternating row colors", e)
        }
    }

    private fun applyValueFormatting(service: Sheets, spreadsheetId: String) {
        try {
            val spreadsheet = service.spreadsheets().get(spreadsheetId).execute()
            val transactionsSheetId = spreadsheet.sheets.find { it.properties.title == "Transactions" }?.properties?.sheetId
            if (transactionsSheetId != null) {
                val requests = mutableListOf<Request>()
                requests.add(Request().setRepeatCell(
                    RepeatCellRequest()
                        .setRange(GridRange().setSheetId(transactionsSheetId).setStartRowIndex(1).setStartColumnIndex(4).setEndColumnIndex(5))
                        .setCell(CellData().setUserEnteredFormat(CellFormat().setNumberFormat(NumberFormat().setType("CURRENCY").setPattern("₹#,##0.00"))))
                        .setFields("userEnteredFormat.numberFormat")
                ))
                requests.add(Request().setRepeatCell(
                    RepeatCellRequest()
                        .setRange(GridRange().setSheetId(transactionsSheetId).setStartRowIndex(1).setStartColumnIndex(1).setEndColumnIndex(2))
                        .setCell(CellData().setUserEnteredFormat(CellFormat().setNumberFormat(NumberFormat().setType("DATE").setPattern("dd-MMM-yyyy"))))
                        .setFields("userEnteredFormat.numberFormat")
                ))
                service.spreadsheets().batchUpdate(spreadsheetId, BatchUpdateSpreadsheetRequest().setRequests(requests)).execute()
            }
        } catch (e: Exception) {
            Log.e(tag, "Failed to apply value formatting", e)
        }
    }

    private fun applyConditionalFormatting(service: Sheets, spreadsheetId: String) {
        try {
            val spreadsheet = service.spreadsheets().get(spreadsheetId).execute()
            val sheet = spreadsheet.sheets.find { it.properties.title == "Transactions" }
            val sheetId = sheet?.properties?.sheetId ?: return

            if (!sheet.conditionalFormats.isNullOrEmpty()) return

            val requests = mutableListOf<Request>()
            fun createRule(columnIndex: Int, text: String, bgColor: Color, textColor: Color? = null): ConditionalFormatRule {
                val condition = ConditionValue().setUserEnteredValue(text)
                val rule = BooleanRule()
                    .setCondition(BooleanCondition().setType("TEXT_EQ").setValues(listOf(condition)))
                    .setFormat(CellFormat().setBackgroundColor(bgColor))
                textColor?.let { rule.format.setTextFormat(TextFormat().setForegroundColor(it)) }
                return ConditionalFormatRule()
                    .setRanges(listOf(GridRange().setSheetId(sheetId).setStartRowIndex(1).setStartColumnIndex(columnIndex).setEndColumnIndex(columnIndex + 1)))
                    .setBooleanRule(rule)
            }

            requests.add(Request().setAddConditionalFormatRule(AddConditionalFormatRuleRequest().setRule(createRule(2, "Debit", Color().setRed(0.97f).setGreen(0.84f).setBlue(0.85f)))))
            requests.add(Request().setAddConditionalFormatRule(AddConditionalFormatRuleRequest().setRule(createRule(2, "Credit", Color().setRed(0.83f).setGreen(0.93f).setBlue(0.85f)))))

            service.spreadsheets().batchUpdate(spreadsheetId, BatchUpdateSpreadsheetRequest().setRequests(requests)).execute()
        } catch (e: Exception) {
            Log.e(tag, "Failed to apply conditional formatting", e)
        }
    }

    private fun applyFilters(service: Sheets, spreadsheetId: String) {
        try {
            val spreadsheet = service.spreadsheets().get(spreadsheetId).execute()
            val sheet = spreadsheet.sheets.find { it.properties.title == "Transactions" } ?: return
            if (sheet.basicFilter == null) {
                val request = Request().setSetBasicFilter(SetBasicFilterRequest().setFilter(BasicFilter().setRange(GridRange().setSheetId(sheet.properties.sheetId).setStartRowIndex(0).setEndColumnIndex(11))))
                service.spreadsheets().batchUpdate(spreadsheetId, BatchUpdateSpreadsheetRequest().setRequests(listOf(request))).execute()
            }
        } catch (e: Exception) {
            Log.e(tag, "Failed to apply basic filter", e)
        }
    }

    private fun applyCellAlignment(service: Sheets, spreadsheetId: String) {
        try {
            val spreadsheet = service.spreadsheets().get(spreadsheetId).execute()
            val sheetId = spreadsheet.sheets.find { it.properties.title == "Transactions" }?.properties?.sheetId ?: return
            val requests = mutableListOf<Request>()
            val alignmentMap = mapOf(0 to "CENTER", 1 to "CENTER", 2 to "CENTER", 3 to "LEFT", 4 to "RIGHT", 5 to "CENTER", 6 to "LEFT", 7 to "LEFT", 8 to "CENTER", 9 to "CENTER", 10 to "CENTER")
            alignmentMap.forEach { (columnIndex, alignment) ->
                requests.add(Request().setRepeatCell(RepeatCellRequest().setRange(GridRange().setSheetId(sheetId).setStartRowIndex(1).setStartColumnIndex(columnIndex).setEndColumnIndex(columnIndex + 1)).setCell(CellData().setUserEnteredFormat(CellFormat().setHorizontalAlignment(alignment).setVerticalAlignment("MIDDLE"))).setFields("userEnteredFormat(horizontalAlignment,verticalAlignment)")))
            }
            service.spreadsheets().batchUpdate(spreadsheetId, BatchUpdateSpreadsheetRequest().setRequests(requests)).execute()
            
            val friendsSheet = spreadsheet.sheets.find { it.properties.title == "Friends" }
            val friendsSheetId = friendsSheet?.properties?.sheetId
            if (friendsSheetId != null) {
                val friendsRequests = mutableListOf<Request>()
                val friendsAlignmentMap = mapOf(0 to "CENTER", 1 to "LEFT", 2 to "RIGHT", 3 to "RIGHT", 4 to "RIGHT", 5 to "CENTER", 6 to "CENTER")
                friendsAlignmentMap.forEach { (columnIndex, alignment) ->
                    friendsRequests.add(Request().setRepeatCell(RepeatCellRequest().setRange(GridRange().setSheetId(friendsSheetId).setStartRowIndex(1).setStartColumnIndex(columnIndex).setEndColumnIndex(columnIndex + 1)).setCell(CellData().setUserEnteredFormat(CellFormat().setHorizontalAlignment(alignment).setVerticalAlignment("MIDDLE"))).setFields("userEnteredFormat(horizontalAlignment,verticalAlignment)")))
                }
                service.spreadsheets().batchUpdate(spreadsheetId, BatchUpdateSpreadsheetRequest().setRequests(friendsRequests)).execute()
            }
        } catch (e: Exception) {
            Log.e(tag, "Failed to apply cell alignment", e)
        }
    }

    private fun applyFinalPolish(service: Sheets, spreadsheetId: String) {
        try {
            val spreadsheet = service.spreadsheets().get(spreadsheetId).execute()
            val sheets = spreadsheet.sheets
            val requests = mutableListOf<Request>()
            sheets.forEach { sheet ->
                val sheetId = sheet.properties.sheetId
                requests.add(Request().setRepeatCell(RepeatCellRequest().setRange(GridRange().setSheetId(sheetId).setStartRowIndex(0).setEndRowIndex(1000)).setCell(CellData().setUserEnteredFormat(CellFormat().setTextFormat(TextFormat().setFontFamily("Arial").setFontSize(11)).setVerticalAlignment("MIDDLE"))).setFields("userEnteredFormat(textFormat.fontFamily,textFormat.fontSize,verticalAlignment)")))
            }
            service.spreadsheets().batchUpdate(spreadsheetId, BatchUpdateSpreadsheetRequest().setRequests(requests)).execute()
        } catch (e: Exception) {
            Log.e(tag, "Failed to apply final polish", e)
        }
    }

    private fun ensureRequiredSheetsExist(service: Sheets, spreadsheetId: String) {
        try {
            val spreadsheet = service.spreadsheets()[spreadsheetId].execute()
            val existingSheets = spreadsheet.sheets
            val existingSheetNames = existingSheets.map { it.properties.title }
            val requiredSheets = listOf("Transactions", "Categories", "Friends", "Budgets", "Settings", "App_Metadata", "Payment_Methods", "Autopay")
            
            // 1. Check if all required sheets already exist
            val missingSheets = requiredSheets.filter { it !in existingSheetNames }
            
            if (missingSheets.isNotEmpty()) {
                val requests = missingSheets.map { name ->
                    Request().setAddSheet(AddSheetRequest().setProperties(SheetProperties().setTitle(name)))
                }
                service.spreadsheets().batchUpdate(spreadsheetId, BatchUpdateSpreadsheetRequest().setRequests(requests)).execute()
                Log.d(tag, "Created missing sheets: $missingSheets")
            }

            // 2. Re-fetch current state to get fresh sheet IDs for cleanup
            val updatedSpreadsheet = service.spreadsheets()[spreadsheetId].execute()
            val updatedSheets = updatedSpreadsheet.sheets
            val updatedSheetNames = updatedSheets.map { it.properties.title }

            // 3. Cleanup "Sheet1" ONLY if we have at least one other required sheet to keep
            if ("Sheet1" in updatedSheetNames && updatedSheetNames.size > 1) {
                val sheet1 = updatedSheets.find { it.properties.title == "Sheet1" }
                val sheet1Id = sheet1?.properties?.sheetId
                
                if (sheet1Id != null) {
                    val response = service.spreadsheets().values()
                        .get(spreadsheetId, "Sheet1!A1:E10")
                        .execute()
                    
                    if (response.getValues().isNullOrEmpty()) {
                        Log.d(tag, "Sheet1 is empty. Deleting...")
                        val deleteRequest = Request().setDeleteSheet(DeleteSheetRequest().setSheetId(sheet1Id))
                        service.spreadsheets().batchUpdate(spreadsheetId, BatchUpdateSpreadsheetRequest().setRequests(listOf(deleteRequest))).execute()
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(tag, "Failed to ensure required sheets exist", e)
        }
    }

    private suspend fun findRowIndex(service: Sheets, spreadsheetId: String, sheetName: String, value: String, column: String = "A"): Int? {
        return try {
            val range = "$sheetName!$column:$column"
            val response = service.spreadsheets().values().get(spreadsheetId, range).execute()
            val values = response.getValues()
            if (values != null) {
                for (i in values.indices) {
                    if (values[i].isNotEmpty()) {
                        val cellValue = values[i][0].toString().trim()
                        if (cellValue.equals(value.trim(), ignoreCase = column == "B")) return i + 1
                    }
                }
            }
            null
        } catch (e: Exception) {
            Log.e(tag, "Error finding row in $sheetName for $value", e)
            null
        }
    }

    suspend fun syncBudgetToSheet(budget: com.nothing.expensetracker.data.local.Budget): Boolean = withContext(Dispatchers.IO) {
        val service = getSheetsService() ?: return@withContext false
        val spreadsheetId = appPrefs.spreadsheetId.value ?: return@withContext false
        try {
            val budgetId = if (budget.categoryName == null) "OVERALL" else "CAT_${budget.categoryName}"
            // lastAlertThreshold rides along so a restored budget doesn't re-fire an alert the
            // user has already seen.
            val rowValues = listOf(budgetId, budget.categoryName ?: "Overall", budget.amount.toString(), budget.month.toString(), budget.year.toString(), budget.lastAlertThreshold.toString())
            val body = ValueRange().setValues(listOf(rowValues))
            val rowIndex = findRowIndex(service, spreadsheetId, "Budgets", budgetId)
            if (rowIndex != null) {
                service.spreadsheets().values().update(spreadsheetId, "Budgets!A$rowIndex:F$rowIndex", body).setValueInputOption("RAW").execute()
            } else {
                service.spreadsheets().values().append(spreadsheetId, "Budgets!A2", body).setValueInputOption("RAW").execute()
            }
            true
        } catch (e: Exception) {
            Log.e(tag, "Failed to sync budget to sheet", e)
            false
        }
    }

    suspend fun deleteBudgetFromSheet(categoryName: String?): Boolean = withContext(Dispatchers.IO) {
        val service = getSheetsService() ?: return@withContext false
        val spreadsheetId = appPrefs.spreadsheetId.value ?: return@withContext false
        try {
            val budgetId = if (categoryName == null) "OVERALL" else "CAT_$categoryName"
            val rowIndex = findRowIndex(service, spreadsheetId, "Budgets", budgetId)
            if (rowIndex != null) {
                val sheetId = service.spreadsheets().get(spreadsheetId).execute().sheets.find { it.properties.title == "Budgets" }?.properties?.sheetId ?: return@withContext false
                val deleteRequest = Request().setDeleteDimension(DeleteDimensionRequest().setRange(DimensionRange().setSheetId(sheetId).setDimension("ROWS").setStartIndex(rowIndex - 1).setEndIndex(rowIndex)))
                service.spreadsheets().batchUpdate(spreadsheetId, BatchUpdateSpreadsheetRequest().setRequests(listOf(deleteRequest))).execute()
            }
            true
        } catch (e: Exception) {
            Log.e(tag, "Failed to delete budget from sheet", e)
            false
        }
    }

    suspend fun addCategoryToSheet(category: com.nothing.expensetracker.data.local.Category): Boolean = withContext(Dispatchers.IO) {
        val service = getSheetsService() ?: return@withContext false
        val spreadsheetId = appPrefs.spreadsheetId.value ?: return@withContext false
        addCategoryToSheetInternal(service, spreadsheetId, category)
    }

    suspend fun updateCategoryInSheet(oldName: String, category: com.nothing.expensetracker.data.local.Category): Boolean = withContext(Dispatchers.IO) {
        Log.i(tag, "[CATEGORY_SYNC] Operation: UPDATE | ID: ${category.id} | Old Name: $oldName -> New Name: ${category.name}")
        val service = getSheetsService() ?: return@withContext false
        val spreadsheetId = appPrefs.spreadsheetId.value ?: return@withContext false
        try {
            val rowIndex = findRowIndex(service, spreadsheetId, "Categories", oldName, "B")
            if (rowIndex != null) {
                val rowValues = categoryRowValues(category)
                val body = ValueRange().setValues(listOf(rowValues))
                val response = service.spreadsheets().values().update(spreadsheetId, "Categories!A$rowIndex:E$rowIndex", body).setValueInputOption("RAW").execute()
                Log.i(tag, "[CATEGORY_SYNC] Google API Response: Success | Updated Range: ${response.updatedRange}")
                true
            } else {
                Log.w(tag, "[CATEGORY_SYNC] Category not found in sheet. Creating instead.")
                addCategoryToSheet(category)
            }
        } catch (e: Exception) {
            Log.e(tag, "[CATEGORY_SYNC] Operation: UPDATE | Failed to update category: $oldName", e)
            false
        }
    }

    suspend fun deleteCategoryFromSheet(categoryName: String): Boolean = withContext(Dispatchers.IO) {
        val service = getSheetsService() ?: return@withContext false
        val spreadsheetId = appPrefs.spreadsheetId.value ?: return@withContext false
        try {
            val rowIndex = findRowIndex(service, spreadsheetId, "Categories", categoryName, "B")
            if (rowIndex != null) {
                val sheetId = service.spreadsheets().get(spreadsheetId).execute().sheets.find { it.properties.title == "Categories" }?.properties?.sheetId ?: return@withContext false
                val deleteRequest = Request().setDeleteDimension(DeleteDimensionRequest().setRange(DimensionRange().setSheetId(sheetId).setDimension("ROWS").setStartIndex(rowIndex - 1).setEndIndex(rowIndex)))
                service.spreadsheets().batchUpdate(spreadsheetId, BatchUpdateSpreadsheetRequest().setRequests(listOf(deleteRequest))).execute()
            }
            true
        } catch (e: Exception) {
            Log.e(tag, "Failed to delete category from sheet: $categoryName", e)
            false
        }
    }

    suspend fun addFriendToSheet(friend: com.nothing.expensetracker.data.local.Friend): Boolean = withContext(Dispatchers.IO) {
        val service = getSheetsService() ?: return@withContext false
        val spreadsheetId = appPrefs.spreadsheetId.value ?: return@withContext false
        addFriendToSheetInternal(service, spreadsheetId, friend)
    }

    suspend fun updateFriendSummaryInSheet(friend: com.nothing.expensetracker.data.local.Friend, triggeredByTransactionId: Long? = null): Boolean = withContext(Dispatchers.IO) {
        val service = getSheetsService() ?: return@withContext false
        val spreadsheetId = appPrefs.spreadsheetId.value ?: return@withContext false
        updateFriendSummaryInSheetInternal(service, spreadsheetId, friend, triggeredByTransactionId)
    }

    suspend fun deleteFriendFromSheet(friendId: String, friendName: String): Boolean = withContext(Dispatchers.IO) {
        val service = getSheetsService() ?: return@withContext false
        val spreadsheetId = appPrefs.spreadsheetId.value ?: return@withContext false
        try {
            var rowIndex = findRowIndex(service, spreadsheetId, "Friends", friendId, "A")
            if (rowIndex == null) rowIndex = findRowIndex(service, spreadsheetId, "Friends", friendName, "B")
            if (rowIndex != null) {
                val sheetId = service.spreadsheets().get(spreadsheetId).execute().sheets.find { it.properties.title == "Friends" }?.properties?.sheetId ?: return@withContext false
                val deleteRequest = Request().setDeleteDimension(DeleteDimensionRequest().setRange(DimensionRange().setSheetId(sheetId).setDimension("ROWS").setStartIndex(rowIndex - 1).setEndIndex(rowIndex)))
                service.spreadsheets().batchUpdate(spreadsheetId, BatchUpdateSpreadsheetRequest().setRequests(listOf(deleteRequest))).execute()
            }
            true
        } catch (e: Exception) {
            Log.e(tag, "Failed to delete friend ID $friendId from sheet", e)
            false
        }
    }

    suspend fun addTransactionToSheet(transaction: com.nothing.expensetracker.data.local.Expense): Boolean = withContext(Dispatchers.IO) {
        Log.i(tag, "[SYNC] Processing Transaction ID: ${transaction.id}")
        val service = getSheetsService() ?: return@withContext false
        val spreadsheetId = appPrefs.spreadsheetId.value ?: return@withContext false
        try {
            val dateFormat = java.text.SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            val formattedDate = dateFormat.format(Date(transaction.timestamp))
            val rowValues = listOf(listOf(transaction.id.toString(), formattedDate, transaction.type, transaction.category, transaction.amount.toString(), transaction.paymentMethod, transaction.friendId ?: "", transaction.notes, transaction.timestamp.toString(), transaction.timestamp.toString(), "Synced", transaction.description))
            val body = ValueRange().setValues(rowValues)
            val response = service.spreadsheets().values().append(spreadsheetId, "Transactions!A2", body).setValueInputOption("USER_ENTERED").execute()
            if (response != null && response.updates?.updatedRows != null && response.updates.updatedRows > 0) {
                Log.i(tag, "[SYNC] Upload success | ID: ${transaction.id}")
                true
            } else false
        } catch (e: Exception) {
            Log.e(tag, "[SYNC] Upload failure | ID: ${transaction.id}", e)
            false
        }
    }

    suspend fun updateTransactionInSheet(transaction: com.nothing.expensetracker.data.local.Expense): Boolean = withContext(Dispatchers.IO) {
        val service = getSheetsService() ?: return@withContext false
        val spreadsheetId = appPrefs.spreadsheetId.value ?: return@withContext false
        try {
            val rowIndex = findRowIndex(service, spreadsheetId, "Transactions", transaction.id.toString())
            if (rowIndex == null) return@withContext false
            val dateFormat = java.text.SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            val formattedDate = dateFormat.format(Date(transaction.timestamp))
            val values = listOf(listOf(transaction.id.toString(), formattedDate, transaction.type, transaction.category, transaction.amount.toString(), transaction.paymentMethod, transaction.friendId ?: "", transaction.notes, transaction.timestamp.toString(), transaction.timestamp.toString(), "Synced", transaction.description))
            val body = ValueRange().setValues(values)
            service.spreadsheets().values().update(spreadsheetId, "Transactions!A$rowIndex:L$rowIndex", body).setValueInputOption("USER_ENTERED").execute()
            true
        } catch (e: Exception) {
            Log.e(tag, "Failed to update transaction in sheet", e)
            false
        }
    }

    suspend fun deleteTransactionFromSheet(transactionId: String): Boolean = withContext(Dispatchers.IO) {
        val service = getSheetsService() ?: return@withContext false
        val spreadsheetId = appPrefs.spreadsheetId.value ?: return@withContext false
        try {
            val rowIndex = findRowIndex(service, spreadsheetId, "Transactions", transactionId)
            if (rowIndex != null) {
                val sheetId = service.spreadsheets().get(spreadsheetId).execute().sheets.find { it.properties.title == "Transactions" }?.properties?.sheetId ?: return@withContext false
                val deleteRequest = Request().setDeleteDimension(DeleteDimensionRequest().setRange(DimensionRange().setSheetId(sheetId).setDimension("ROWS").setStartIndex(rowIndex - 1).setEndIndex(rowIndex)))
                service.spreadsheets().batchUpdate(spreadsheetId, BatchUpdateSpreadsheetRequest().setRequests(listOf(deleteRequest))).execute()
            }
            true
        } catch (e: Exception) {
            Log.e(tag, "Failed to delete transaction from sheet", e)
            false
        }
    }

    // ---------------------------------------------------------------------------------
    // Settings tab (device -> sheet)
    //
    // ensureInitialDataExists only *appends* keys that are absent, so a value written when the
    // spreadsheet was created was never updated again. These methods keep it current.
    // ---------------------------------------------------------------------------------

    /** Writes a Settings key, updating the existing row or appending one if the key is new. */
    private suspend fun upsertSetting(
        service: Sheets,
        spreadsheetId: String,
        key: String,
        value: String
    ) {
        try {
            val rowIndex = findRowIndex(service, spreadsheetId, "Settings", key, "A")
            val body = ValueRange().setValues(listOf(listOf(key, value)))
            if (rowIndex != null) {
                service.spreadsheets().values()
                    .update(spreadsheetId, "Settings!A$rowIndex:B$rowIndex", body)
                    .setValueInputOption("RAW")
                    .execute()
            } else {
                service.spreadsheets().values()
                    .append(spreadsheetId, "Settings!A2:B", body)
                    .setValueInputOption("RAW")
                    .execute()
            }
        } catch (e: Exception) {
            Log.e(tag, "[SETTINGS_SYNC] Failed to write '$key'", e)
        }
    }

    /**
     * Pushes the opening balances to the Settings tab.
     *
     * These live in app preferences rather than the database, so without this they are the one
     * piece of state a reinstall cannot recover — the transactions come back from the sheet while
     * the balance they build on top of does not.
     */
    suspend fun syncOpeningBalancesToSheet(): Boolean = withContext(Dispatchers.IO) {
        val service = getSheetsService() ?: return@withContext false
        val spreadsheetId = appPrefs.spreadsheetId.value ?: return@withContext false
        upsertSetting(service, spreadsheetId, OPENING_BANK_KEY, appPrefs.openingBankBalance.value.toString())
        upsertSetting(service, spreadsheetId, OPENING_CASH_KEY, appPrefs.openingCashBalance.value.toString())
        Log.i(tag, "[SETTINGS_SYNC] Opening balances pushed")
        true
    }

    // ---------------------------------------------------------------------------------
    // Payment methods and autopay rules (device <-> sheet)
    //
    // Neither table carries a syncStatus column, so they cannot take part in the pending-queue
    // model the way transactions do. They are small and change rarely, so the whole set is pushed
    // on every sync cycle instead, upserting row by row.
    //
    // Note the asymmetry: pushes upsert but never delete, so removing a payment method locally
    // does not remove it from the sheet, and a later restore will bring it back. That is the safe
    // direction to err in while deletion propagation is still unproven.
    // ---------------------------------------------------------------------------------

    suspend fun syncPaymentMethodsToSheet(): Boolean = withContext(Dispatchers.IO) {
        val service = getSheetsService() ?: return@withContext false
        val spreadsheetId = appPrefs.spreadsheetId.value ?: return@withContext false
        try {
            val methods = repositoryProvider.get().getAllPaymentMethods().first()
            methods.forEach { method ->
                val row = listOf(method.name, if (method.isSystem) "Yes" else "No")
                upsertRow(service, spreadsheetId, "Payment_Methods", method.name, "A", row, "B")
            }
            Log.i(tag, "[PAYMENT_SYNC] Pushed ${methods.size} payment methods")
            true
        } catch (e: Exception) {
            Log.e(tag, "[PAYMENT_SYNC] Failed", e)
            false
        }
    }

    suspend fun syncAutopayRulesToSheet(): Boolean = withContext(Dispatchers.IO) {
        val service = getSheetsService() ?: return@withContext false
        val spreadsheetId = appPrefs.spreadsheetId.value ?: return@withContext false
        try {
            val rules = repositoryProvider.get().getAllAutopayRulesOnce()
            rules.forEach { rule ->
                val row = listOf(
                    rule.id.toString(),
                    rule.description,
                    rule.category,
                    rule.type,
                    rule.amount.toString(),
                    rule.paymentMethod,
                    rule.friendId.orEmpty(),
                    rule.dayOfMonth.toString(),
                    if (rule.isActive) "Yes" else "No",
                    rule.lastRunMonth.orEmpty()
                )
                upsertRow(service, spreadsheetId, "Autopay", rule.id.toString(), "A", row, "J")
            }
            Log.i(tag, "[AUTOPAY_SYNC] Pushed ${rules.size} autopay rules")
            true
        } catch (e: Exception) {
            Log.e(tag, "[AUTOPAY_SYNC] Failed", e)
            false
        }
    }

    /**
     * Removes a row from one of the whole-set tabs.
     *
     * Payment methods and autopay rules have no syncStatus column, so a deletion cannot be queued
     * the way a transaction's is — it has to be pushed at the moment the user deletes locally. If
     * the device is offline at that point the row stays in the sheet and a later restore brings it
     * back; that is the safe direction to fail in, and it matches how the push already behaves.
     */
    suspend fun deleteRowFromSheet(sheetName: String, key: String): Boolean = withContext(Dispatchers.IO) {
        val service = getSheetsService() ?: return@withContext false
        val spreadsheetId = appPrefs.spreadsheetId.value ?: return@withContext false
        try {
            val rowIndex = findRowIndex(service, spreadsheetId, sheetName, key, "A")
                ?: return@withContext true // already absent
            val sheetId = service.spreadsheets().get(spreadsheetId).execute()
                .sheets.find { it.properties.title == sheetName }?.properties?.sheetId
                ?: return@withContext false
            val request = Request().setDeleteDimension(
                DeleteDimensionRequest().setRange(
                    DimensionRange()
                        .setSheetId(sheetId)
                        .setDimension("ROWS")
                        .setStartIndex(rowIndex - 1)
                        .setEndIndex(rowIndex)
                )
            )
            service.spreadsheets()
                .batchUpdate(spreadsheetId, BatchUpdateSpreadsheetRequest().setRequests(listOf(request)))
                .execute()
            Log.i(tag, "[SYNC] Removed '$key' from $sheetName")
            true
        } catch (e: Exception) {
            Log.e(tag, "[SYNC] Failed to remove '$key' from $sheetName", e)
            false
        }
    }

    suspend fun deletePaymentMethodFromSheet(name: String) = deleteRowFromSheet("Payment_Methods", name)

    suspend fun deleteAutopayRuleFromSheet(ruleId: Long) = deleteRowFromSheet("Autopay", ruleId.toString())

    /** Updates the row whose [keyColumn] holds [key], or appends one if there isn't one yet. */
    private suspend fun upsertRow(
        service: Sheets,
        spreadsheetId: String,
        sheetName: String,
        key: String,
        keyColumn: String,
        values: List<String>,
        lastColumn: String
    ) {
        val body = ValueRange().setValues(listOf(values))
        val rowIndex = findRowIndex(service, spreadsheetId, sheetName, key, keyColumn)
        if (rowIndex != null) {
            service.spreadsheets().values()
                .update(spreadsheetId, "$sheetName!A$rowIndex:$lastColumn$rowIndex", body)
                .setValueInputOption("RAW")
                .execute()
        } else {
            service.spreadsheets().values()
                .append(spreadsheetId, "$sheetName!A2", body)
                .setValueInputOption("RAW")
                .execute()
        }
    }

    // ---------------------------------------------------------------------------------
    // Restore (Sheet <-> device)
    //
    // Pushes have always been one-way: local changes go up, nothing comes back. That means a
    // reinstall loses everything even though the spreadsheet still holds it. These methods close
    // the loop in both directions:
    //
    //   * rows in the sheet that the device lacks are inserted
    //   * records the device has that the SHEET no longer has are deleted locally
    //
    // The deletion half only ever touches records whose syncStatus is "Synced" -- ones known to
    // have reached the sheet. A "Pending" record is legitimately absent upstream because it hasn't
    // been uploaded yet, and deleting it would destroy work. Categories and friends still
    // referenced by a transaction are kept regardless, so nothing is orphaned.
    //
    // Crucially, a failed read is NOT an empty sheet. readRows returns null on error and every
    // deletion pass is skipped in that case; otherwise one network blip would wipe the database.
    // ---------------------------------------------------------------------------------

    /**
     * Reads a whole data range.
     *
     * Returns an empty list when the range is genuinely empty, and **null when the read failed**.
     * Callers must treat null as "I don't know what the sheet contains" and skip deletions.
     */
    private fun readRows(service: Sheets, spreadsheetId: String, range: String): List<List<Any>>? {
        return try {
            service.spreadsheets().values().get(spreadsheetId, range)
                // Without this the API returns FORMATTED_VALUE - what the user sees. The
                // Transactions tab is currency- and date-formatted by applyValueFormatting, so
                // amounts came back as "Rs 5,000.00" and dates as "15-Mar-2026", and every row
                // failed to parse. UNFORMATTED_VALUE returns the underlying number instead.
                .setValueRenderOption("UNFORMATTED_VALUE")
                .execute()
                .getValues()
                ?: emptyList()
        } catch (e: Exception) {
            Log.e(tag, "[RESTORE] Could not read range $range - skipping deletions for it", e)
            null
        }
    }

    /**
     * Reconciles the device against the spreadsheet in both directions.
     *
     * Ordering matters. Categories and friends are restored before transactions, because a
     * transaction references them by name. Deletions run last, after every insert, so a category
     * about to be used by a transaction restored in the same pass is not pruned.
     */
    suspend fun restoreFromSheet(): RestoreSummary = withContext(Dispatchers.IO) {
        val service = getSheetsService() ?: return@withContext RestoreSummary()
        val spreadsheetId = appPrefs.spreadsheetId.value ?: return@withContext RestoreSummary()
        Log.i(tag, "[RESTORE] Starting reconcile against $spreadsheetId")

        restoreOpeningBalances(service, spreadsheetId)

        val categoryRows = readRows(service, spreadsheetId, "Categories!A2:E")
        val friendRows = readRows(service, spreadsheetId, "Friends!A2:G")
        val budgetRows = readRows(service, spreadsheetId, "Budgets!A2:F")
        val transactionRows = readRows(service, spreadsheetId, "Transactions!A2:L")
        val paymentRows = readRows(service, spreadsheetId, "Payment_Methods!A2:B")
        val autopayRows = readRows(service, spreadsheetId, "Autopay!A2:J")

        // Payment methods and categories come before transactions and autopay rules, which
        // reference them by name.
        val added = RestoreSummary(
            categories = restoreCategories(categoryRows.orEmpty()),
            friends = restoreFriends(friendRows.orEmpty()),
            paymentMethods = restorePaymentMethods(paymentRows.orEmpty()),
            budgets = restoreBudgets(budgetRows.orEmpty()),
            transactions = restoreTransactions(transactionRows.orEmpty()),
            autopayRules = restoreAutopayRules(autopayRows.orEmpty())
        )

        val summary = added.copy(
            removedTransactions = transactionRows?.let { pruneTransactions(it) } ?: 0,
            removedBudgets = budgetRows?.let { pruneBudgets(it) } ?: 0,
            removedFriends = friendRows?.let { pruneFriends(it) } ?: 0,
            removedCategories = categoryRows?.let { pruneCategories(it) } ?: 0
        )

        Log.i(tag, "[RESTORE] Finished | $summary")
        summary
    }

    /**
     * Pulls the opening balances back from the Settings tab.
     *
     * Only applied when this device has none set, so a restore never overwrites a figure the user
     * has already corrected on this install. Values are written with a currency prefix for
     * readability, which [toSheetNumber] strips.
     */
    private fun restoreOpeningBalances(service: Sheets, spreadsheetId: String) {
        val rows = readRows(service, spreadsheetId, "Settings!A2:B") ?: return
        val byKey = rows.associate { it.cell(0) to it.cell(1) }

        if (appPrefs.openingBankBalance.value == 0.0) {
            byKey[OPENING_BANK_KEY]?.toSheetNumber()?.let {
                appPrefs.setOpeningBankBalance(it)
                Log.i(tag, "[RESTORE] Opening bank balance recovered from sheet")
            }
        }
        if (appPrefs.openingCashBalance.value == 0.0) {
            byKey[OPENING_CASH_KEY]?.toSheetNumber()?.let {
                appPrefs.setOpeningCashBalance(it)
                Log.i(tag, "[RESTORE] Opening cash balance recovered from sheet")
            }
        }
    }

    // --- Sheet -> device inserts ---

    /**
     * Restores categories, including their Debit/Credit scope and one level of hierarchy.
     *
     * Runs in two passes: top-level categories first, then subcategories, because a child needs
     * its parent's local id to exist before it can point at it. Rows written by a version before
     * columns C..E existed restore as top-level Debit categories, which is what they were.
     */
    private suspend fun restoreCategories(rows: List<List<Any>>): Int {
        val repository = repositoryProvider.get()
        var restored = 0

        rows.filter { it.cell(2).isEmpty() }.forEach { row ->
            if (insertCategoryRow(row, parentId = null)) restored++
        }

        rows.filter { it.cell(2).isNotEmpty() }.forEach { row ->
            val parent = repository.getCategoryByNameCaseInsensitive(row.cell(2))
            if (parent == null) {
                Log.w(tag, "[RESTORE] Skipping child category: parent not found")
                return@forEach
            }
            if (insertCategoryRow(row, parentId = parent.id)) restored++
        }
        return restored
    }

    /** Inserts one Categories row if that name isn't taken. Returns whether it inserted. */
    private suspend fun insertCategoryRow(row: List<Any>, parentId: Long?): Boolean {
        val repository = repositoryProvider.get()
        val name = row.cell(1)
        if (name.isEmpty()) return false
        // Matched by name, not id: names are what transactions reference, and the local
        // default-category seed will have created its own ids already.
        if (repository.getCategoryByNameCaseInsensitive(name) != null) return false

        repository.insertRestoredCategory(
            com.nothing.expensetracker.data.local.Category(
                name = name,
                parentId = parentId,
                usedForDebit = parseScopeFlag(row, DEBIT_FLAG_COLUMN),
                usedForCredit = parseScopeFlag(row, CREDIT_FLAG_COLUMN)
            )
        )
        return true
    }

    private suspend fun restoreFriends(rows: List<List<Any>>): Int {
        val repository = repositoryProvider.get()
        var restored = 0
        rows.forEach { row ->
            val name = row.cell(1)
            if (name.isEmpty()) return@forEach
            if (repository.getFriendByNameCaseInsensitive(name) != null) return@forEach
            // Balance columns are recomputed from the restored transactions, so only the
            // identity is taken from the sheet.
            repository.insertRestoredFriend(
                com.nothing.expensetracker.data.local.Friend(name = name)
            )
            restored++
        }
        return restored
    }

    private suspend fun restoreBudgets(rows: List<List<Any>>): Int {
        val repository = repositoryProvider.get()
        var restored = 0
        rows.forEach { row ->
            val key = parseBudgetKey(row) ?: return@forEach
            val amount = row.cell(2).toDoubleOrNull() ?: return@forEach
            if (repository.findExistingBudget(key.categoryName, key.month, key.year) != null) {
                return@forEach
            }
            repository.insertRestoredBudget(
                com.nothing.expensetracker.data.local.Budget(
                    categoryName = key.categoryName,
                    amount = amount,
                    month = key.month,
                    year = key.year,
                    lastAlertThreshold = row.cell(5).toSheetInt() ?: 0
                )
            )
            restored++
        }
        return restored
    }

    private suspend fun restoreTransactions(rows: List<List<Any>>): Int {
        val repository = repositoryProvider.get()
        val existingIds = repository.getAllExpenseIds().toHashSet()

        // Collected first, then written in one go: a restore can be hundreds of rows, and one
        // insert each is both slow and non-atomic.
        val toInsert = ArrayList<com.nothing.expensetracker.data.local.Expense>()
        rows.forEach { row ->
            val expense = parseTransactionRow(row) ?: return@forEach
            if (expense.id in existingIds) return@forEach
            toInsert.add(expense)
            existingIds.add(expense.id)
        }

        if (toInsert.isNotEmpty()) repository.insertRestoredExpenses(toInsert)
        return toInsert.size
    }

    private suspend fun restorePaymentMethods(rows: List<List<Any>>): Int {
        val repository = repositoryProvider.get()
        var restored = 0
        rows.forEach { row ->
            val name = row.cell(0)
            if (name.isEmpty()) return@forEach
            if (repository.getPaymentMethodByName(name) != null) return@forEach
            repository.insertRestoredPaymentMethod(
                com.nothing.expensetracker.data.local.PaymentMethod(
                    name = name,
                    isSystem = row.cell(1).toSheetBoolean()
                )
            )
            restored++
        }
        return restored
    }

    /**
     * Restores autopay rules, keeping each rule's id so a later push updates the same sheet row.
     *
     * `lastRunMonth` is carried across deliberately: without it a restored rule would believe it
     * had never run this month and fire a second time, creating a duplicate transaction.
     */
    private suspend fun restoreAutopayRules(rows: List<List<Any>>): Int {
        val repository = repositoryProvider.get()
        var restored = 0
        rows.forEach { row ->
            val id = row.cell(0).toSheetLong() ?: return@forEach
            val amount = row.cell(4).toSheetNumber() ?: return@forEach
            val dayOfMonth = row.cell(7).toSheetInt() ?: return@forEach
            if (repository.getAutopayRuleById(id) != null) return@forEach
            repository.insertRestoredAutopayRule(
                com.nothing.expensetracker.data.local.AutopayRule(
                    id = id,
                    amount = amount,
                    description = row.cell(1),
                    category = row.cell(2).ifEmpty { "Other" },
                    type = row.cell(3).ifEmpty { "Debit" },
                    paymentMethod = row.cell(5).ifEmpty { "UPI" },
                    friendId = row.cell(6).takeIf { it.isNotEmpty() },
                    dayOfMonth = dayOfMonth.coerceIn(1, 31),
                    isActive = row.cell(8).toSheetBoolean(),
                    lastRunMonth = row.cell(9).takeIf { it.isNotEmpty() }
                )
            )
            restored++
        }
        return restored
    }

    // --- Deletions, where the sheet says a record is gone ---

    /**
     * Deletes locally-synced transactions whose row is no longer in the sheet.
     *
     * Only "Synced" rows are candidates. Anything Pending/Failed/Syncing hasn't reached the sheet
     * yet, so its absence there says nothing; "Deleted" is already queued for removal.
     */
    private suspend fun pruneTransactions(rows: List<List<Any>>): Int {
        val repository = repositoryProvider.get()
        val sheetIds = rows.mapNotNull { it.cell(0).toSheetLong() }.toHashSet()
        val local = repository.getAllExpenses().first()
        val doomed = local.filter { it.syncStatus == "Synced" && it.id !in sheetIds }

        // Deleting a handful of rows is a user tidying their spreadsheet. Deleting most of them is
        // far more likely to be a truncated read, a wrong spreadsheet, or a bug here - so refuse,
        // and let the user delete from inside the app instead. Nothing is lost by being wrong in
        // this direction; everything is lost by being wrong in the other.
        val syncedCount = local.count { it.syncStatus == "Synced" }
        if (syncedCount >= PRUNE_SAFETY_MINIMUM && doomed.size > syncedCount * PRUNE_SAFETY_RATIO) {
            Log.w(
                tag,
                "[RESTORE] Refusing to prune ${doomed.size} of $syncedCount transactions - " +
                    "that looks like a bad read rather than a deliberate deletion"
            )
            return 0
        }

        doomed.forEach { expense ->
            Log.i(tag, "[RESTORE] Transaction deleted upstream - removing locally")
            repository.deleteExpensePermanently(expense)
        }
        return doomed.size
    }

    private suspend fun pruneBudgets(rows: List<List<Any>>): Int {
        val repository = repositoryProvider.get()
        val sheetKeys = rows.mapNotNull { parseBudgetKey(it) }.toHashSet()
        var removed = 0
        repository.getAllBudgetsOnce().forEach { budget ->
            if (budget.syncStatus != "Synced") return@forEach
            if (BudgetKey(budget.categoryName, budget.month, budget.year) in sheetKeys) {
                return@forEach
            }
            repository.deleteBudgetPermanently(budget)
            removed++
        }
        return removed
    }

    /** Friends still referenced by a transaction are kept, so no transaction is left dangling. */
    private suspend fun pruneFriends(rows: List<List<Any>>): Int {
        val repository = repositoryProvider.get()
        val sheetNames = rows.map { it.cell(1).lowercase(Locale.getDefault()) }.toHashSet()
        var removed = 0
        repository.getAllFriendEntities().first().forEach { friend ->
            if (friend.syncStatus != "Synced") return@forEach
            if (friend.name.lowercase(Locale.getDefault()) in sheetNames) return@forEach
            if (repository.friendHasTransactions(friend.name)) {
                Log.w(tag, "[RESTORE] Keeping friend: transactions still reference it")
                return@forEach
            }
            repository.deleteFriendPermanently(friend)
            removed++
        }
        return removed
    }

    /**
     * Prunes categories the sheet no longer lists. Kept regardless when still used by a
     * transaction, still the parent of a subcategory, or one the app's own logic looks up by
     * name (Friends/Friend/Transfer).
     */
    private suspend fun pruneCategories(rows: List<List<Any>>): Int {
        val repository = repositoryProvider.get()
        val sheetNames = rows.map { it.cell(1).lowercase(Locale.getDefault()) }.toHashSet()
        var removed = 0
        repository.getCategories().first().forEach { category ->
            if (category.syncStatus != "Synced") return@forEach
            if (category.name in ExpenseRepository.PROTECTED_CATEGORIES) return@forEach
            if (category.name.lowercase(Locale.getDefault()) in sheetNames) return@forEach
            if (repository.getCategoryUsageCount(category.name) > 0) {
                Log.w(tag, "[RESTORE] Keeping category: transactions still use it")
                return@forEach
            }
            if (repository.countSubcategories(category.id) > 0) {
                Log.w(tag, "[RESTORE] Keeping category: it still has subcategories")
                return@forEach
            }
            repository.deleteCategoryPermanently(category)
            removed++
        }
        return removed
    }


    private fun createNewSpreadsheet(service: Sheets): String? {
        return try {
            val spreadsheet = Spreadsheet().setProperties(SpreadsheetProperties().setTitle("Expense Tracker Database"))
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
