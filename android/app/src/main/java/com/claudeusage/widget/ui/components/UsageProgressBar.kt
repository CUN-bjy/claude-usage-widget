package com.claudeusage.widget.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.claudeusage.widget.data.model.StatusLevel
import com.claudeusage.widget.ui.theme.*
import java.time.Duration
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun UsageProgressBar(
    label: String,
    utilization: Double,
    statusLevel: StatusLevel,
    remainingDuration: Duration?,
    totalWindowHours: Double = 5.0,
    modifier: Modifier = Modifier
) {
    val animatedProgress by animateFloatAsState(
        targetValue = (utilization / 100.0).toFloat().coerceIn(0f, 1f),
        animationSpec = tween(durationMillis = 800),
        label = "progress"
    )

    // Calculate remaining time as fraction of total window
    val remainingFraction = if (remainingDuration != null && totalWindowHours > 0) {
        val totalWindowSeconds = totalWindowHours * 3600
        val remainingSeconds = remainingDuration.seconds.toDouble()
        (remainingSeconds / totalWindowSeconds).toFloat().coerceIn(0f, 1f - animatedProgress)
    } else 0f

    val animatedRemaining by animateFloatAsState(
        targetValue = remainingFraction,
        animationSpec = tween(durationMillis = 800),
        label = "remaining"
    )

    val barColor = when (statusLevel) {
        StatusLevel.NORMAL -> StatusNormal
        StatusLevel.WARNING -> StatusWarning
        StatusLevel.CRITICAL -> StatusCritical
    }

    val gradient = when (statusLevel) {
        StatusLevel.NORMAL -> Brush.horizontalGradient(
            colors = listOf(ClaudePurpleDark, ClaudePurple, ClaudePurpleLight)
        )
        StatusLevel.WARNING -> Brush.horizontalGradient(
            colors = listOf(Color(0xFFCC7A20), StatusWarning, Color(0xFFF0B060))
        )
        StatusLevel.CRITICAL -> Brush.horizontalGradient(
            colors = listOf(Color(0xFFCC3030), StatusCritical, Color(0xFFF07070))
        )
    }

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                color = TextSecondary,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium
            )

            Row(verticalAlignment = Alignment.CenterVertically) {
                if (remainingDuration != null) {
                    CircularTimer(
                        remainingDuration = remainingDuration,
                        color = barColor,
                        modifier = Modifier.padding(end = 8.dp)
                    )
                }
                Text(
                    text = "${String.format("%.1f", utilization)}%",
                    color = barColor,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // Progress bar with usage + remaining time
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(10.dp)
                .clip(RoundedCornerShape(5.dp))
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val cornerRadius = CornerRadius(5.dp.toPx())

                // Track background
                drawRoundRect(
                    color = ProgressTrack,
                    cornerRadius = cornerRadius
                )

                // Remaining time portion (light purple, drawn first so usage overlaps)
                val totalFilledWidth = size.width * (animatedProgress + animatedRemaining)
                if (animatedRemaining > 0f) {
                    drawRoundRect(
                        color = ClaudePurple.copy(alpha = 0.25f),
                        size = Size(totalFilledWidth, size.height),
                        cornerRadius = cornerRadius
                    )
                }

                // Usage portion (solid color, on top)
                if (animatedProgress > 0f) {
                    drawRoundRect(
                        brush = gradient,
                        size = Size(size.width * animatedProgress, size.height),
                        cornerRadius = cornerRadius
                    )
                }
            }
        }

        if (remainingDuration != null) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = formatDuration(remainingDuration),
                color = TextMuted,
                fontSize = 11.sp
            )
        }
    }
}

@Composable
fun CircularTimer(
    remainingDuration: Duration,
    color: Color,
    modifier: Modifier = Modifier
) {
    val size = 18.dp

    Canvas(modifier = modifier.size(size)) {
        val strokeWidth = 2.dp.toPx()
        val radius = (this.size.minDimension - strokeWidth) / 2
        val center = Offset(this.size.width / 2, this.size.height / 2)

        drawCircle(
            color = ProgressTrack,
            radius = radius,
            center = center,
            style = Stroke(width = strokeWidth)
        )

        val totalSeconds = remainingDuration.seconds.toFloat()
        val progress = if (totalSeconds > 0) {
            (totalSeconds % 3600) / 3600f
        } else 0f

        drawArc(
            color = color,
            startAngle = -90f,
            sweepAngle = 360f * progress,
            useCenter = false,
            style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
            topLeft = Offset(center.x - radius, center.y - radius),
            size = Size(radius * 2, radius * 2)
        )

        val angle = (-90.0 + 360.0 * progress) * PI / 180.0
        val dotX = center.x + radius * 0.5f * cos(angle).toFloat()
        val dotY = center.y + radius * 0.5f * sin(angle).toFloat()
        drawCircle(
            color = color.copy(alpha = 0.6f),
            radius = 1.5.dp.toPx(),
            center = Offset(dotX, dotY)
        )
    }
}

fun formatDuration(duration: Duration): String {
    if (duration <= Duration.ZERO) return "Resetting..."

    val totalSeconds = duration.seconds
    val days = totalSeconds / 86400
    val hours = (totalSeconds % 86400) / 3600
    val minutes = (totalSeconds % 3600) / 60

    return when {
        days > 0 -> "Resets in ${days}d ${hours}h"
        hours > 0 -> "Resets in ${hours}h ${minutes}m"
        minutes > 0 -> "Resets in ${minutes}m"
        else -> "Resetting soon..."
    }
}
