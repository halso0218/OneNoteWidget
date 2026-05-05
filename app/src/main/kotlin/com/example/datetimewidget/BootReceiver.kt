package com.example.datetimewidget

import android.appwidget.AppWidgetManager
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val awm = AppWidgetManager.getInstance(context)
        val ids = awm.getAppWidgetIds(ComponentName(context, DateTimeWidget::class.java))
        if (ids.isNotEmpty()) {
            WidgetUpdateService.start(context)
        }
    }
}
