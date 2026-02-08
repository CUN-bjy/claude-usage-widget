package com.claudeusage.widget

import android.app.Application
import android.content.Intent
import java.io.PrintWriter
import java.io.StringWriter

class CrashHandler(private val app: Application) : Thread.UncaughtExceptionHandler {

    private val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()

    fun install() {
        Thread.setDefaultUncaughtExceptionHandler(this)
    }

    override fun uncaughtException(thread: Thread, throwable: Throwable) {
        try {
            val sw = StringWriter()
            throwable.printStackTrace(PrintWriter(sw))
            val stackTrace = sw.toString()

            val intent = Intent(app, CrashActivity::class.java).apply {
                putExtra(CrashActivity.EXTRA_CRASH_LOG, stackTrace)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            }
            app.startActivity(intent)
        } catch (_: Exception) {
            defaultHandler?.uncaughtException(thread, throwable)
        }
    }
}
