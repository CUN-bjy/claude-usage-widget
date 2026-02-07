package com.claudeusage.widget.data.local

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.claudeusage.widget.data.model.Credentials

class CredentialManager(context: Context) {

    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val prefs = EncryptedSharedPreferences.create(
        context,
        "claude_credentials",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    fun getCredentials(): Credentials? {
        val sessionKey = prefs.getString(KEY_SESSION, null) ?: return null
        val orgId = prefs.getString(KEY_ORG_ID, null) ?: return null
        return Credentials(sessionKey, orgId)
    }

    fun saveCredentials(credentials: Credentials) {
        prefs.edit()
            .putString(KEY_SESSION, credentials.sessionKey)
            .putString(KEY_ORG_ID, credentials.organizationId)
            .apply()
    }

    fun clearCredentials() {
        prefs.edit().clear().apply()
    }

    fun hasCredentials(): Boolean {
        return getCredentials()?.isValid == true
    }

    companion object {
        private const val KEY_SESSION = "session_key"
        private const val KEY_ORG_ID = "organization_id"
    }
}
