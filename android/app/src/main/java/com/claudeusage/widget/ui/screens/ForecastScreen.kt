package com.claudeusage.widget.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.text.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.claudeusage.widget.data.local.UsageHistoryEntry
import com.claudeusage.widget.data.model.UsageData
import com.claudeusage.widget.ui.theme.*
import java.time.Duration
import java.time.Instant

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ForecastScreen(
    usageData: UsageData?,
    history: List<UsageHistoryEntry>,
    onBack: () -> Unit
) {
    val weeklyReset = usageData?.sevenDay?.resetsAt
    val weeklyUtil = usageData?.sevenDay?.utilization ?: 0.0

    // Calculate week boundaries
    val now = Instant.now()
    val weekEndMs = weeklyReset?.toEpochMilli() ?: (now.toEpochMilli() + Duration.ofDays(7).toMillis())
    val weekDurationMs = Duration.ofDays(7).toMillis()
    val weekStartMs = weekEndMs - weekDurationMs
    val totalWeekMs = weekDurationMs.toDouble()

    // Elapsed fraction
    val elapsedMs = (now.toEpochMilli() - weekStartMs).coerceAtLeast(0)
    val elapsedDays = elapsedMs / (1000.0 * 60 * 60 * 24)
    val remainingDays = 7.0 - elapsedDays

    // Burning rate calculation
    val burningRatePerHour = if (elapsedDays > 0) weeklyUtil / (elapsedDays * 24) else 0.0
    val remainingHours = remainingDays * 24
    val projectedTotal = weeklyUtil + burningRatePerHour * remainingHours
    val depletionHoursFromNow = if (burningRatePerHour > 0) {
        (100.0 - weeklyUtil) / burningRatePerHour
    } else Double.MAX_VALUE

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Weekly Usage Forecast",
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
            // Graph card
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = DarkCard),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    val textMeasurer = rememberTextMeasurer()

                    Canvas(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(250.dp)
                    ) {
                        val leftPad = 40.dp.toPx()
                        val rightPad = 16.dp.toPx()
                        val topPad = 16.dp.toPx()
                        val bottomPad = 30.dp.toPx()

                        val graphWidth = size.width - leftPad - rightPad
                        val graphHeight = size.height - topPad - bottomPad

                        // Danger zone (above 80%)
                        val dangerY = topPad + graphHeight * (1 - 0.8f)
                        drawRect(
                            color = StatusCritical.copy(alpha = 0.08f),
                            topLeft = Offset(leftPad, topPad),
                            size = androidx.compose.ui.geometry.Size(graphWidth, dangerY - topPad)
                        )

                        // Grid lines + Y-axis labels
                        val ySteps = listOf(0, 25, 50, 75, 100)
                        for (pct in ySteps) {
                            val y = topPad + graphHeight * (1 - pct / 100f)
                            drawLine(
                                color = TextMuted.copy(alpha = 0.2f),
                                start = Offset(leftPad, y),
                                end = Offset(leftPad + graphWidth, y),
                                strokeWidth = 1.dp.toPx()
                            )
                            // Y-axis label
                            val label = textMeasurer.measure(
                                text = AnnotatedString("${pct}%"),
                                style = TextStyle(
                                    fontSize = 9.sp,
                                    color = TextMuted
                                )
                            )
                            drawText(
                                textLayoutResult = label,
                                topLeft = Offset(
                                    leftPad - label.size.width - 4.dp.toPx(),
                                    y - label.size.height / 2
                                )
                            )
                        }

                        // X-axis labels (Day 0 to Day 7)
                        for (day in 0..7) {
                            val x = leftPad + graphWidth * (day / 7f)
                            val label = textMeasurer.measure(
                                text = AnnotatedString("D$day"),
                                style = TextStyle(
                                    fontSize = 9.sp,
                                    color = TextMuted
                                )
                            )
                            drawText(
                                textLayoutResult = label,
                                topLeft = Offset(
                                    x - label.size.width / 2,
                                    topPad + graphHeight + 8.dp.toPx()
                                )
                            )
                        }

                        // Axes
                        drawLine(
                            color = TextMuted.copy(alpha = 0.3f),
                            start = Offset(leftPad, topPad),
                            end = Offset(leftPad, topPad + graphHeight),
                            strokeWidth = 1.dp.toPx()
                        )
                        drawLine(
                            color = TextMuted.copy(alpha = 0.3f),
                            start = Offset(leftPad, topPad + graphHeight),
                            end = Offset(leftPad + graphWidth, topPad + graphHeight),
                            strokeWidth = 1.dp.toPx()
                        )

                        // Draw history polyline
                        if (history.size >= 2) {
                            val sortedHistory = history.sortedBy { it.timestamp }
                            for (i in 0 until sortedHistory.size - 1) {
                                val e1 = sortedHistory[i]
                                val e2 = sortedHistory[i + 1]
                                val x1 = leftPad + graphWidth * ((e1.timestamp - weekStartMs) / totalWeekMs).toFloat().coerceIn(0f, 1f)
                                val y1 = topPad + graphHeight * (1 - (e1.utilization / 100.0).toFloat().coerceIn(0f, 1f))
                                val x2 = leftPad + graphWidth * ((e2.timestamp - weekStartMs) / totalWeekMs).toFloat().coerceIn(0f, 1f)
                                val y2 = topPad + graphHeight * (1 - (e2.utilization / 100.0).toFloat().coerceIn(0f, 1f))
                                drawLine(
                                    color = ClaudePurple,
                                    start = Offset(x1, y1),
                                    end = Offset(x2, y2),
                                    strokeWidth = 2.5.dp.toPx(),
                                    cap = StrokeCap.Round
                                )
                            }
                        }

                        // Current position dot
                        val currentX = leftPad + graphWidth * (elapsedMs / totalWeekMs).toFloat().coerceIn(0f, 1f)
                        val currentY = topPad + graphHeight * (1 - (weeklyUtil / 100.0).toFloat().coerceIn(0f, 1f))
                        drawCircle(
                            color = ClaudePurpleLight,
                            radius = 5.dp.toPx(),
                            center = Offset(currentX, currentY)
                        )
                        drawCircle(
                            color = ClaudePurple,
                            radius = 3.dp.toPx(),
                            center = Offset(currentX, currentY)
                        )

                        // Projection line (dashed)
                        val projEndUtil = projectedTotal.coerceAtMost(100.0)
                        val projEndX = leftPad + graphWidth
                        val projEndY = topPad + graphHeight * (1 - (projEndUtil / 100.0).toFloat().coerceIn(0f, 1f))

                        // If depletion before end of week, draw to depletion point, then flat at 100%
                        if (projectedTotal >= 100.0 && depletionHoursFromNow < remainingHours) {
                            val depletionFraction = ((elapsedMs + depletionHoursFromNow * 3600 * 1000) / totalWeekMs).toFloat().coerceIn(0f, 1f)
                            val depletionX = leftPad + graphWidth * depletionFraction
                            val depletionY = topPad // 100%

                            // Dashed line to depletion
                            drawDashedLine(
                                color = ClaudePurpleLight.copy(alpha = 0.6f),
                                start = Offset(currentX, currentY),
                                end = Offset(depletionX, depletionY),
                                strokeWidth = 2.dp.toPx()
                            )

                            // Depletion marker
                            drawCircle(
                                color = StatusCritical,
                                radius = 5.dp.toPx(),
                                center = Offset(depletionX, depletionY)
                            )

                            // Flat at 100% from depletion to end
                            if (depletionFraction < 1f) {
                                drawDashedLine(
                                    color = StatusCritical.copy(alpha = 0.4f),
                                    start = Offset(depletionX, depletionY),
                                    end = Offset(projEndX, depletionY),
                                    strokeWidth = 2.dp.toPx()
                                )
                            }
                        } else {
                            // Simple projection to end of week
                            drawDashedLine(
                                color = ClaudePurpleLight.copy(alpha = 0.6f),
                                start = Offset(currentX, currentY),
                                end = Offset(projEndX, projEndY),
                                strokeWidth = 2.dp.toPx()
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Stats row
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = DarkCard),
                shape = RoundedCornerShape(16.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    StatItem(
                        label = "Burning Rate",
                        value = String.format("%.2f%%/h", burningRatePerHour),
                        color = ClaudePurpleLight
                    )
                    StatItem(
                        label = "Day",
                        value = String.format("%.1f / 7", elapsedDays),
                        color = TextPrimary
                    )
                    StatItem(
                        label = "Depletion",
                        value = if (projectedTotal >= 100.0 && depletionHoursFromNow < remainingHours) {
                            formatDepletionTime(depletionHoursFromNow)
                        } else {
                            "Safe"
                        },
                        color = if (projectedTotal >= 100.0 && depletionHoursFromNow < remainingHours) {
                            StatusCritical
                        } else {
                            StatusExtra
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Legend
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = DarkCard),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    LegendItem(color = ClaudePurple, label = "Actual usage")
                    Spacer(modifier = Modifier.height(8.dp))
                    LegendItem(color = ClaudePurpleLight.copy(alpha = 0.6f), label = "Projected usage", dashed = true)
                    Spacer(modifier = Modifier.height(8.dp))
                    LegendItem(color = StatusCritical.copy(alpha = 0.15f), label = "Danger zone (>80%)")
                    if (projectedTotal >= 100.0 && depletionHoursFromNow < remainingHours) {
                        Spacer(modifier = Modifier.height(8.dp))
                        LegendItem(color = StatusCritical, label = "Projected depletion point")
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun StatItem(
    label: String,
    value: String,
    color: Color
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            color = color,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = label,
            color = TextMuted,
            fontSize = 11.sp
        )
    }
}

@Composable
private fun LegendItem(
    color: Color,
    label: String,
    dashed: Boolean = false
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (dashed) {
            Canvas(modifier = Modifier.size(16.dp, 3.dp)) {
                drawLine(
                    color = color,
                    start = Offset(0f, size.height / 2),
                    end = Offset(size.width, size.height / 2),
                    strokeWidth = 2.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(
                        floatArrayOf(4.dp.toPx(), 3.dp.toPx())
                    )
                )
            }
        } else {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .background(color, CircleShape)
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = label,
            color = TextSecondary,
            fontSize = 12.sp
        )
    }
}

private fun DrawScope.drawDashedLine(
    color: Color,
    start: Offset,
    end: Offset,
    strokeWidth: Float
) {
    drawLine(
        color = color,
        start = start,
        end = end,
        strokeWidth = strokeWidth,
        cap = StrokeCap.Round,
        pathEffect = PathEffect.dashPathEffect(
            floatArrayOf(8.dp.toPx(), 6.dp.toPx())
        )
    )
}

private fun formatDepletionTime(hours: Double): String {
    return when {
        hours < 1 -> "${(hours * 60).toInt()}m"
        hours < 24 -> String.format("%.1fh", hours)
        else -> String.format("%.1fd", hours / 24)
    }
}
