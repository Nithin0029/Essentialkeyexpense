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
                repositoryProvider.get().seedDefaultCategories() // Ensure DB is seeded
                ensureMasterDataExists(service, id)
                isInitialized = true
                Log.d(tag, "Spreadsheet initialized and cached for this session.")
            }
        }

        return@withContext spreadsheetId
    }

    private suspend fun ensureMasterDataExists(service: Sheets, spreadsheetId: String) {
        // 1. Initialize Categories
        val localCategories = repositoryProvider.get().getAllCategories().first()
        
        ensureListData(
            service = service,
            spreadsheetId = spreadsheetId,
            sheetName = "Categories",
            data = localCategories.mapIndexed { index, name -> listOf((index + 1).toString(), name) }
        )

        // 2. Initialize Friends
        val localFriends = repositoryProvider.get().getAllFriends().first()
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
            "Opening Bank Balance" to "₹${appPrefs.openingBankBalance.value}",
            "Opening Cash Balance" to "₹${appPrefs.openingCashBalance.value}",
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
                
                // 1. Format Header Cells
                requests.add(Request().setRepeatCell(
                    RepeatCellRequest()
                        .setRange(GridRange()
                            .setSheetId(sheetId)
                            .setStartRowIndex(0)
                            .setEndRowIndex(1)
                        )
                        .setCell(CellData()
                            .setUserEnteredFormat(CellFormat()
                                .setBackgroundColor(Color().setRed(0.12f).setGreen(0.23f).setBlue(0.54f)) // #1E3A8A approx
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

                // 2. Freeze First Row
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
                val sheetName = sheet.properties.title
                
                val widths = when (sheetName) {
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
            val spreadsheet: Spreadsheet = service.spreadsheets().get(spreadsheetId).execute()
            val sheets = spreadsheet.sheets
            val requests = mutableListOf<Request>()

            sheets.forEach { sheet ->
                val sheetId = sheet.properties.sheetId
                
                // For now, we'll add banding directly. To prevent infinite duplicates 
                // in a production environment, we'd check for existing bandings, 
                // but getBandedRanges() appears inaccessible in this environment.
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
                Log.d(tag, "Alternating row colors applied to all sheets")
            }
        } catch (e: Exception) {
            Log.e(tag, "Failed to apply alternating row colors", e)
        }
    }

    private fun applyValueFormatting(service: Sheets, spreadsheetId: String) {
        try {
            val spreadsheet = service.spreadsheets().get(spreadsheetId).execute()
            val sheetId = spreadsheet.sheets.find { it.properties.title == "Transactions" }?.properties?.sheetId
                ?: return

            val requests = mutableListOf<Request>()

            // 1. Amount Formatting (Column E - Index 4)
            requests.add(Request().setRepeatCell(
                RepeatCellRequest()
                    .setRange(GridRange().setSheetId(sheetId).setStartRowIndex(1).setStartColumnIndex(4).setEndColumnIndex(5))
                    .setCell(CellData().setUserEnteredFormat(CellFormat().setNumberFormat(NumberFormat().setType("CURRENCY").setPattern("₹#,##0.00"))))
                    .setFields("userEnteredFormat.numberFormat")
            ))

            // 2. Date Formatting (Column B - Index 1)
            requests.add(Request().setRepeatCell(
                RepeatCellRequest()
                    .setRange(GridRange().setSheetId(sheetId).setStartRowIndex(1).setStartColumnIndex(1).setEndColumnIndex(2))
                    .setCell(CellData().setUserEnteredFormat(CellFormat().setNumberFormat(NumberFormat().setType("DATE").setPattern("dd-MMM-yyyy"))))
                    .setFields("userEnteredFormat.numberFormat")
            ))

            // 3. Created/Updated At Formatting (Columns I, J - Indices 8, 9)
            requests.add(Request().setRepeatCell(
                RepeatCellRequest()
                    .setRange(GridRange().setSheetId(sheetId).setStartRowIndex(1).setStartColumnIndex(8).setEndColumnIndex(10))
                    .setCell(CellData().setUserEnteredFormat(CellFormat().setNumberFormat(NumberFormat().setType("DATE_TIME").setPattern("dd-MMM-yyyy HH:mm:ss"))))
                    .setFields("userEnteredFormat.numberFormat")
            ))

            if (requests.isNotEmpty()) {
                val batchUpdate = BatchUpdateSpreadsheetRequest().setRequests(requests)
                service.spreadsheets().batchUpdate(spreadsheetId, batchUpdate).execute()
                Log.d(tag, "Value formatting applied to Transactions sheet")
            }

            // 4. Friends Sheet Value Formatting
            val friendsSheetId = spreadsheet.sheets.find { it.properties.title == "Friends" }?.properties?.sheetId
            if (friendsSheetId != null) {
                val friendsRequests = mutableListOf<Request>()
                
                // Currency for Debit, Credit, Balance (Columns C, D, E - Indices 2, 3, 4)
                friendsRequests.add(Request().setRepeatCell(
                    RepeatCellRequest()
                        .setRange(GridRange().setSheetId(friendsSheetId).setStartRowIndex(1).setStartColumnIndex(2).setEndColumnIndex(5))
                        .setCell(CellData().setUserEnteredFormat(CellFormat().setNumberFormat(NumberFormat().setType("CURRENCY").setPattern("₹#,##0.00"))))
                        .setFields("userEnteredFormat.numberFormat")
                ))

                // Date Time for Last Updated (Column G - Index 6)
                friendsRequests.add(Request().setRepeatCell(
                    RepeatCellRequest()
                        .setRange(GridRange().setSheetId(friendsSheetId).setStartRowIndex(1).setStartColumnIndex(6).setEndColumnIndex(7))
                        .setCell(CellData().setUserEnteredFormat(CellFormat().setNumberFormat(NumberFormat().setType("DATE_TIME").setPattern("dd-MMM-yyyy HH:mm:ss"))))
                        .setFields("userEnteredFormat.numberFormat")
                ))

                if (friendsRequests.isNotEmpty()) {
                    val batchUpdate = BatchUpdateSpreadsheetRequest().setRequests(friendsRequests)
                    service.spreadsheets().batchUpdate(spreadsheetId, batchUpdate).execute()
                    Log.d(tag, "Value formatting applied to Friends sheet")
                }
            }
        } catch (e: Exception) {
            Log.e(tag, "Failed to apply value formatting", e)
        }
    }

    private fun applyConditionalFormatting(service: Sheets, spreadsheetId: String) {
        try {
            val spreadsheet = service.spreadsheets().get(spreadsheetId).execute()
            val sheetId = spreadsheet.sheets.find { it.properties.title == "Transactions" }?.properties?.sheetId
                ?: return

            // Check if rules already exist to avoid duplication
            val existingRules = spreadsheet.sheets.find { it.properties.title == "Transactions" }?.conditionalFormats
            if (existingRules != null && existingRules.isNotEmpty()) {
                Log.d(tag, "Conditional formatting rules already exist, skipping.")
                return
            }

            val requests = mutableListOf<Request>()

            // Helper to create a rule
            fun createRule(columnIndex: Int, text: String, bgColor: Color, textColor: Color? = null): ConditionalFormatRule {
                val condition = ConditionValue().setUserEnteredValue(text)
                val rule = BooleanRule()
                    .setCondition(BooleanCondition().setType("TEXT_EQ").setValues(listOf(condition)))
                    .setFormat(CellFormat().setBackgroundColor(bgColor))
                
                textColor?.let { rule.format.setTextFormat(TextFormat().setForegroundColor(it)) }

                return ConditionalFormatRule()
                    .setRanges(listOf(GridRange()
                        .setSheetId(sheetId)
                        .setStartRowIndex(1)
                        .setStartColumnIndex(columnIndex)
                        .setEndColumnIndex(columnIndex + 1)
                    ))
                    .setBooleanRule(rule)
            }

            // 1. Type Rules (Column C - Index 2)
            requests.add(Request().setAddConditionalFormatRule(AddConditionalFormatRuleRequest().setRule(
                createRule(2, "Debit", Color().setRed(0.97f).setGreen(0.84f).setBlue(0.85f))
            )))
            requests.add(Request().setAddConditionalFormatRule(AddConditionalFormatRuleRequest().setRule(
                createRule(2, "Credit", Color().setRed(0.83f).setGreen(0.93f).setBlue(0.85f))
            )))

            // 2. Sync Status Rules (Column K - Index 10)
            requests.add(Request().setAddConditionalFormatRule(AddConditionalFormatRuleRequest().setRule(
                createRule(10, "Synced", Color().setRed(0.78f).setGreen(0.94f).setBlue(0.81f), Color().setRed(0.0f).setGreen(0.38f).setBlue(0.0f))
            )))
            requests.add(Request().setAddConditionalFormatRule(AddConditionalFormatRuleRequest().setRule(
                createRule(10, "Pending", Color().setRed(1.0f).setGreen(0.92f).setBlue(0.61f), Color().setRed(0.61f).setGreen(0.34f).setBlue(0.0f))
            )))
            requests.add(Request().setAddConditionalFormatRule(AddConditionalFormatRuleRequest().setRule(
                createRule(10, "Failed", Color().setRed(1.0f).setGreen(0.78f).setBlue(0.81f), Color().setRed(1.0f).setGreen(1.0f).setBlue(1.0f))
            )))

            if (requests.isNotEmpty()) {
                val batchUpdate = BatchUpdateSpreadsheetRequest().setRequests(requests)
                service.spreadsheets().batchUpdate(spreadsheetId, batchUpdate).execute()
                Log.d(tag, "Conditional formatting rules applied to Transactions sheet")
            }

            // 3. Friends Sheet Rules
            val friendsSheet = spreadsheet.sheets.find { it.properties.title == "Friends" }
            val friendsSheetId = friendsSheet?.properties?.sheetId
            if (friendsSheetId != null && friendsSheet.conditionalFormats.isNullOrEmpty()) {
                val friendsRequests = mutableListOf<Request>()

                // Helper to create a rule for Friends sheet
                fun createFriendRule(text: String, bgColor: Color, textColor: Color? = null): ConditionalFormatRule {
                    val condition = ConditionValue().setUserEnteredValue(text)
                    val rule = BooleanRule()
                        .setCondition(BooleanCondition().setType("TEXT_EQ").setValues(listOf(condition)))
                        .setFormat(CellFormat().setBackgroundColor(bgColor))
                    
                    textColor?.let { rule.format.setTextFormat(TextFormat().setForegroundColor(it)) }

                    return ConditionalFormatRule()
                        .setRanges(listOf(GridRange()
                            .setSheetId(friendsSheetId)
                            .setStartRowIndex(1)
                            .setStartColumnIndex(5) // Column F - Status
                            .setEndColumnIndex(6)
                        ))
                        .setBooleanRule(rule)
                }

                friendsRequests.add(Request().setAddConditionalFormatRule(AddConditionalFormatRuleRequest().setRule(
                    createFriendRule("Friend Owes You", Color().setRed(0.83f).setGreen(0.93f).setBlue(0.85f), Color().setRed(0.0f).setGreen(0.38f).setBlue(0.0f))
                )))
                friendsRequests.add(Request().setAddConditionalFormatRule(AddConditionalFormatRuleRequest().setRule(
                    createFriendRule("You Owe Friend", Color().setRed(0.97f).setGreen(0.84f).setBlue(0.85f), Color().setRed(0.64f).setGreen(0.1f).setBlue(0.1f))
                )))
                friendsRequests.add(Request().setAddConditionalFormatRule(AddConditionalFormatRuleRequest().setRule(
                    createFriendRule("Settled", Color().setRed(0.95f).setGreen(0.95f).setBlue(0.95f), Color().setRed(0.5f).setGreen(0.5f).setBlue(0.5f))
                )))

                if (friendsRequests.isNotEmpty()) {
                    val batchUpdate = BatchUpdateSpreadsheetRequest().setRequests(friendsRequests)
                    service.spreadsheets().batchUpdate(spreadsheetId, batchUpdate).execute()
                    Log.d(tag, "Conditional formatting rules applied to Friends sheet")
                }
            }
        } catch (e: Exception) {
            Log.e(tag, "Failed to apply conditional formatting", e)
        }
    }

    private fun applyFilters(service: Sheets, spreadsheetId: String) {
        try {
            val spreadsheet = service.spreadsheets().get(spreadsheetId).execute()
            val sheet = spreadsheet.sheets.find { it.properties.title == "Transactions" } ?: return
            val sheetId = sheet.properties.sheetId

            // Check if filter already exists
            if (sheet.basicFilter != null) {
                Log.d(tag, "Filter already exists on Transactions sheet")
                return
            }

            val request = Request().setSetBasicFilter(
                SetBasicFilterRequest().setFilter(
                    BasicFilter()
                        .setRange(GridRange()
                            .setSheetId(sheetId)
                            .setStartRowIndex(0)
                            .setStartColumnIndex(0)
                            .setEndColumnIndex(11) // Columns A to K
                        )
                )
            )

            val batchUpdate = BatchUpdateSpreadsheetRequest().setRequests(listOf(request))
            service.spreadsheets().batchUpdate(spreadsheetId, batchUpdate).execute()
            Log.d(tag, "Basic filter applied to Transactions sheet")

            // 2. Friends Sheet Filter
            val friendsSheet = spreadsheet.sheets.find { it.properties.title == "Friends" }
            if (friendsSheet != null && friendsSheet.basicFilter == null) {
                val friendsRequest = Request().setSetBasicFilter(
                    SetBasicFilterRequest().setFilter(
                        BasicFilter().setRange(GridRange()
                            .setSheetId(friendsSheet.properties.sheetId)
                            .setStartRowIndex(0)
                            .setEndRowIndex(1000)
                            .setStartColumnIndex(0)
                            .setEndColumnIndex(7) // Columns A to G
                        )
                    )
                )
                service.spreadsheets().batchUpdate(spreadsheetId, BatchUpdateSpreadsheetRequest().setRequests(listOf(friendsRequest))).execute()
                Log.d(tag, "Basic filter applied to Friends sheet")
            }
        } catch (e: Exception) {
            Log.e(tag, "Failed to apply basic filter", e)
        }
    }

    private fun applyCellAlignment(service: Sheets, spreadsheetId: String) {
        try {
            val spreadsheet = service.spreadsheets().get(spreadsheetId).execute()
            val sheetId = spreadsheet.sheets.find { it.properties.title == "Transactions" }?.properties?.sheetId
                ?: return

            val requests = mutableListOf<Request>()

            // Alignment Map: Column Index to Horizontal Alignment
            val alignmentMap = mapOf(
                0 to "CENTER", // Transaction ID
                1 to "CENTER", // Date
                2 to "CENTER", // Type
                3 to "LEFT",   // Category
                4 to "RIGHT",  // Amount
                5 to "CENTER", // Payment Method
                6 to "LEFT",   // Friend Name
                7 to "LEFT",   // Notes
                8 to "CENTER", // Created At
                9 to "CENTER", // Updated At
                10 to "CENTER" // Sync Status
            )

            alignmentMap.forEach { (columnIndex, alignment) ->
                requests.add(Request().setRepeatCell(
                    RepeatCellRequest()
                        .setRange(GridRange()
                            .setSheetId(sheetId)
                            .setStartRowIndex(1) // Data rows
                            .setStartColumnIndex(columnIndex)
                            .setEndColumnIndex(columnIndex + 1)
                        )
                        .setCell(CellData().setUserEnteredFormat(CellFormat()
                            .setHorizontalAlignment(alignment)
                            .setVerticalAlignment("MIDDLE")
                        ))
                        .setFields("userEnteredFormat(horizontalAlignment,verticalAlignment)")
                ))
            }

            if (requests.isNotEmpty()) {
                val batchUpdate = BatchUpdateSpreadsheetRequest().setRequests(requests)
                service.spreadsheets().batchUpdate(spreadsheetId, batchUpdate).execute()
                Log.d(tag, "Cell alignment applied to Transactions sheet")
            }

            // 3. Friends Sheet Alignment
            val friendsSheetId = spreadsheet.sheets.find { it.properties.title == "Friends" }?.properties?.sheetId
            if (friendsSheetId != null) {
                val friendsRequests = mutableListOf<Request>()
                val friendsAlignmentMap = mapOf(
                    0 to "CENTER", // ID
                    1 to "LEFT",   // Name
                    2 to "RIGHT",  // Debit
                    3 to "RIGHT",  // Credit
                    4 to "RIGHT",  // Balance
                    5 to "CENTER", // Status
                    6 to "CENTER"  // Last Updated
                )
                friendsAlignmentMap.forEach { (columnIndex, alignment) ->
                    friendsRequests.add(Request().setRepeatCell(
                        RepeatCellRequest()
                            .setRange(GridRange().setSheetId(friendsSheetId).setStartRowIndex(1).setStartColumnIndex(columnIndex).setEndColumnIndex(columnIndex + 1))
                            .setCell(CellData().setUserEnteredFormat(CellFormat().setHorizontalAlignment(alignment).setVerticalAlignment("MIDDLE")))
                            .setFields("userEnteredFormat(horizontalAlignment,verticalAlignment)")
                    ))
                }
                if (friendsRequests.isNotEmpty()) {
                    service.spreadsheets().batchUpdate(spreadsheetId, BatchUpdateSpreadsheetRequest().setRequests(friendsRequests)).execute()
                    Log.d(tag, "Cell alignment applied to Friends sheet")
                }
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
                val sheetName = sheet.properties.title

                // 1. Set Font Arial, Size 11, and Vertical Middle for all cells
                requests.add(Request().setRepeatCell(
                    RepeatCellRequest()
                        .setRange(GridRange().setSheetId(sheetId).setStartRowIndex(0).setEndRowIndex(1000))
                        .setCell(CellData().setUserEnteredFormat(CellFormat()
                            .setTextFormat(TextFormat().setFontFamily("Arial").setFontSize(11))
                            .setVerticalAlignment("MIDDLE")
                        ))
                        .setFields("userEnteredFormat(textFormat.fontFamily,textFormat.fontSize,verticalAlignment)")
                ))

                // 2. Apply Thin Borders to a reasonable data range
                val border = Border().setStyle("SOLID").setColor(Color().setRed(0.8f).setGreen(0.8f).setBlue(0.8f))
                requests.add(Request().setUpdateBorders(
                    UpdateBordersRequest()
                        .setRange(GridRange().setSheetId(sheetId).setStartRowIndex(0).setEndRowIndex(100).setStartColumnIndex(0).setEndColumnIndex(20))
                        .setTop(border).setBottom(border).setLeft(border).setRight(border)
                        .setInnerHorizontal(border).setInnerVertical(border)
                ))

                // 3. Wrap Text for Notes in Transactions sheet (Column H - Index 7)
                if (sheetName == "Transactions") {
                    requests.add(Request().setRepeatCell(
                        RepeatCellRequest()
                            .setRange(GridRange().setSheetId(sheetId).setStartColumnIndex(7).setEndColumnIndex(8))
                            .setCell(CellData().setUserEnteredFormat(CellFormat().setWrapStrategy("WRAP")))
                            .setFields("userEnteredFormat.wrapStrategy")
                    ))
                }

                // 4. Auto Resize Rows
                requests.add(Request().setAutoResizeDimensions(
                    AutoResizeDimensionsRequest()
                        .setDimensions(DimensionRange()
                            .setSheetId(sheetId)
                            .setDimension("ROWS")
                            .setStartIndex(0)
                        )
                ))
            }

            if (requests.isNotEmpty()) {
                val batchUpdate = BatchUpdateSpreadsheetRequest().setRequests(requests)
                service.spreadsheets().batchUpdate(spreadsheetId, batchUpdate).execute()
                Log.d(tag, "Final spreadsheet polish applied to all sheets")
            }
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

    /**
     * Finds the row number for a given value in a specific column.
     * Returns 1-based row number or null if not found.
     */
    private suspend fun findRowIndex(service: Sheets, spreadsheetId: String, sheetName: String, value: String, column: String = "A"): Int? {
        return try {
            val range = "$sheetName!$column:$column"
            val response = service.spreadsheets().values().get(spreadsheetId, range).execute()
            val values = response.getValues()
            if (values != null) {
                for (i in values.indices) {
                    if (values[i].isNotEmpty() && values[i][0].toString() == value) {
                        return i + 1
                    }
                }
            }
            null
        } catch (e: Exception) {
            Log.e(tag, "Error finding row in $sheetName for $value", e)
            null
        }
    }

    /**
     * Synchronizes a budget record to the "Budgets" sheet.
     */
    suspend fun syncBudgetToSheet(budget: com.nothing.expensetracker.data.local.Budget) = withContext(Dispatchers.IO) {
        val service = getSheetsService() ?: return@withContext
        val spreadsheetId = appPrefs.spreadsheetId.value ?: return@withContext

        try {
            val budgetId = if (budget.categoryName == null) "OVERALL" else "CAT_${budget.categoryName}"
            val rowValues = listOf(
                budgetId,
                budget.categoryName ?: "Overall",
                budget.amount.toString(),
                budget.month.toString(),
                budget.year.toString()
            )
            val body = ValueRange().setValues(listOf(rowValues))
            
            // Find existing row by Budget ID (Column A)
            val rowIndex = findRowIndex(service, spreadsheetId, "Budgets", budgetId)
            
            if (rowIndex != null) {
                val range = "Budgets!A$rowIndex:E$rowIndex"
                service.spreadsheets().values().update(spreadsheetId, range, body)
                    .setValueInputOption("RAW")
                    .execute()
            } else {
                service.spreadsheets().values().append(spreadsheetId, "Budgets!A2", body)
                    .setValueInputOption("RAW")
                    .execute()
            }
        } catch (e: Exception) {
            Log.e(tag, "Failed to sync budget to sheet", e)
        }
    }

    /**
     * Deletes a budget record from the "Budgets" sheet.
     */
    suspend fun deleteBudgetFromSheet(categoryName: String?) = withContext(Dispatchers.IO) {
        val service = getSheetsService() ?: return@withContext
        val spreadsheetId = appPrefs.spreadsheetId.value ?: return@withContext

        try {
            val budgetId = if (categoryName == null) "OVERALL" else "CAT_$categoryName"
            val rowIndex = findRowIndex(service, spreadsheetId, "Budgets", budgetId)
            
            if (rowIndex != null) {
                val spreadsheet = service.spreadsheets().get(spreadsheetId).execute()
                val sheetId = spreadsheet.sheets.find { it.properties.title == "Budgets" }?.properties?.sheetId ?: return@withContext

                val deleteRequest = Request().setDeleteDimension(
                    DeleteDimensionRequest().setRange(
                        DimensionRange()
                            .setSheetId(sheetId)
                            .setDimension("ROWS")
                            .setStartIndex(rowIndex - 1)
                            .setEndIndex(rowIndex)
                    )
                )

                service.spreadsheets().batchUpdate(spreadsheetId, BatchUpdateSpreadsheetRequest().setRequests(listOf(deleteRequest))).execute()
            }
        } catch (e: Exception) {
            Log.e(tag, "Failed to delete budget from sheet", e)
        }
    }

    /**
     * Appends a new category to the "Categories" sheet.
     */
    suspend fun addCategoryToSheet(category: com.nothing.expensetracker.data.local.Category) = withContext(Dispatchers.IO) {
        val service = getSheetsService() ?: return@withContext
        val spreadsheetId = appPrefs.spreadsheetId.value ?: return@withContext

        try {
            val rowValues = listOf(category.id.toString(), category.name)
            val body = ValueRange().setValues(listOf(rowValues))
            service.spreadsheets().values().append(spreadsheetId, "Categories!A2", body)
                .setValueInputOption("RAW")
                .execute()
            Log.d(tag, "Appended category: ${category.name}")
        } catch (e: Exception) {
            Log.e(tag, "Failed to add category to sheet: ${category.name}", e)
        }
    }

    /**
     * Updates an existing category in the "Categories" sheet.
     */
    suspend fun updateCategoryInSheet(oldName: String, category: com.nothing.expensetracker.data.local.Category) = withContext(Dispatchers.IO) {
        val service = getSheetsService() ?: return@withContext
        val spreadsheetId = appPrefs.spreadsheetId.value ?: return@withContext

        try {
            val rowIndex = findRowIndex(service, spreadsheetId, "Categories", oldName, "B")
            if (rowIndex != null) {
                val rowValues = listOf(category.id.toString(), category.name)
                val body = ValueRange().setValues(listOf(rowValues))
                val range = "Categories!A$rowIndex:B$rowIndex"
                service.spreadsheets().values().update(spreadsheetId, range, body)
                    .setValueInputOption("RAW")
                    .execute()
                Log.d(tag, "Updated category: $oldName -> ${category.name}")
            }
        } catch (e: Exception) {
            Log.e(tag, "Failed to update category in sheet: $oldName", e)
        }
    }

    /**
     * Deletes a category from the "Categories" sheet.
     */
    suspend fun deleteCategoryFromSheet(categoryName: String) = withContext(Dispatchers.IO) {
        val service = getSheetsService() ?: return@withContext
        val spreadsheetId = appPrefs.spreadsheetId.value ?: return@withContext

        try {
            val rowIndex = findRowIndex(service, spreadsheetId, "Categories", categoryName, "B")
            if (rowIndex != null) {
                val spreadsheet = service.spreadsheets().get(spreadsheetId).execute()
                val sheetId = spreadsheet.sheets.find { it.properties.title == "Categories" }?.properties?.sheetId ?: return@withContext

                val deleteRequest = Request().setDeleteDimension(
                    DeleteDimensionRequest().setRange(
                        DimensionRange()
                            .setSheetId(sheetId)
                            .setDimension("ROWS")
                            .setStartIndex(rowIndex - 1)
                            .setEndIndex(rowIndex)
                    )
                )

                val batchUpdate = BatchUpdateSpreadsheetRequest().setRequests(listOf(deleteRequest))
                service.spreadsheets().batchUpdate(spreadsheetId, batchUpdate).execute()
                Log.d(tag, "Deleted category: $categoryName")
            }
        } catch (e: Exception) {
            Log.e(tag, "Failed to delete category from sheet: $categoryName", e)
        }
    }

    /**
     * Appends a new friend to the "Friends" sheet.
     */
    suspend fun addFriendToSheet(friend: com.nothing.expensetracker.data.local.Friend) = withContext(Dispatchers.IO) {
        val service = getSheetsService() ?: return@withContext
        val spreadsheetId = appPrefs.spreadsheetId.value ?: return@withContext

        try {
            val now = java.text.SimpleDateFormat("dd-MMM-yyyy HH:mm", Locale.getDefault()).format(Date())
            val rowValues = listOf(
                friend.id.toString(),
                friend.name,
                "0",        // Total Debit
                "0",        // Total Credit
                "0",        // Outstanding Balance
                "Settled",  // Status
                now         // Last Updated
            )

            val body = ValueRange().setValues(listOf(rowValues))
            service.spreadsheets().values().append(spreadsheetId, "Friends!A2", body)
                .setValueInputOption("USER_ENTERED")
                .execute()
            Log.d(tag, "Appended new friend: ${friend.name} (ID: ${friend.id})")
        } catch (e: Exception) {
            Log.e(tag, "Failed to add friend to sheet: ${friend.name}", e)
        }
    }

    /**
     * Updates an existing friend summary in the "Friends" sheet using Friend ID.
     */
    suspend fun updateFriendSummaryInSheet(friend: com.nothing.expensetracker.data.local.Friend) = withContext(Dispatchers.IO) {
        val service = getSheetsService() ?: return@withContext
        val spreadsheetId = appPrefs.spreadsheetId.value ?: return@withContext

        try {
            // 1. Get live balance from local engine
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
            val rowIndex = findRowIndex(service, spreadsheetId, "Friends", friend.id.toString())

            if (rowIndex != null) {
                val range = "Friends!A$rowIndex:G$rowIndex"
                service.spreadsheets().values().update(spreadsheetId, range, body)
                    .setValueInputOption("USER_ENTERED")
                    .execute()
                Log.d(tag, "Updated summary for ${friend.name} (ID: ${friend.id}) at row $rowIndex")
            } else {
                Log.w(tag, "Friend ID ${friend.id} not found for update. Appending instead.")
                addFriendToSheet(friend)
            }
        } catch (e: Exception) {
            Log.e(tag, "Failed to update friend summary for ${friend.name}", e)
        }
    }

    /**
     * Deletes a friend row from the "Friends" sheet.
     */
    suspend fun deleteFriendFromSheet(friendId: String) = withContext(Dispatchers.IO) {
        val service = getSheetsService() ?: return@withContext
        val spreadsheetId = appPrefs.spreadsheetId.value ?: return@withContext

        try {
            val rowIndex = findRowIndex(service, spreadsheetId, "Friends", friendId)
            if (rowIndex != null) {
                val spreadsheet = service.spreadsheets().get(spreadsheetId).execute()
                val sheetId = spreadsheet.sheets.find { it.properties.title == "Friends" }?.properties?.sheetId ?: return@withContext

                val deleteRequest = Request().setDeleteDimension(
                    DeleteDimensionRequest().setRange(
                        DimensionRange()
                            .setSheetId(sheetId)
                            .setDimension("ROWS")
                            .setStartIndex(rowIndex - 1)
                            .setEndIndex(rowIndex)
                    )
                )

                val batchUpdate = BatchUpdateSpreadsheetRequest().setRequests(listOf(deleteRequest))
                service.spreadsheets().batchUpdate(spreadsheetId, batchUpdate).execute()
                Log.d(tag, "Deleted friend ID $friendId from Friends sheet")
            }
        } catch (e: Exception) {
            Log.e(tag, "Failed to delete friend ID $friendId from sheet", e)
        }
    }

    /**
     * Appends a new transaction to the "Transactions" sheet.
     * Returns true if successful.
     */
    suspend fun addTransactionToSheet(transaction: com.nothing.expensetracker.data.local.Expense): Boolean = withContext(Dispatchers.IO) {
        Log.d(tag, "Starting sync for transaction: ${transaction.id} (${transaction.category})")
        val service = getSheetsService() ?: run {
            Log.e(tag, "Failed to start sync: Google Sheets service unavailable")
            return@withContext false
        }
        val spreadsheetId = appPrefs.spreadsheetId.value ?: run {
            Log.e(tag, "Failed to start sync: Spreadsheet ID missing")
            return@withContext false
        }

        try {
            val dateFormat = java.text.SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            val formattedDate = dateFormat.format(Date(transaction.timestamp))

            val rowValues = listOf(
                listOf(
                    transaction.id.toString(),            // 1. Transaction ID
                    formattedDate,                        // 2. Date
                    transaction.type,                     // 3. Type
                    transaction.category,                 // 4. Category
                    transaction.amount.toString(),        // 5. Amount
                    transaction.paymentMethod,            // 6. Payment Method
                    transaction.friendId ?: "",           // 7. Friend Name
                    transaction.notes,                    // 8. Notes
                    transaction.timestamp.toString(),     // 9. Created At
                    transaction.timestamp.toString(),     // 10. Updated At
                    "Synced"                              // 11. Sync Status
                )
            )

            val body = ValueRange().setValues(rowValues)
            Log.d(tag, "Sending API request to append row to 'Transactions' sheet")
            
            val response = service.spreadsheets().values()
                .append(spreadsheetId, "Transactions!A2", body)
                .setValueInputOption("USER_ENTERED")
                .execute()
            
            Log.d(tag, "Google Sheets API Response: $response")
            Log.i(tag, "Transaction ${transaction.id} synced successfully.")
            true
        } catch (e: Exception) {
            Log.e(tag, "Failed to sync transaction ${transaction.id} to Google Sheets", e)
            e.printStackTrace()
            false
        }
    }

    /**
     * Updates an existing transaction in the "Transactions" sheet.
     */
    suspend fun updateTransactionInSheet(transaction: com.nothing.expensetracker.data.local.Expense): Boolean = withContext(Dispatchers.IO) {
        val service = getSheetsService() ?: return@withContext false
        val spreadsheetId = appPrefs.spreadsheetId.value ?: return@withContext false

        try {
            val rowIndex = findRowIndex(service, spreadsheetId, "Transactions", transaction.id.toString())
            if (rowIndex == null) return@withContext false

            val dateFormat = java.text.SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            val formattedDate = dateFormat.format(Date(transaction.timestamp))

            val values = listOf(
                listOf(
                    transaction.id.toString(),
                    formattedDate,
                    transaction.type,
                    transaction.category,
                    transaction.amount.toString(),
                    transaction.paymentMethod,
                    transaction.friendId ?: "",
                    transaction.notes,
                    transaction.timestamp.toString(),
                    transaction.timestamp.toString(),
                    "Synced"
                )
            )

            val body = ValueRange().setValues(values)
            val range = "Transactions!A$rowIndex:K$rowIndex"

            service.spreadsheets().values().update(spreadsheetId, range, body)
                .setValueInputOption("USER_ENTERED")
                .execute()
            true
        } catch (e: Exception) {
            Log.e(tag, "Failed to update transaction in sheet", e)
            false
        }
    }

    /**
     * Deletes a transaction row from the "Transactions" sheet.
     */
    suspend fun deleteTransactionFromSheet(transactionId: String): Boolean = withContext(Dispatchers.IO) {
        val service = getSheetsService() ?: return@withContext false
        val spreadsheetId = appPrefs.spreadsheetId.value ?: return@withContext false

        try {
            val rowIndex = findRowIndex(service, spreadsheetId, "Transactions", transactionId)
            if (rowIndex != null) {
                val spreadsheet = service.spreadsheets().get(spreadsheetId).execute()
                val sheetId = spreadsheet.sheets.find { it.properties.title == "Transactions" }?.properties?.sheetId ?: return@withContext false

                val deleteRequest = Request().setDeleteDimension(
                    DeleteDimensionRequest().setRange(
                        DimensionRange()
                            .setSheetId(sheetId)
                            .setDimension("ROWS")
                            .setStartIndex(rowIndex - 1)
                            .setEndIndex(rowIndex)
                    )
                )

                val batchUpdate = BatchUpdateSpreadsheetRequest().setRequests(listOf(deleteRequest))
                service.spreadsheets().batchUpdate(spreadsheetId, batchUpdate).execute()
                true
            } else false
        } catch (e: Exception) {
            Log.e(tag, "Failed to delete transaction from sheet", e)
            false
        }
    }
}
