package com.claudeusage.widget

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.claudeusage.widget.service.UsageUpdateScheduler
import com.claudeusage.widget.ui.screens.UiState
import com.claudeusage.widget.ui.screens.UsageScreen
import com.claudeusage.widget.ui.screens.UsageViewModel
import com.claudeusage.widget.ui.theme.ClaudeUsageTheme

class MainActivity : ComponentActivity() {

    private val viewModel: UsageViewModel by viewModels()

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

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)

        // Schedule background updates
        UsageUpdateScheduler.schedule(applicationContext)

        setContent {
            ClaudeUsageTheme {
                val uiState by viewModel.uiState.collectAsState()
                val isRefreshing by viewModel.isRefreshing.collectAsState()
                val lastUpdated by viewModel.lastUpdated.collectAsState()

                UsageScreen(
                    uiState = uiState,
                    isRefreshing = isRefreshing,
                    lastUpdated = lastUpdated,
                    onRefresh = viewModel::refresh,
                    onLogout = {
                        viewModel.logout()
                        UsageUpdateScheduler.cancel(applicationContext)
                    },
                    onLoginClick = { launchLogin() },
                    onManualLogin = { sessionKey ->
                        viewModel.onManualLogin(sessionKey)
                    }
                )
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
}
