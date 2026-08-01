package com.nothing.expensetracker.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.tasks.Task
import com.nothing.expensetracker.auth.AuthState
import com.nothing.expensetracker.auth.GoogleAuthManager
import com.nothing.expensetracker.data.local.AppPrefs
import com.nothing.expensetracker.sync.SpreadsheetManager
import com.nothing.expensetracker.sync.SyncManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

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
    private val syncManager: SyncManager
) : ViewModel() {

    val openingBankBalance: StateFlow<Double> = appPrefs.openingBankBalance
    val openingCashBalance: StateFlow<Double> = appPrefs.openingCashBalance
    val authState: StateFlow<AuthState> = googleAuthManager.authState

    private val _spreadsheetState = MutableStateFlow<SpreadsheetState>(SpreadsheetState.Idle)
    val spreadsheetState: StateFlow<SpreadsheetState> = _spreadsheetState.asStateFlow()

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
            } else {
                _spreadsheetState.value = SpreadsheetState.Error("Failed to initialize Google Sheet")
            }
        }
    }

    fun signOut() {
        googleAuthManager.signOut {
            _spreadsheetState.value = SpreadsheetState.Idle
        }
    }

    fun syncNow() {
        viewModelScope.launch {
            syncManager.syncNow()
        }
    }
}
