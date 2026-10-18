package com.nothing.expensetracker.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.tasks.Task
import com.nothing.expensetracker.auth.AuthState
import com.nothing.expensetracker.auth.GoogleAuthManager
import com.nothing.expensetracker.data.local.AppPrefs
import com.nothing.expensetracker.data.local.ThemeMode
import com.nothing.expensetracker.data.repository.ExpenseRepository
import com.nothing.expensetracker.sync.RestoreSummary
import com.nothing.expensetracker.sync.SpreadsheetManager
import com.nothing.expensetracker.sync.SyncManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Progress of the pull-down that runs once the spreadsheet is connected. */
sealed class RestoreState {
    object Idle : RestoreState()
    object Restoring : RestoreState()
    /** Finished. [summary] is empty when the device was already up to date. */
    data class Done(val summary: RestoreSummary) : RestoreState()
    /** The pull could not complete. Distinct from an empty [Done], which means "nothing to do". */
    data class Failed(val message: String) : RestoreState()
}

sealed class SpreadsheetState {
    object Idle : SpreadsheetState()
    object Initializing : SpreadsheetState()
    data class Connected(val name: String, val id: String) : SpreadsheetState()
    data class Error(val message: String) : SpreadsheetState()
}

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val appPrefs: AppPrefs,
    private val googleAuthManager: GoogleAuthManager,
    private val spreadsheetManager: SpreadsheetManager,
    private val syncManager: SyncManager,
    private val repository: ExpenseRepository
) : ViewModel() {

    /**
     * What each account is worth today: the opening figure plus everything that has happened
     * since. This is the number the user actually recognises, so it is what the UI shows and
     * what they are asked to correct — the stored opening balance is derived from it.
     */
    val currentBankBalance: StateFlow<Double> = combine(
        appPrefs.openingBankBalance,
        repository.getTotalUpiBankCredits(),
        repository.getTotalUpiBankDebits()
    ) { opening, credits, debits ->
        opening + (credits ?: 0.0) - (debits ?: 0.0)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0.0)

    val currentCashBalance: StateFlow<Double> = combine(
        appPrefs.openingCashBalance,
        repository.getTotalCashCredits(),
        repository.getTotalCashDebits()
    ) { opening, credits, debits ->
        opening + (credits ?: 0.0) - (debits ?: 0.0)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0.0)

    /**
     * Sets the bank balance to what the user says it is right now.
     *
     * The dashboard works forwards from an opening balance, but nobody knows what their balance
     * was before their first ever transaction — especially after a restore, where the opening
     * figure was lost with the uninstall while the transactions came back from the spreadsheet.
     * So the opening balance is solved for instead: whatever value makes today's total come out
     * right, given every transaction on record.
     */
    fun setCurrentBankBalance(current: Double) {
        viewModelScope.launch {
            val credits = repository.getTotalUpiBankCredits().first() ?: 0.0
            val debits = repository.getTotalUpiBankDebits().first() ?: 0.0
            appPrefs.setOpeningBankBalance(current - (credits - debits))
            // Answering here counts as completing setup, so the first-run prompt won't reappear.
            appPrefs.markBalancesSetUp()
            // Push straight away: this value lives in app preferences, so the spreadsheet is the
            // only place it survives a reinstall.
            spreadsheetManager.syncOpeningBalancesToSheet()
        }
    }

    fun setCurrentCashBalance(current: Double) {
        viewModelScope.launch {
            val credits = repository.getTotalCashCredits().first() ?: 0.0
            val debits = repository.getTotalCashDebits().first() ?: 0.0
            appPrefs.setOpeningCashBalance(current - (credits - debits))
            appPrefs.markBalancesSetUp()
            spreadsheetManager.syncOpeningBalancesToSheet()
        }
    }

    val openingBankBalance: StateFlow<Double> = appPrefs.openingBankBalance
    val openingCashBalance: StateFlow<Double> = appPrefs.openingCashBalance
    val themeMode: StateFlow<ThemeMode> = appPrefs.themeMode
    val authState: StateFlow<AuthState> = googleAuthManager.authState

    fun setThemeMode(mode: ThemeMode) {
        appPrefs.setThemeMode(mode)
    }

    private val _spreadsheetState = MutableStateFlow<SpreadsheetState>(SpreadsheetState.Idle)
    val spreadsheetState: StateFlow<SpreadsheetState> = _spreadsheetState.asStateFlow()

    private val _isSyncing = MutableStateFlow(false)
    val isSyncing: StateFlow<Boolean> = _isSyncing.asStateFlow()

    /** Guards the automatic pull so it happens once per connection, not per screen visit. */
    private var hasRestoredThisSession = false

    private val _restoreState = MutableStateFlow<RestoreState>(RestoreState.Idle)
    val restoreState: StateFlow<RestoreState> = _restoreState.asStateFlow()

    // Sync Stats
    val unsyncedCount: StateFlow<Int> = syncManager.getUnsyncedCount()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)
    
    val syncedCount: StateFlow<Int> = syncManager.getSyncedCount()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)
    
    val failedCount: StateFlow<Int> = syncManager.getFailedCount()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)
    
    val lastSyncTime: StateFlow<Long?> = syncManager.getLastSyncTime()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    init {
        // Automatically setup spreadsheet when connected
        authState.onEach { state ->
            if (state is AuthState.Connected) {
                setupGoogleSheets()
            } else {
                _spreadsheetState.value = SpreadsheetState.Idle
                hasRestoredThisSession = false
            }
        }.launchIn(viewModelScope)
    }

    fun updateOpeningBankBalance(balance: Double) {
        appPrefs.setOpeningBankBalance(balance)
    }

    fun updateOpeningCashBalance(balance: Double) {
        appPrefs.setOpeningCashBalance(balance)
    }

    fun getSignInIntent() = googleAuthManager.getSignInIntent()

    @Suppress("DEPRECATION")
    fun handleSignInResult(task: Task<GoogleSignInAccount>) {
        googleAuthManager.handleSignInResult(task)
    }

    private fun setupGoogleSheets() {
        if (_spreadsheetState.value is SpreadsheetState.Initializing) return
        
        viewModelScope.launch {
            _spreadsheetState.value = SpreadsheetState.Initializing
            val id = spreadsheetManager.getOrCreateSpreadsheet()
            if (id != null) {
                _spreadsheetState.value = SpreadsheetState.Connected(
                    name = "Expense Tracker Database",
                    id = id
                )
                // Pull down anything the spreadsheet has that this device doesn't. Guarded so it
                // runs once per connection rather than every time the Settings screen is opened -
                // each pass is six spreadsheet reads, and it grows with history size. The manual
                // "Restore from Spreadsheet" row covers re-running it on demand.
                if (!hasRestoredThisSession) {
                    hasRestoredThisSession = true
                    restoreFromSheet()
                }
            } else {
                _spreadsheetState.value = SpreadsheetState.Error("Failed to initialize Google Sheet")
            }
        }
    }

    /** Runs the sheet-to-device merge. Safe to call repeatedly: it only adds what's missing. */
    fun restoreFromSheet() {
        if (_restoreState.value is RestoreState.Restoring) return
        viewModelScope.launch {
            _restoreState.value = RestoreState.Restoring
            _restoreState.value = try {
                RestoreState.Done(spreadsheetManager.restoreFromSheet())
            } catch (e: Exception) {
                // Reported rather than swallowed: an empty Done means "already up to date", and
                // showing that after a failure would tell the user their data isn't in the sheet.
                RestoreState.Failed(e.message ?: "Could not reach the spreadsheet")
            }
        }
    }

    fun acknowledgeRestore() {
        _restoreState.value = RestoreState.Idle
    }

    fun signOut() {
        googleAuthManager.signOut {
            _spreadsheetState.value = SpreadsheetState.Idle
        }
    }

    fun syncNow() {
        viewModelScope.launch {
            _isSyncing.value = true
            try {
                syncManager.syncNow()
            } finally {
                _isSyncing.value = false
            }
        }
    }
}
