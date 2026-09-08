package com.nothing.expensetracker.auth

import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity

private const val ALLOWED_AUTHENTICATORS = BiometricManager.Authenticators.BIOMETRIC_WEAK

object BiometricAuthHelper {

    /** True when the device has enrolled biometrics (fingerprint/face) usable right now. */
    fun isAvailable(activity: FragmentActivity): Boolean {
        return BiometricManager.from(activity).canAuthenticate(ALLOWED_AUTHENTICATORS) ==
            BiometricManager.BIOMETRIC_SUCCESS
    }

    /** Shows the system biometric prompt. [onSuccess] fires on a successful match; anything else is left to the MPIN fallback already on screen. */
    fun authenticate(activity: FragmentActivity, onSuccess: () -> Unit) {
        val executor = ContextCompat.getMainExecutor(activity)
        val prompt = BiometricPrompt(activity, executor, object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                onSuccess()
            }
        })

        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Unlock Expense Tracker")
            .setSubtitle("Use your fingerprint or face to continue")
            .setNegativeButtonText("Use MPIN")
            .setAllowedAuthenticators(ALLOWED_AUTHENTICATORS)
            .build()

        prompt.authenticate(promptInfo)
    }
}
