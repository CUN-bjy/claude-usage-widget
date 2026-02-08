package com.claudeusage.widget.data.local

import android.content.Context
import android.content.SharedPreferences

class AppPreferences(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences(
        PREFS_NAME, Context.MODE_PRIVATE
    )

    var notificationEnabled: Boolean
        get() = prefs.getBoolean(KEY_NOTIFICATION_ENABLED, false)
        set(value) = prefs.edit().putBoolean(KEY_NOTIFICATION_ENABLED, value).apply()

    companion object {
        private const val PREFS_NAME = "claude_app_preferences"
        private const val KEY_NOTIFICATION_ENABLED = "notification_enabled"
    }
}
