package com.example.datetimewidget

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.appwidget.AppWidgetManager
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager

class WidgetAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        android.util.Log.d("WidgetAlarm", "onReceive fired")
        val awm = AppWidgetManager.getInstance(context)
        val amedasIds = awm.getAppWidgetIds(ComponentName(context, DateTimeWidget::class.java))
        val clockIds = ClockWidget.allIds(context, awm)
        if (amedasIds.isEmpty() && clockIds.isEmpty()) {
            cancelAlarm(context)
            return
        }

        clockIds.forEach { ClockWidget.updateWidget(context, awm, it) }

        // 10分ごとに天気を更新
        if (amedasIds.isNotEmpty()) {
            val prefs = context.getSharedPreferences("widget_weather", Context.MODE_PRIVATE)
            val lastFetch = prefs.getLong("last_fetch", 0L)
            if (System.currentTimeMillis() - lastFetch > 10 * 60 * 1000L) {
                enqueueWeatherFetch(context)
            }
        }

        scheduleNext(context)
    }

    companion object {
        private const val INTERVAL_MS = 60 * 1000L  // 1分

        fun enqueueWeatherFetch(context: Context) {
            android.util.Log.d("WidgetAlarm", "enqueuing WeatherFetchWorker")
            WorkManager.getInstance(context).enqueueUniqueWork(
                "weather_fetch",
                ExistingWorkPolicy.KEEP,
                OneTimeWorkRequestBuilder<WeatherFetchWorker>().build()
            )
        }

        fun scheduleNext(context: Context) {
            val alarmManager = context.getSystemService(AlarmManager::class.java)
            alarmManager.setAndAllowWhileIdle(
                AlarmManager.RTC,
                System.currentTimeMillis() + INTERVAL_MS,
                pendingIntent(context)
            )
        }

        fun cancelAlarm(context: Context) {
            context.getSystemService(AlarmManager::class.java)
                .cancel(pendingIntent(context))
        }

        private fun pendingIntent(context: Context) = PendingIntent.getBroadcast(
            context, 0,
            Intent(context, WidgetAlarmReceiver::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}

object WidgetUpdateService {
    fun start(context: Context) = WidgetAlarmReceiver.scheduleNext(context)
    fun stop(context: Context) = WidgetAlarmReceiver.cancelAlarm(context)
}
