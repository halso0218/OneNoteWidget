package com.example.datetimewidget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.widget.RemoteViews

class DateTimeWidget : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        appWidgetIds.forEach { updateWidget(context, appWidgetManager, it) }
        val prefs = context.getSharedPreferences("widget_weather", Context.MODE_PRIVATE)
        val lastFetch = prefs.getLong("last_fetch", 0L)
        if (System.currentTimeMillis() - lastFetch > 10 * 60 * 1000L) {
            WidgetAlarmReceiver.enqueueWeatherFetch(context)
        }
    }

    override fun onEnabled(context: Context) {
        WidgetUpdateService.start(context)
    }

    override fun onDisabled(context: Context) {
        if (ClockWidget.allIds(context).isEmpty()) WidgetUpdateService.stop(context)
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        appWidgetIds.forEach { WidgetPrefs.delete(context, it) }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == Intent.ACTION_USER_PRESENT) {
            // 画面ロック解除時: 天気が古ければ即フェッチ
            val prefs = context.getSharedPreferences("widget_weather", Context.MODE_PRIVATE)
            val lastFetch = prefs.getLong("last_fetch", 0L)
            if (System.currentTimeMillis() - lastFetch > 10 * 60 * 1000L) {
                WidgetAlarmReceiver.enqueueWeatherFetch(context)
            }
        }
    }

    companion object {
        fun saveWeather(context: Context, stationId: String, icon: String, temp: String, obsTime: String?) {
            context.getSharedPreferences("widget_weather", Context.MODE_PRIVATE).edit()
                .putString("icon_$stationId", icon)
                .putString("temp_$stationId", temp)
                .putString("obs_time_$stationId", obsTime)
                .putLong("updated_$stationId", System.currentTimeMillis())
                .apply()
        }

        private const val STALE_MS = 30 * 60 * 1000L

        fun updateWidget(context: Context, awm: AppWidgetManager, widgetId: Int) {
            val config = WidgetPrefs.load(context, widgetId)
            val layout = if (config.textShadow) R.layout.widget_datetime_shadow
                         else R.layout.widget_datetime
            val views = RemoteViews(context.packageName, layout)

            val weatherPrefs = context.getSharedPreferences("widget_weather", Context.MODE_PRIVATE)
            val updatedAt = weatherPrefs.getLong("updated_${config.stationId}", 0L)
            val isStale = updatedAt > 0L && System.currentTimeMillis() - updatedAt > STALE_MS

            val icon = if (isStale) "🔄" else weatherPrefs.getString("icon_${config.stationId}", "🌡") ?: "🌡"
            val temp = if (isStale) "--°C" else weatherPrefs.getString("temp_${config.stationId}", "--°C") ?: "--°C"
            val time = weatherPrefs.getString("obs_time_${config.stationId}", null) ?: "--:--"

            views.setTextViewText(R.id.widget_weather_icon, icon)
            views.setTextViewText(R.id.widget_temp, temp)
            views.setTextViewText(R.id.widget_station, config.stationName)
            views.setTextViewText(R.id.widget_update_time, time)

            views.setInt(R.id.widget_temp, "setTextColor", config.textColor)
            val subColor = Color.argb(
                (Color.alpha(config.textColor) * 0.7).toInt(),
                Color.red(config.textColor),
                Color.green(config.textColor),
                Color.blue(config.textColor)
            )
            views.setInt(R.id.widget_station, "setTextColor", subColor)
            views.setInt(R.id.widget_update_time, "setTextColor", subColor)
            views.setInt(R.id.widget_root, "setBackgroundColor", config.backgroundColor)

            awm.updateAppWidget(widgetId, views)
        }
    }
}
