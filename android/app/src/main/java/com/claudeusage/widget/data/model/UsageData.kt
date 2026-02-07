package com.claudeusage.widget.data.model

import org.json.JSONObject
import java.time.Instant
import java.time.Duration

data class UsageMetric(
    val utilization: Double,
    val resetsAt: Instant?
) {
    val remainingDuration: Duration?
        get() = resetsAt?.let {
            val now = Instant.now()
            if (it.isAfter(now)) Duration.between(now, it) else Duration.ZERO
        }

    val isExpired: Boolean
        get() = resetsAt?.let { Instant.now().isAfter(it) } ?: false

    val statusLevel: StatusLevel
        get() = when {
            utilization >= 90.0 -> StatusLevel.CRITICAL
            utilization >= 75.0 -> StatusLevel.WARNING
            else -> StatusLevel.NORMAL
        }

    companion object {
        fun fromJson(json: JSONObject?): UsageMetric? {
            if (json == null) return null
            return try {
                UsageMetric(
                    utilization = json.optDouble("utilization", 0.0),
                    resetsAt = json.optString("resets_at", "").takeIf { it.isNotEmpty() }
                        ?.let { Instant.parse(it) }
                )
            } catch (e: Exception) {
                null
            }
        }
    }
}

enum class StatusLevel {
    NORMAL, WARNING, CRITICAL
}

data class UsageData(
    val fiveHour: UsageMetric?,
    val sevenDay: UsageMetric?,
    val sevenDaySonnet: UsageMetric?,
    val sevenDayOpus: UsageMetric?,
    val sevenDayCowork: UsageMetric?,
    val sevenDayOauthApps: UsageMetric?,
    val extraUsage: UsageMetric?,
    val fetchedAt: Instant = Instant.now()
) {
    val extraMetrics: List<Pair<String, UsageMetric>>
        get() = listOfNotNull(
            sevenDaySonnet?.let { "Sonnet (7d)" to it },
            sevenDayOpus?.let { "Opus (7d)" to it },
            sevenDayCowork?.let { "Cowork (7d)" to it },
            sevenDayOauthApps?.let { "OAuth Apps (7d)" to it },
            extraUsage?.let { "Extra Usage" to it }
        ).filter { it.second.utilization > 0 }

    companion object {
        fun fromJson(json: JSONObject): UsageData {
            return UsageData(
                fiveHour = UsageMetric.fromJson(json.optJSONObject("five_hour")),
                sevenDay = UsageMetric.fromJson(json.optJSONObject("seven_day")),
                sevenDaySonnet = UsageMetric.fromJson(json.optJSONObject("seven_day_sonnet")),
                sevenDayOpus = UsageMetric.fromJson(json.optJSONObject("seven_day_opus")),
                sevenDayCowork = UsageMetric.fromJson(json.optJSONObject("seven_day_cowork")),
                sevenDayOauthApps = UsageMetric.fromJson(json.optJSONObject("seven_day_oauth_apps")),
                extraUsage = UsageMetric.fromJson(json.optJSONObject("extra_usage"))
            )
        }
    }
}
