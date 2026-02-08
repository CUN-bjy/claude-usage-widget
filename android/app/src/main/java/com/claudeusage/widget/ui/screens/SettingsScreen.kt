package com.claudeusage.widget.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.claudeusage.widget.ui.theme.*

private const val GITHUB_URL = "https://github.com/CUN-bjy/claude-usage-widget"
private const val PRIVACY_POLICY_URL = "https://github.com/CUN-bjy/claude-usage-widget/blob/main/PRIVACY_POLICY.md"
private const val DONATE_URL = "https://buymeacoffee.com/dion_jy"
private const val APP_VERSION = "1.0.0"

data class MetricToggle(
    val key: String,
    val label: String,
    val enabled: Boolean
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    notificationEnabled: Boolean,
    onNotificationToggle: (Boolean) -> Unit,
    metricToggles: List<MetricToggle>,
    onMetricToggle: (String, Boolean) -> Unit,
    onBack: () -> Unit
) {
    val uriHandler = LocalUriHandler.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Settings",
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.Default.ArrowBack,
                            contentDescription = "Back",
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp)
        ) {
            // Notification section
            SectionLabel("Notification")
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = DarkCard),
                shape = RoundedCornerShape(16.dp)
            ) {
                SettingsToggleRow(
                    title = "Persistent Notification",
                    subtitle = "Show usage in the notification bar",
                    checked = notificationEnabled,
                    onCheckedChange = onNotificationToggle
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Display section
            SectionLabel("Display")
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = DarkCard),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column {
                    metricToggles.forEachIndexed { index, toggle ->
                        SettingsToggleRow(
                            title = toggle.label,
                            checked = toggle.enabled,
                            onCheckedChange = { onMetricToggle(toggle.key, it) }
                        )
                        if (index < metricToggles.lastIndex) {
                            Divider(
                                color = DarkBackground,
                                modifier = Modifier.padding(horizontal = 16.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // About section
            SectionLabel("About")
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = DarkCard),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column {
                    SettingsInfoRow(title = "Version", value = APP_VERSION)
                    SettingsDivider()
                    SettingsInfoRow(title = "Developer", value = "dion")
                    SettingsDivider()
                    SettingsLinkRow(title = "GitHub") {
                        uriHandler.openUri(GITHUB_URL)
                    }
                    SettingsDivider()
                    SettingsLinkRow(title = "Privacy Policy") {
                        uriHandler.openUri(PRIVACY_POLICY_URL)
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Buy Me a Coffee badge
            Button(
                onClick = { uriHandler.openUri(DONATE_URL) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFFFFDD00)
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(
                    text = "\u2615  Buy me a coffee",
                    color = Color.Black,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Disclaimer
            Text(
                text = "This app is not affiliated with or endorsed by Anthropic.",
                color = TextMuted,
                fontSize = 11.sp,
                modifier = Modifier.padding(horizontal = 4.dp)
            )

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        color = TextSecondary,
        fontSize = 13.sp,
        fontWeight = FontWeight.Medium,
        modifier = Modifier.padding(bottom = 8.dp, start = 4.dp)
    )
}

@Composable
private fun SettingsDivider() {
    Divider(
        color = DarkBackground,
        modifier = Modifier.padding(horizontal = 16.dp)
    )
}

@Composable
private fun SettingsToggleRow(
    title: String,
    subtitle: String? = null,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = TextPrimary,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium
            )
            if (subtitle != null) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    color = TextMuted,
                    fontSize = 13.sp
                )
            }
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = ClaudePurple,
                checkedTrackColor = ClaudePurpleDark
            )
        )
    }
}

@Composable
private fun SettingsInfoRow(title: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            color = TextPrimary,
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium
        )
        Text(
            text = value,
            color = TextMuted,
            fontSize = 14.sp
        )
    }
}

@Composable
private fun SettingsLinkRow(title: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            color = TextPrimary,
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium
        )
        Text(
            text = "\u203A",
            color = TextMuted,
            fontSize = 18.sp
        )
    }
}
