package com.nothing.expensetracker.ui.settings

import androidx.lifecycle.ViewModel
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.tasks.Task
import com.nothing.expensetracker.auth.AuthState
import com.nothing.expensetracker.auth.GoogleAuthManager
import com.nothing.expensetracker.data.local.AppPrefs
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val appPrefs: AppPrefs,
    private val googleAuthManager: GoogleAuthManager
) : ViewModel() {

    val openingBalance: StateFlow<Double> = appPrefs.openingBalance
    val authState: StateFlow<AuthState> = googleAuthManager.authState

    fun updateOpeningBalance(balance: Double) {
        appPrefs.setOpeningBalance(balance)
    }

    fun getSignInIntent() = googleAuthManager.getSignInIntent()

    @Suppress("DEPRECATION")
    fun handleSignInResult(task: Task<GoogleSignInAccount>) {
        googleAuthManager.handleSignInResult(task)
    }

    fun signOut() {
        googleAuthManager.signOut()
    }
}
