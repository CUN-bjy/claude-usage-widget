package com.claudeusage.widget.service

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.claudeusage.widget.data.local.CredentialManager
import com.claudeusage.widget.data.repository.UsageRepository
import com.claudeusage.widget.widget.UsageWidgetReceiver

class UsageUpdateWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val credentialManager = CredentialManager(applicationContext)
        val repository = UsageRepository()

        val credentials = credentialManager.getCredentials() ?: return Result.failure()

        val result = repository.fetchUsageData(credentials)

        return if (result.isSuccess) {
            // Trigger widget update
            try {
                UsageWidgetReceiver.updateWidget(applicationContext)
            } catch (_: Exception) {
                // Widget might not be placed
            }
            Result.success()
        } else {
            Result.retry()
        }
    }
}
