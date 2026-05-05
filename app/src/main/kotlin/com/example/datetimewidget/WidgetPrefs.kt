package com.example.datetimewidget

import android.content.Context
import androidx.core.content.edit

enum class ClockFace { CLASSIC, SIMPLE, MINIMAL, DIGITAL }

data class ClockConfig(
    val backgroundColor: Int = 0xAA000000.toInt(),
    val handColor: Int = 0xFFFFFFFF.toInt(),
    val face: ClockFace = ClockFace.CLASSIC
)

data class WidgetConfig(
    val stationId: String = "44132",
    val stationName: String = "東京",
    val backgroundColor: Int = 0xAA000000.toInt(),
    val textColor: Int = 0xFFFFFFFF.toInt(),
    val textShadow: Boolean = false
)

object WidgetPrefs {
    private fun prefs(context: Context) =
        context.getSharedPreferences("widget_prefs", Context.MODE_PRIVATE)

    fun save(context: Context, widgetId: Int, config: WidgetConfig) {
        prefs(context).edit {
            putString("${widgetId}_station_id", config.stationId)
            putString("${widgetId}_station_name", config.stationName)
            putInt("${widgetId}_bg_color", config.backgroundColor)
            putInt("${widgetId}_text_color", config.textColor)
            putBoolean("${widgetId}_shadow", config.textShadow)
        }
    }

    fun load(context: Context, widgetId: Int): WidgetConfig {
        val p = prefs(context)
        return WidgetConfig(
            stationId = p.getString("${widgetId}_station_id", "44132") ?: "44132",
            stationName = p.getString("${widgetId}_station_name", "東京") ?: "東京",
            backgroundColor = p.getInt("${widgetId}_bg_color", 0xAA000000.toInt()),
            textColor = p.getInt("${widgetId}_text_color", 0xFFFFFFFF.toInt()),
            textShadow = p.getBoolean("${widgetId}_shadow", false)
        )
    }

    fun delete(context: Context, widgetId: Int) {
        prefs(context).edit {
            remove("${widgetId}_station_id")
            remove("${widgetId}_station_name")
            remove("${widgetId}_bg_color")
            remove("${widgetId}_text_color")
            remove("${widgetId}_shadow")
        }
    }

    fun saveClockConfig(context: Context, widgetId: Int, config: ClockConfig) {
        prefs(context).edit {
            putInt("${widgetId}_clock_bg", config.backgroundColor)
            putInt("${widgetId}_clock_hand", config.handColor)
            putString("${widgetId}_clock_face", config.face.name)
        }
    }

    fun loadClockConfig(context: Context, widgetId: Int): ClockConfig {
        val p = prefs(context)
        return ClockConfig(
            backgroundColor = p.getInt("${widgetId}_clock_bg", 0xAA000000.toInt()),
            handColor = p.getInt("${widgetId}_clock_hand", 0xFFFFFFFF.toInt()),
            face = ClockFace.entries.firstOrNull { it.name == p.getString("${widgetId}_clock_face", "CLASSIC") }
                ?: ClockFace.CLASSIC
        )
    }

    fun deleteClockConfig(context: Context, widgetId: Int) {
        prefs(context).edit {
            remove("${widgetId}_clock_bg")
            remove("${widgetId}_clock_hand")
            remove("${widgetId}_clock_face")
        }
    }
}
