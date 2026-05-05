package com.example.datetimewidget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class WeatherFetchWorker(appContext: Context, params: WorkerParameters) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        return withContext(Dispatchers.IO) {
            try {
                val context = applicationContext
                val awm = AppWidgetManager.getInstance(context)
                val ids = awm.getAppWidgetIds(ComponentName(context, DateTimeWidget::class.java))
                android.util.Log.d("WeatherWorker", "doWork: widgetIds=${ids.toList()}")

                if (ids.isEmpty()) return@withContext Result.success()

                if (!AmedasCache.isLoaded()) AmedasCache.load(context)

                val stationIds = ids.map { WidgetPrefs.load(context, it).stationId }.distinct()
                android.util.Log.d("WeatherWorker", "fetching for stations: $stationIds")

                var anySuccess = false
                stationIds.forEach { stationId ->
                    val data = JmaWeatherClient.fetchWeather(stationId)
                    android.util.Log.d("WeatherWorker", "stationId=$stationId temp=${data.tempCelsius} code=${data.weatherCode}")
                    val icon = JmaWeatherClient.weatherCodeToEmoji(data.weatherCode)
                    val temp = data.tempCelsius?.let { "%.1f°C".format(it) } ?: "--°C"

                    if (data.tempCelsius != null || data.weatherCode != null) {
                        DateTimeWidget.saveWeather(context, stationId, icon, temp, data.observationTime)
                        val affected = ids.filter { WidgetPrefs.load(context, it).stationId == stationId }
                        affected.forEach { DateTimeWidget.updateWidget(context, awm, it) }
                        anySuccess = true
                    } else {
                        android.util.Log.w("WeatherWorker", "both null for $stationId, will retry")
                    }
                }

                if (anySuccess) {
                    context.getSharedPreferences("widget_weather", Context.MODE_PRIVATE)
                        .edit().putLong("last_fetch", System.currentTimeMillis()).apply()
                    Result.success()
                } else {
                    Result.retry()
                }
            } catch (e: Exception) {
                android.util.Log.e("WeatherWorker", "fetch failed", e)
                Result.retry()
            }
        }
    }
}
