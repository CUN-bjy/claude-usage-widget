package com.claudeusage.widget.data.local

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

data class UsageHistoryEntry(
    val timestamp: Long,
    val utilization: Double
)

class UsageHistoryStore(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences(
        PREFS_NAME, Context.MODE_PRIVATE
    )

    fun getHistory(): List<UsageHistoryEntry> {
        val json = prefs.getString(KEY_HISTORY, null) ?: return emptyList()
        return try {
            val array = JSONArray(json)
            (0 until array.length()).map { i ->
                val obj = array.getJSONObject(i)
                UsageHistoryEntry(
                    timestamp = obj.getLong("t"),
                    utilization = obj.getDouble("u")
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun addEntry(timestamp: Long, utilization: Double) {
        val history = getHistory().toMutableList()
        history.add(UsageHistoryEntry(timestamp, utilization))

        // Cap at max entries
        val trimmed = if (history.size > MAX_ENTRIES) {
            history.takeLast(MAX_ENTRIES)
        } else {
            history
        }

        saveHistory(trimmed)
    }

    fun clearHistory() {
        prefs.edit().remove(KEY_HISTORY).apply()
    }

    private fun saveHistory(entries: List<UsageHistoryEntry>) {
        val array = JSONArray()
        for (entry in entries) {
            val obj = JSONObject()
            obj.put("t", entry.timestamp)
            obj.put("u", entry.utilization)
            array.put(obj)
        }
        prefs.edit().putString(KEY_HISTORY, array.toString()).apply()
    }

    companion object {
        private const val PREFS_NAME = "claude_usage_history"
        private const val KEY_HISTORY = "history"
        private const val MAX_ENTRIES = 2016 // 7 days * 24h * 60min / 5min
    }
}
