package com.claudeusage.widget

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.claudeusage.widget.ui.theme.ClaudeUsageTheme
import com.claudeusage.widget.ui.theme.DarkBackground
import com.claudeusage.widget.ui.theme.TextPrimary
import com.claudeusage.widget.ui.theme.TextSecondary

class LoginActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Clear any existing cookies for fresh login
        CookieManager.getInstance().removeAllCookies(null)
        CookieManager.getInstance().flush()

        setContent {
            ClaudeUsageTheme {
                LoginWebViewScreen(
                    onSessionCaptured = { sessionKey ->
                        val resultIntent = Intent().apply {
                            putExtra(EXTRA_SESSION_KEY, sessionKey)
                        }
                        setResult(Activity.RESULT_OK, resultIntent)
                        finish()
                    },
                    onClose = {
                        setResult(Activity.RESULT_CANCELED)
                        finish()
                    }
                )
            }
        }
    }

    companion object {
        const val EXTRA_SESSION_KEY = "session_key"
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun LoginWebViewScreen(
    onSessionCaptured: (String) -> Unit,
    onClose: () -> Unit
) {
    var isLoading by remember { mutableStateOf(true) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Sign in to Claude",
                        fontSize = 18.sp
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = "Close",
                            tint = TextSecondary
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = DarkBackground,
                    titleContentColor = TextPrimary
                )
            )
        },
        containerColor = DarkBackground
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            AndroidView(
                factory = { context ->
                    WebView(context).apply {
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        settings.userAgentString =
                            "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/121.0.0.0 Mobile Safari/537.36"

                        CookieManager.getInstance().setAcceptCookie(true)
                        CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)

                        webViewClient = object : WebViewClient() {
                            override fun shouldOverrideUrlLoading(
                                view: WebView?,
                                request: WebResourceRequest?
                            ): Boolean {
                                return false
                            }

                            override fun onPageFinished(view: WebView?, url: String?) {
                                isLoading = false
                                checkForSessionCookie(url, onSessionCaptured)
                            }
                        }

                        loadUrl("https://claude.ai/login")
                    }
                },
                modifier = Modifier.fillMaxSize()
            )

            if (isLoading) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(
                        color = com.claudeusage.widget.ui.theme.ClaudePurple,
                        strokeWidth = 3.dp
                    )
                }
            }
        }
    }
}

private fun checkForSessionCookie(url: String?, onSessionCaptured: (String) -> Unit) {
    val cookies = CookieManager.getInstance().getCookie("https://claude.ai") ?: return

    // Parse cookies to find sessionKey
    val sessionKey = cookies.split(";")
        .map { it.trim() }
        .firstOrNull { it.startsWith("sessionKey=") }
        ?.substringAfter("sessionKey=")
        ?.trim()

    if (!sessionKey.isNullOrBlank()) {
        onSessionCaptured(sessionKey)
    }
}
