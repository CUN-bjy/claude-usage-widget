package com.claudeusage.widget.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.claudeusage.widget.data.model.UsageData
import com.claudeusage.widget.ui.components.UsageProgressBar
import com.claudeusage.widget.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UsageScreen(
    uiState: UiState,
    isRefreshing: Boolean,
    lastUpdated: String?,
    onRefresh: () -> Unit,
    onLogout: () -> Unit,
    onLoginClick: () -> Unit,
    onManualLogin: (String) -> Unit,
    onSettingsClick: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Claude Usage",
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp
                    )
                },
                actions = {
                    if (uiState is UiState.Success) {
                        IconButton(onClick = onSettingsClick) {
                            Icon(
                                Icons.Default.Settings,
                                contentDescription = "Settings",
                                tint = TextSecondary
                            )
                        }
                        IconButton(onClick = onRefresh, enabled = !isRefreshing) {
                            if (isRefreshing) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    strokeWidth = 2.dp,
                                    color = ClaudePurpleLight
                                )
                            } else {
                                Icon(
                                    Icons.Default.Refresh,
                                    contentDescription = "Refresh",
                                    tint = TextSecondary
                                )
                            }
                        }
                        IconButton(onClick = onLogout) {
                            Icon(
                                Icons.Default.Logout,
                                contentDescription = "Logout",
                                tint = TextSecondary
                            )
                        }
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
            when (uiState) {
                is UiState.Loading -> LoadingContent()
                is UiState.LoginRequired -> LoginContent(
                    onLoginClick = onLoginClick,
                    onManualLogin = onManualLogin
                )
                is UiState.Success -> UsageContent(
                    data = uiState.data,
                    lastUpdated = lastUpdated
                )
                is UiState.Error -> ErrorContent(
                    message = uiState.message,
                    isAuthError = uiState.isAuthError,
                    onRetry = if (uiState.isAuthError) onLoginClick else onRefresh
                )
            }
        }
    }
}

@Composable
private fun LoadingContent() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator(
                color = ClaudePurple,
                strokeWidth = 3.dp
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Loading usage data...",
                color = TextSecondary,
                fontSize = 14.sp
            )
        }
    }
}

@Composable
private fun LoginContent(
    onLoginClick: () -> Unit,
    onManualLogin: (String) -> Unit
) {
    var showManualInput by remember { mutableStateOf(false) }
    var sessionKeyInput by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Logo placeholder
        Box(
            modifier = Modifier
                .size(80.dp)
                .clip(CircleShape)
                .background(DarkSurfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "C",
                fontSize = 36.sp,
                fontWeight = FontWeight.Bold,
                color = ClaudePurple
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "Claude Usage Widget",
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            color = TextPrimary
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Sign in to monitor your Claude usage",
            fontSize = 14.sp,
            color = TextSecondary,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(32.dp))

        Button(
            onClick = onLoginClick,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = ClaudePurple
            ),
            shape = RoundedCornerShape(12.dp)
        ) {
            Text(
                text = "Sign in with Claude.ai",
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        OutlinedButton(
            onClick = { showManualInput = !showManualInput },
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
            colors = ButtonDefaults.outlinedButtonColors(
                contentColor = TextSecondary
            ),
            shape = RoundedCornerShape(12.dp)
        ) {
            Text(
                text = "Enter session key manually",
                fontSize = 14.sp
            )
        }

        AnimatedVisibility(visible = showManualInput) {
            Column(modifier = Modifier.padding(top = 16.dp)) {
                OutlinedTextField(
                    value = sessionKeyInput,
                    onValueChange = { sessionKeyInput = it },
                    label = { Text("Session Key") },
                    placeholder = { Text("sk-ant-...") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = ClaudePurple,
                        unfocusedBorderColor = DarkSurfaceVariant,
                        focusedLabelColor = ClaudePurple,
                        cursorColor = ClaudePurple
                    ),
                    shape = RoundedCornerShape(12.dp)
                )

                Spacer(modifier = Modifier.height(12.dp))

                Button(
                    onClick = {
                        if (sessionKeyInput.isNotBlank()) {
                            onManualLogin(sessionKeyInput.trim())
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = sessionKeyInput.isNotBlank(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = ClaudePurpleDark
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Connect")
                }
            }
        }
    }
}

@Composable
private fun UsageContent(
    data: UsageData,
    lastUpdated: String?
) {
    var expandedExtras by remember { mutableStateOf(false) }
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(horizontal = 20.dp, vertical = 8.dp)
    ) {
        // Main usage cards
        if (data.fiveHour != null) {
            UsageCard(
                title = "Current Session",
                subtitle = "5-hour window",
                metric = data.fiveHour,
                totalWindowHours = 5.0
            )
            Spacer(modifier = Modifier.height(12.dp))
        }

        if (data.sevenDay != null) {
            UsageCard(
                title = "Weekly Limit",
                subtitle = "7-day window",
                metric = data.sevenDay,
                totalWindowHours = 168.0
            )
        }

        // Extra metrics
        if (data.extraMetrics.isNotEmpty()) {
            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { expandedExtras = !expandedExtras }
                    .padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Additional Metrics",
                    color = TextSecondary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium
                )
                Icon(
                    imageVector = if (expandedExtras) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = "Toggle",
                    tint = TextMuted,
                    modifier = Modifier.size(20.dp)
                )
            }

            AnimatedVisibility(
                visible = expandedExtras,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Column {
                    data.extraMetrics.forEach { (label, metric) ->
                        Spacer(modifier = Modifier.height(8.dp))
                        MiniUsageCard(label = label, metric = metric)
                    }
                }
            }
        }

        // Last updated
        if (lastUpdated != null) {
            Spacer(modifier = Modifier.height(20.dp))
            Text(
                text = "Last updated at $lastUpdated",
                color = TextMuted,
                fontSize = 11.sp,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center
            )
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
private fun UsageCard(
    title: String,
    subtitle: String,
    metric: com.claudeusage.widget.data.model.UsageMetric,
    totalWindowHours: Double = 5.0
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = DarkCard),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = title,
                        color = TextPrimary,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = subtitle,
                        color = TextMuted,
                        fontSize = 11.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            UsageProgressBar(
                label = "",
                utilization = metric.utilization,
                statusLevel = metric.statusLevel,
                remainingDuration = metric.remainingDuration,
                totalWindowHours = totalWindowHours
            )
        }
    }
}

@Composable
private fun MiniUsageCard(
    label: String,
    metric: com.claudeusage.widget.data.model.UsageMetric,
    totalWindowHours: Double = 168.0
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = DarkCard.copy(alpha = 0.7f)),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            UsageProgressBar(
                label = label,
                utilization = metric.utilization,
                statusLevel = metric.statusLevel,
                remainingDuration = metric.remainingDuration,
                totalWindowHours = totalWindowHours
            )
        }
    }
}

@Composable
private fun ErrorContent(
    message: String,
    isAuthError: Boolean,
    onRetry: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = if (isAuthError) "Session Expired" else "Error",
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = StatusCritical
        )

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = message,
            fontSize = 14.sp,
            color = TextSecondary,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(24.dp))

        Button(
            onClick = onRetry,
            colors = ButtonDefaults.buttonColors(
                containerColor = ClaudePurple
            ),
            shape = RoundedCornerShape(12.dp)
        ) {
            Text(
                text = if (isAuthError) "Sign In Again" else "Retry",
                fontSize = 15.sp
            )
        }
    }
}
