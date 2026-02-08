package com.claudeusage.widget

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.claudeusage.widget.ui.theme.*

class LoginActivity : ComponentActivity() {

    private val handler = Handler(Looper.getMainLooper())
    private var cookiePollingRunnable: Runnable? = null
    private var sessionCaptured = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            ClaudeUsageTheme {
                var webViewError by remember { mutableStateOf<String?>(null) }

                if (webViewError != null) {
                    WebViewErrorScreen(
                        message = webViewError!!,
                        onClose = {
                            setResult(Activity.RESULT_CANCELED)
                            finish()
                        }
                    )
                } else {
                    LoginWebViewScreen(
                        onSessionCaptured = { sessionKey ->
                            if (!sessionCaptured) {
                                sessionCaptured = true
                                stopCookiePolling()
                                val resultIntent = Intent().apply {
                                    putExtra(EXTRA_SESSION_KEY, sessionKey)
                                }
                                setResult(Activity.RESULT_OK, resultIntent)
                                finish()
                            }
                        },
                        onClose = {
                            setResult(Activity.RESULT_CANCELED)
                            finish()
                        },
                        onError = { error ->
                            webViewError = error
                        },
                        onStartPolling = { callback ->
                            startCookiePolling(callback)
                        }
                    )
                }
            }
        }
    }

    private fun startCookiePolling(onSessionCaptured: (String) -> Unit) {
        stopCookiePolling()
        cookiePollingRunnable = object : Runnable {
            override fun run() {
                if (sessionCaptured) return
                val sessionKey = extractSessionKey()
                if (sessionKey != null) {
                    onSessionCaptured(sessionKey)
                } else {
                    handler.postDelayed(this, 2000)
                }
            }
        }
        handler.postDelayed(cookiePollingRunnable!!, 2000)
    }

    private fun stopCookiePolling() {
        cookiePollingRunnable?.let { handler.removeCallbacks(it) }
        cookiePollingRunnable = null
    }

    private fun extractSessionKey(): String? {
        return try {
            val cookies = CookieManager.getInstance().getCookie("https://claude.ai") ?: return null
            cookies.split(";")
                .map { it.trim() }
                .firstOrNull { it.startsWith("sessionKey=") }
                ?.substringAfter("sessionKey=")
                ?.trim()
                ?.takeIf { it.isNotBlank() }
        } catch (_: Exception) {
            null
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        stopCookiePolling()
    }

    companion object {
        const val EXTRA_SESSION_KEY = "session_key"

        // JS to hide Google OAuth button and show email login guidance
        private const val HIDE_GOOGLE_BUTTON_JS = """
            (function() {
                var style = document.createElement('style');
                style.textContent = `
                    button[data-testid="google-auth-button"],
                    a[href*="accounts.google.com"],
                    button:has(img[alt*="Google"]),
                    button:has(svg) ~ button:has(svg) {
                        display: none !important;
                    }
                `;
                document.head.appendChild(style);

                var buttons = document.querySelectorAll('button');
                buttons.forEach(function(btn) {
                    var text = btn.textContent || '';
                    if (text.toLowerCase().includes('google')) {
                        btn.style.display = 'none';
                    }
                });
            })();
        """
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WebViewErrorScreen(
    message: String,
    onClose: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Sign in to Claude", fontSize = 18.sp) },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = TextSecondary)
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "WebView Unavailable",
                fontSize = 18.sp,
                color = TextPrimary
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = message,
                fontSize = 14.sp,
                color = TextSecondary
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Please use \"Enter session key manually\" instead.",
                fontSize = 14.sp,
                color = TextSecondary
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun LoginWebViewScreen(
    onSessionCaptured: (String) -> Unit,
    onClose: () -> Unit,
    onError: (String) -> Unit,
    onStartPolling: ((String) -> Unit) -> Unit
) {
    var isLoading by remember { mutableStateOf(true) }
    var showEmailHint by remember { mutableStateOf(false) }

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
                    try {
                        WebView(context).apply {
                            settings.javaScriptEnabled = true
                            settings.domStorageEnabled = true
                            settings.setSupportMultipleWindows(false)
                            settings.javaScriptCanOpenWindowsAutomatically = true

                            val webView = this
                            try {
                                val cookieManager = CookieManager.getInstance()
                                cookieManager.setAcceptCookie(true)
                                cookieManager.setAcceptThirdPartyCookies(webView, true)
                                cookieManager.removeAllCookies(null)
                                cookieManager.flush()
                            } catch (_: Exception) {}

                            webViewClient = object : WebViewClient() {
                                override fun shouldOverrideUrlLoading(
                                    view: WebView?,
                                    request: WebResourceRequest?
                                ): Boolean {
                                    return false
                                }

                                override fun onPageFinished(view: WebView?, url: String?) {
                                    isLoading = false

                                    // Hide Google login button via JS
                                    view?.evaluateJavascript(
                                        LoginActivity.HIDE_GOOGLE_BUTTON_JS,
                                        null
                                    )

                                    // Show email hint on login page
                                    if (url?.contains("claude.ai/login") == true) {
                                        showEmailHint = true
                                    } else {
                                        showEmailHint = false
                                    }

                                    // Check for session cookie
                                    checkForSessionCookie(url, onSessionCaptured)

                                    // Start polling after login page (magic link flow)
                                    if (url?.contains("claude.ai") == true) {
                                        onStartPolling(onSessionCaptured)
                                    }
                                }
                            }

                            loadUrl("https://claude.ai/login")
                        }
                    } catch (e: Exception) {
                        onError("Failed to initialize WebView: ${e.message}")
                        android.view.View(context)
                    }
                },
                modifier = Modifier.fillMaxSize()
            )

            // Email login hint banner
            if (showEmailHint) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                        .padding(12.dp)
                ) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = ClaudePurpleDark
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(
                            text = "Use \"Continue with email\" to sign in.\nGoogle login is not supported in-app.",
                            modifier = Modifier.padding(16.dp),
                            color = TextPrimary,
                            fontSize = 13.sp,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }

            if (isLoading) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(
                        color = ClaudePurple,
                        strokeWidth = 3.dp
                    )
                }
            }
        }
    }
}

private fun checkForSessionCookie(url: String?, onSessionCaptured: (String) -> Unit) {
    try {
        val cookies = CookieManager.getInstance().getCookie("https://claude.ai") ?: return

        val sessionKey = cookies.split(";")
            .map { it.trim() }
            .firstOrNull { it.startsWith("sessionKey=") }
            ?.substringAfter("sessionKey=")
            ?.trim()

        if (!sessionKey.isNullOrBlank()) {
            onSessionCaptured(sessionKey)
        }
    } catch (_: Exception) {}
}
