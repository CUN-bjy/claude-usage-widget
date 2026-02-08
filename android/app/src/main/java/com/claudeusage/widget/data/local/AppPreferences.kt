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

    var showSonnet: Boolean
        get() = prefs.getBoolean(KEY_SHOW_SONNET, true)
        set(value) = prefs.edit().putBoolean(KEY_SHOW_SONNET, value).apply()

    var showOpus: Boolean
        get() = prefs.getBoolean(KEY_SHOW_OPUS, false)
        set(value) = prefs.edit().putBoolean(KEY_SHOW_OPUS, value).apply()

    var showCowork: Boolean
        get() = prefs.getBoolean(KEY_SHOW_COWORK, false)
        set(value) = prefs.edit().putBoolean(KEY_SHOW_COWORK, value).apply()

    var showOauthApps: Boolean
        get() = prefs.getBoolean(KEY_SHOW_OAUTH_APPS, false)
        set(value) = prefs.edit().putBoolean(KEY_SHOW_OAUTH_APPS, value).apply()

    var showExtraUsage: Boolean
        get() = prefs.getBoolean(KEY_SHOW_EXTRA_USAGE, true)
        set(value) = prefs.edit().putBoolean(KEY_SHOW_EXTRA_USAGE, value).apply()

    companion object {
        private const val PREFS_NAME = "claude_app_preferences"
        private const val KEY_NOTIFICATION_ENABLED = "notification_enabled"
        private const val KEY_SHOW_SONNET = "show_sonnet"
        private const val KEY_SHOW_OPUS = "show_opus"
        private const val KEY_SHOW_COWORK = "show_cowork"
        private const val KEY_SHOW_OAUTH_APPS = "show_oauth_apps"
        private const val KEY_SHOW_EXTRA_USAGE = "show_extra_usage"
    }
}
