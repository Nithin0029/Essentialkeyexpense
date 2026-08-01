package com.nothing.expensetracker.auth

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import java.security.MessageDigest
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MpinManager @Inject constructor(
    @param:ApplicationContext private val context: Context
) {
    private val sharedPreferences by lazy {
        try {
            val masterKey = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
            EncryptedSharedPreferences.create(
                context,
                "secure_mpin_prefs",
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        } catch (e: Exception) {
            context.getSharedPreferences("secure_mpin_prefs_fallback", Context.MODE_PRIVATE)
        }
    }

    companion object {
        private const val KEY_MPIN_HASH = "mpin_hash"
        private const val KEY_MPIN_SALT = "mpin_salt"
        private const val KEY_IS_MPIN_ENABLED = "is_mpin_enabled"
    }

    fun isMpinSet(): Boolean {
        return (sharedPreferences.getString(KEY_MPIN_HASH, null) != null) && 
               sharedPreferences.getBoolean(KEY_IS_MPIN_ENABLED, false)
    }

    fun setMpin(mpin: String) {
        val salt = UUID.randomUUID().toString()
        val hash = hashMpin(mpin, salt)
        sharedPreferences.edit()
            .putString(KEY_MPIN_HASH, hash)
            .putString(KEY_MPIN_SALT, salt)
            .putBoolean(KEY_IS_MPIN_ENABLED, true)
            .apply()
    }

    fun verifyMpin(mpin: String): Boolean {
        val storedHash = sharedPreferences.getString(KEY_MPIN_HASH, null) ?: return false
        val salt = sharedPreferences.getString(KEY_MPIN_SALT, null) ?: return false
        val inputHash = hashMpin(mpin, salt)
        return storedHash == inputHash
    }

    fun removeMpin() {
        sharedPreferences.edit()
            .remove(KEY_MPIN_HASH)
            .remove(KEY_MPIN_SALT)
            .putBoolean(KEY_IS_MPIN_ENABLED, false)
            .apply()
    }

    private fun hashMpin(mpin: String, salt: String): String {
        val bytes = (mpin + salt).toByteArray()
        val md = MessageDigest.getInstance("SHA-256")
        val digest = md.digest(bytes)
        return digest.joinToString("") { "%02x".format(it) }
    }
}
