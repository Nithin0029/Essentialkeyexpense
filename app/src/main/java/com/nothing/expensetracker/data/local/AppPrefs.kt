package com.nothing.expensetracker.data.local

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class ThemeMode { SYSTEM, LIGHT, DARK }

@Singleton
class AppPrefs @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val prefs = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
    private val syncPrefs = context.getSharedPreferences("sync_prefs", Context.MODE_PRIVATE)

    private val _themeMode = MutableStateFlow(getThemeMode())
    val themeMode: StateFlow<ThemeMode> = _themeMode.asStateFlow()

    fun setThemeMode(mode: ThemeMode) {
        prefs.edit().putString("theme_mode", mode.name).apply()
        _themeMode.value = mode
    }

    private fun getThemeMode(): ThemeMode {
        val stored = prefs.getString("theme_mode", null) ?: return ThemeMode.SYSTEM
        return try {
            ThemeMode.valueOf(stored)
        } catch (e: IllegalArgumentException) {
            ThemeMode.SYSTEM
        }
    }

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
        if (prefs.contains("opening_bank_balance")) {
            return prefs.getFloat("opening_bank_balance", 0f).toDouble()
        }
        // Migrate from the pre-split key only when it genuinely exists. This used to fall through
        // to a hardcoded 15000 default, so every fresh install began with a phantom balance it
        // then persisted; a new install must start at zero.
        if (prefs.contains("opening_balance")) {
            val oldBalance = prefs.getFloat("opening_balance", 0f).toDouble()
            prefs.edit().putFloat("opening_bank_balance", oldBalance.toFloat()).apply()
            return oldBalance
        }
        return 0.0
    }

    /**
     * Whether the user has been asked for their starting balances.
     *
     * Set once the prompt is answered or dismissed, so a fresh install asks exactly one time.
     * Setting a balance anywhere else (Settings, the post-restore prompt) also marks it done.
     */
    private val _hasSetUpBalances = MutableStateFlow(
        // An install upgrading from a version before this flag existed has already configured its
        // balances, so the presence of a stored value counts as having been asked.
        prefs.getBoolean(KEY_BALANCES_SET_UP, false) ||
            prefs.contains("opening_bank_balance") ||
            prefs.contains("opening_cash_balance")
    )
    val hasSetUpBalances: StateFlow<Boolean> = _hasSetUpBalances.asStateFlow()

    fun markBalancesSetUp() {
        prefs.edit().putBoolean(KEY_BALANCES_SET_UP, true).apply()
        _hasSetUpBalances.value = true
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

    private companion object {
        const val KEY_BALANCES_SET_UP = "balances_set_up"
    }
}
