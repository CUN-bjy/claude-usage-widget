package com.claudeusage.widget

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.claudeusage.widget.data.local.AppPreferences
import com.claudeusage.widget.service.UsageNotificationService
import com.claudeusage.widget.service.UsageUpdateScheduler
import com.claudeusage.widget.ui.screens.MetricToggle
import com.claudeusage.widget.ui.screens.SettingsScreen
import com.claudeusage.widget.ui.screens.UiState
import com.claudeusage.widget.ui.screens.UsageScreen
import com.claudeusage.widget.ui.screens.UsageViewModel
import com.claudeusage.widget.ui.theme.ClaudeUsageTheme

private enum class Screen { Usage, Settings }

class MainActivity : ComponentActivity() {

    private val viewModel: UsageViewModel by viewModels()
    private lateinit var appPreferences: AppPreferences

    private var currentScreen by mutableStateOf(Screen.Usage)
    private var notificationEnabled by mutableStateOf(false)
    private val metricVisibility = mutableStateMapOf<String, Boolean>()

    private val loginLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            val sessionKey = result.data?.getStringExtra(LoginActivity.EXTRA_SESSION_KEY)
            if (sessionKey != null) {
                viewModel.onLoginComplete(sessionKey)
            }
        }
    }

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            appPreferences.notificationEnabled = true
            notificationEnabled = true
            if (viewModel.uiState.value is UiState.Success) {
                UsageNotificationService.start(applicationContext)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)

        appPreferences = AppPreferences(applicationContext)
        notificationEnabled = appPreferences.notificationEnabled

        // Load metric visibility from preferences
        metricVisibility["sonnet"] = appPreferences.showSonnet
        metricVisibility["opus"] = appPreferences.showOpus
        metricVisibility["cowork"] = appPreferences.showCowork
        metricVisibility["oauth_apps"] = appPreferences.showOauthApps
        metricVisibility["extra_usage"] = appPreferences.showExtraUsage

        // Request notification permission on first launch (Android 13+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
            ) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        // Schedule background updates
        UsageUpdateScheduler.schedule(applicationContext)

        setContent {
            ClaudeUsageTheme {
                val uiState by viewModel.uiState.collectAsState()
                val isRefreshing by viewModel.isRefreshing.collectAsState()
                val lastUpdated by viewModel.lastUpdated.collectAsState()

                when (currentScreen) {
                    Screen.Usage -> {
                        UsageScreen(
                            uiState = uiState,
                            isRefreshing = isRefreshing,
                            lastUpdated = lastUpdated,
                            visibleMetrics = metricVisibility
                                .filter { it.value }
                                .keys,
                            onRefresh = viewModel::refresh,
                            onLogout = {
                                viewModel.logout()
                                UsageUpdateScheduler.cancel(applicationContext)
                            },
                            onLoginClick = { launchLogin() },
                            onManualLogin = { sessionKey ->
                                viewModel.onManualLogin(sessionKey)
                            },
                            onSettingsClick = { currentScreen = Screen.Settings }
                        )
                    }
                    Screen.Settings -> {
                        SettingsScreen(
                            notificationEnabled = notificationEnabled,
                            onNotificationToggle = { enabled ->
                                handleNotificationToggle(enabled)
                            },
                            metricToggles = listOf(
                                MetricToggle("sonnet", "Sonnet (7d)", metricVisibility["sonnet"] ?: true),
                                MetricToggle("opus", "Opus (7d)", metricVisibility["opus"] ?: true),
                                MetricToggle("cowork", "Cowork (7d)", metricVisibility["cowork"] ?: true),
                                MetricToggle("oauth_apps", "OAuth Apps (7d)", metricVisibility["oauth_apps"] ?: true),
                                MetricToggle("extra_usage", "Extra Usage", metricVisibility["extra_usage"] ?: true)
                            ),
                            onMetricToggle = { key, enabled ->
                                handleMetricToggle(key, enabled)
                            },
                            onBack = { currentScreen = Screen.Usage }
                        )
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        val state = viewModel.uiState.value
        if (state is UiState.Success) {
            viewModel.refresh()
        }
    }

    private fun launchLogin() {
        val intent = Intent(this, LoginActivity::class.java)
        loginLauncher.launch(intent)
    }

    private fun handleNotificationToggle(enabled: Boolean) {
        if (enabled) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                    == PackageManager.PERMISSION_GRANTED
                ) {
                    appPreferences.notificationEnabled = true
                    notificationEnabled = true
                    if (viewModel.uiState.value is UiState.Success) {
                        UsageNotificationService.start(applicationContext)
                    }
                } else {
                    notificationPermissionLauncher.launch(
                        Manifest.permission.POST_NOTIFICATIONS
                    )
                }
            } else {
                appPreferences.notificationEnabled = true
                notificationEnabled = true
                if (viewModel.uiState.value is UiState.Success) {
                    UsageNotificationService.start(applicationContext)
                }
            }
        } else {
            appPreferences.notificationEnabled = false
            notificationEnabled = false
            UsageNotificationService.stop(applicationContext)
        }
    }

    private fun handleMetricToggle(key: String, enabled: Boolean) {
        metricVisibility[key] = enabled
        when (key) {
            "sonnet" -> appPreferences.showSonnet = enabled
            "opus" -> appPreferences.showOpus = enabled
            "cowork" -> appPreferences.showCowork = enabled
            "oauth_apps" -> appPreferences.showOauthApps = enabled
            "extra_usage" -> appPreferences.showExtraUsage = enabled
        }
    }
}
