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
    val fetchedAt: Instant = Instant.now(),
    val rawKeys: List<String> = emptyList(),
    val debugInfo: String = ""
) {
    val extraMetrics: List<Pair<String, UsageMetric>>
        get() {
            val default = UsageMetric(0.0, null)
            return listOf(
                "Sonnet (7d)" to (sevenDaySonnet ?: default),
                "Opus (7d)" to (sevenDayOpus ?: default),
                "Cowork (7d)" to (sevenDayCowork ?: default),
                "OAuth Apps (7d)" to (sevenDayOauthApps ?: default),
                "Extra Usage" to (extraUsage ?: default)
            )
        }

    companion object {
        fun fromJson(json: JSONObject): UsageData {
            val keys = json.keys().asSequence().toList()
            val extraKeys = listOf("seven_day_sonnet", "seven_day_opus", "seven_day_cowork",
                "seven_day_oauth_apps", "extra_usage")
            val debug = extraKeys.joinToString(" | ") { key ->
                val raw = json.opt(key)
                val type = when {
                    raw == null || raw == JSONObject.NULL -> "null"
                    raw is JSONObject -> "obj(u=${raw.optDouble("utilization", -1.0)})"
                    else -> raw.javaClass.simpleName + "=" + raw.toString().take(30)
                }
                "$key:$type"
            }
            return UsageData(
                fiveHour = UsageMetric.fromJson(json.optJSONObject("five_hour")),
                sevenDay = UsageMetric.fromJson(json.optJSONObject("seven_day")),
                sevenDaySonnet = UsageMetric.fromJson(json.optJSONObject("seven_day_sonnet")),
                sevenDayOpus = UsageMetric.fromJson(json.optJSONObject("seven_day_opus")),
                sevenDayCowork = UsageMetric.fromJson(json.optJSONObject("seven_day_cowork")),
                sevenDayOauthApps = UsageMetric.fromJson(json.optJSONObject("seven_day_oauth_apps")),
                extraUsage = UsageMetric.fromJson(json.optJSONObject("extra_usage")),
                rawKeys = keys,
                debugInfo = debug
            )
        }
    }
}
