package com.claudeusage.widget

import android.app.Application

class ClaudeUsageApp : Application() {
    override fun onCreate() {
        super.onCreate()
        CrashHandler(this).install()
    }
}
