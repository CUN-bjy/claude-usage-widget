package com.claudeusage.widget.widget

import android.content.Context
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver

class UsageWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = UsageAppWidget()

    companion object {
        fun updateWidget(context: Context) {
            // Trigger widget update via broadcast
            UsageAppWidget().updateAll(context)
        }
    }
}
