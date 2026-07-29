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

    // Migration and Storage logic for Opening Bank Balance
    private val _openingBankBalance = MutableStateFlow(getOpeningBankBalance())
    val openingBankBalance: StateFlow<Double> = _openingBankBankBalanceAsStateFlow()

    // Storage logic for Opening Cash Balance
    private val _openingCashBalance = MutableStateFlow(getOpeningCashBalance())
    val openingCashBalance: StateFlow<Double> = _openingCashBalance.asStateFlow()

    private fun _openingBankBankBalanceAsStateFlow() = _openingBankBalance.asStateFlow()

    private val _spreadsheetId = MutableStateFlow(getSpreadsheetId())
    val spreadsheetId: StateFlow<String?> = _spreadsheetId.asStateFlow()

    fun setOpeningBankBalance(balance: Double) {
        prefs.edit().putFloat("opening_bank_balance", balance.toFloat()).apply()
        _openingBankBalance.value = balance
    }

    private fun getOpeningBankBalance(): Double {
        // Migration: check new key, fallback to old key, then default
        if (prefs.contains("opening_bank_balance")) {
            return prefs.getFloat("opening_bank_balance", 0f).toDouble()
        }
        val oldBalance = prefs.getFloat("opening_balance", 15000.0f).toDouble()
        // Save to new key for future
        prefs.edit().putFloat("opening_bank_balance", oldBalance.toFloat()).apply()
        return oldBalance
    }

    fun setOpeningCashBalance(balance: Double) {
        prefs.edit().putFloat("opening_cash_balance", balance.toFloat()).apply()
        _openingCashBalance.value = balance
    }

    private fun getOpeningCashBalance(): Double {
        return prefs.getFloat("opening_cash_balance", 0.0f).toDouble()
    }

    // Temporary backward compatibility for components not yet updated
    val openingBalance: StateFlow<Double> = openingBankBalance
    fun setOpeningBalance(balance: Double) = setOpeningBankBalance(balance)

    fun setSpreadsheetId(id: String?) {
        syncPrefs.edit().putString("spreadsheet_id", id).apply()
        _spreadsheetId.value = id
    }

    private fun getSpreadsheetId(): String? {
        return syncPrefs.getString("spreadsheet_id", null)
    }
}
