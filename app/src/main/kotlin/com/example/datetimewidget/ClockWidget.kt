package com.example.datetimewidget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.widget.RemoteViews
import java.util.Calendar

open class ClockWidget : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        appWidgetIds.forEach { updateWidget(context, appWidgetManager, it) }
    }

    override fun onEnabled(context: Context) {
        WidgetUpdateService.start(context)
    }

    override fun onDisabled(context: Context) {
        val awm = AppWidgetManager.getInstance(context)
        val amedasIds = awm.getAppWidgetIds(ComponentName(context, DateTimeWidget::class.java))
        if (amedasIds.isEmpty() && allIds(context, awm).isEmpty()) WidgetUpdateService.stop(context)
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        appWidgetIds.forEach { WidgetPrefs.deleteClockConfig(context, it) }
    }

    companion object {
        fun allIds(context: Context, awm: AppWidgetManager = AppWidgetManager.getInstance(context)): IntArray =
            awm.getAppWidgetIds(ComponentName(context, ClockWidget::class.java))

        fun updateWidget(context: Context, awm: AppWidgetManager, widgetId: Int) {
            val config = WidgetPrefs.loadClockConfig(context, widgetId)
            val size = (context.resources.displayMetrics.density * 200).toInt()
            val bm = ClockFaceRenderer.render(size, config, Calendar.getInstance())
            val views = RemoteViews(context.packageName, R.layout.widget_clock)
            views.setImageViewBitmap(R.id.clock_image, bm)
            awm.updateAppWidget(widgetId, views)
        }
    }
}
