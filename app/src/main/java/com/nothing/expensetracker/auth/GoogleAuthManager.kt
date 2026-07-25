package com.nothing.expensetracker.auth

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.Scope
import com.google.android.gms.tasks.Task
import com.google.api.services.sheets.v4.SheetsScopes
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

sealed class AuthState {
    object NotConnected : AuthState()
    data class Connected(
        val displayName: String?,
        val email: String?,
        val photoUrl: Uri?
    ) : AuthState()
    data class Error(val message: String) : AuthState()
}

@Singleton
class GoogleAuthManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    @Suppress("DEPRECATION")
    private val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
        .requestEmail()
        .requestProfile()
        .requestScopes(Scope(SheetsScopes.SPREADSHEETS))
        .build()

    @Suppress("DEPRECATION")
    private val googleSignInClient: GoogleSignInClient = GoogleSignIn.getClient(context, gso)

    private val _authState = MutableStateFlow<AuthState>(getInitialState())
    val authState: StateFlow<AuthState> = _authState.asStateFlow()

    @Suppress("DEPRECATION")
    private fun getInitialState(): AuthState {
        val account = GoogleSignIn.getLastSignedInAccount(context)
        return if (account != null) {
            AuthState.Connected(
                displayName = account.displayName,
                email = account.email,
                photoUrl = account.photoUrl
            )
        } else {
            AuthState.NotConnected
        }
    }

    @Suppress("DEPRECATION")
    fun getSignInIntent(): Intent = googleSignInClient.signInIntent

    @Suppress("DEPRECATION")
    fun handleSignInResult(task: Task<GoogleSignInAccount>) {
        try {
            val account = task.getResult(ApiException::class.java)
            if (account != null) {
                _authState.value = AuthState.Connected(
                    displayName = account.displayName,
                    email = account.email,
                    photoUrl = account.photoUrl
                )
            } else {
                _authState.value = AuthState.Error("Sign-in failed: Account is null")
            }
        } catch (e: ApiException) {
            _authState.value = AuthState.Error("Sign-in error: ${e.localizedMessage}")
        } catch (e: Exception) {
            _authState.value = AuthState.Error("Unexpected error: ${e.localizedMessage}")
        }
    }

    @Suppress("DEPRECATION")
    fun signOut(onComplete: () -> Unit = {}) {
        googleSignInClient.signOut().addOnCompleteListener {
            _authState.value = AuthState.NotConnected
            onComplete()
        }
    }
}
