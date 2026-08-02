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
        val required = listOf("Transactions", "Categories", "Friends", "Budgets", "Settings", "App_Metadata")
        val missing = required.filter { it !in existingNames }
        
        if (missing.isNotEmpty()) {
            throw IllegalStateException("Spreadsheet initialization failed. Missing sheets: $missing")
        }
        
        Log.i(tag, "[SYNC] Master data verification completed.")
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
                Log.d(tag, "[CATEGORY_SYNC] Skipped Duplicate | ID: ${category.id} | Name: ${category.name} | Row: $existingRow")
                return true
            }

            Log.i(tag, "[CATEGORY_SYNC] Operation: CREATE | ID: ${category.id} | Name: ${category.name}")
            val rowValues = listOf(category.id.toString(), category.name)
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
            "Opening Bank Balance" to "₹${appPrefs.openingBankBalance.value}",
            "Opening Cash Balance" to "₹${appPrefs.openingCashBalance.value}",
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
                "Transaction ID", "Date", "Type", "Category", "Amount",
                "Payment Method", "Friend Name", "Notes", "Created At",
                "Updated At", "Sync Status"
            ),
            "Categories" to listOf("Category ID", "Category Name"),
            "Friends" to listOf(
                "Friend ID", "Friend Name", "Total Debit", "Total Credit", 
                "Outstanding Balance", "Status", "Last Updated"
            ),
            "Budgets" to listOf("Budget ID", "Category", "Amount", "Month", "Year"),
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
                    "Transactions" -> listOf(120, 120, 90, 140, 100, 140, 160, 250, 180, 180, 120)
                    "Categories" -> listOf(100, 220)
                    "Friends" -> listOf(90, 220, 130, 130, 170, 180, 180)
                    "Budgets" -> listOf(120, 160, 120, 80, 80)
                    "Settings", "App_Metadata" -> listOf(220, 220)
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
            val requiredSheets = listOf("Transactions", "Categories", "Friends", "Budgets", "Settings", "App_Metadata")
            
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
            val rowValues = listOf(budgetId, budget.categoryName ?: "Overall", budget.amount.toString(), budget.month.toString(), budget.year.toString())
            val body = ValueRange().setValues(listOf(rowValues))
            val rowIndex = findRowIndex(service, spreadsheetId, "Budgets", budgetId)
            if (rowIndex != null) {
                service.spreadsheets().values().update(spreadsheetId, "Budgets!A$rowIndex:E$rowIndex", body).setValueInputOption("RAW").execute()
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
                val rowValues = listOf(category.id.toString(), category.name)
                val body = ValueRange().setValues(listOf(rowValues))
                val response = service.spreadsheets().values().update(spreadsheetId, "Categories!A$rowIndex:B$rowIndex", body).setValueInputOption("RAW").execute()
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
            val rowValues = listOf(listOf(transaction.id.toString(), formattedDate, transaction.type, transaction.category, transaction.amount.toString(), transaction.paymentMethod, transaction.friendId ?: "", transaction.notes, transaction.timestamp.toString(), transaction.timestamp.toString(), "Synced"))
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
            val values = listOf(listOf(transaction.id.toString(), formattedDate, transaction.type, transaction.category, transaction.amount.toString(), transaction.paymentMethod, transaction.friendId ?: "", transaction.notes, transaction.timestamp.toString(), transaction.timestamp.toString(), "Synced"))
            val body = ValueRange().setValues(values)
            service.spreadsheets().values().update(spreadsheetId, "Transactions!A$rowIndex:K$rowIndex", body).setValueInputOption("USER_ENTERED").execute()
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
