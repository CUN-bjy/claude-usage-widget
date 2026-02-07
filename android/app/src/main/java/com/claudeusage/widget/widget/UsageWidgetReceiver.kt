package com.claudeusage.widget.widget

import android.content.Context
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.updateAll
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class UsageWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = UsageAppWidget()

    companion object {
        fun updateWidget(context: Context) {
            CoroutineScope(Dispatchers.IO).launch {
                UsageAppWidget().updateAll(context)
            }
        }
    }
}
