package com.nothing.expensetracker.data.local

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

@Singleton
class AppPrefs @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val prefs = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
    private val syncPrefs = context.getSharedPreferences("sync_prefs", Context.MODE_PRIVATE)

    private val _openingBalance = MutableStateFlow(getOpeningBalance())
    val openingBalance: StateFlow<Double> = _openingBalance.asStateFlow()

    private val _spreadsheetId = MutableStateFlow(getSpreadsheetId())
    val spreadsheetId: StateFlow<String?> = _spreadsheetId.asStateFlow()

    fun setOpeningBalance(balance: Double) {
        prefs.edit().putFloat("opening_balance", balance.toFloat()).apply()
        _openingBalance.value = balance
    }

    private fun getOpeningBalance(): Double {
        return prefs.getFloat("opening_balance", 15000.0f).toDouble()
    }

    fun setSpreadsheetId(id: String?) {
        syncPrefs.edit().putString("spreadsheet_id", id).apply()
        _spreadsheetId.value = id
    }

    private fun getSpreadsheetId(): String? {
        return syncPrefs.getString("spreadsheet_id", null)
    }
}
