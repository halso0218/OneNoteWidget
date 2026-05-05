package com.example.datetimewidget

import android.graphics.*
import java.util.Calendar
import kotlin.math.cos
import kotlin.math.sin

object ClockFaceRenderer {

    fun render(sizePx: Int, config: ClockConfig, cal: Calendar): Bitmap {
        val bm = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bm)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val cx = sizePx / 2f
        val cy = sizePx / 2f
        val r = sizePx / 2f - 2f

        paint.style = Paint.Style.FILL
        paint.color = config.backgroundColor

        if (config.face == ClockFace.DIGITAL) {
            // AMeDASと同じ角丸四角形の背景
            val cornerR = sizePx * 0.10f
            canvas.drawRoundRect(RectF(1f, 1f, sizePx - 1f, sizePx - 1f), cornerR, cornerR, paint)

            val hour = cal.get(Calendar.HOUR_OF_DAY)
            val minute = cal.get(Calendar.MINUTE)
            paint.color = config.handColor
            paint.typeface = Typeface.DEFAULT_BOLD
            paint.textAlign = Paint.Align.CENTER
            paint.textSize = sizePx * 0.38f
            val fm = paint.fontMetrics
            val lineH = fm.descent - fm.ascent
            val gap = sizePx * 0.01f
            val blockH = lineH * 2 + gap
            val firstBaseline = (sizePx - blockH) / 2f - fm.ascent
            canvas.drawText("%02d".format(hour), cx, firstBaseline, paint)
            canvas.drawText("%02d".format(minute), cx, firstBaseline + lineH + gap, paint)
            return bm
        }

        canvas.drawCircle(cx, cy, r, paint)
        paint.color = config.handColor

        when (config.face) {
            ClockFace.CLASSIC -> {
                for (i in 0..11) {
                    val angle = Math.toRadians(i * 30.0 - 90)
                    val mr = r * 0.82f
                    val dotR = if (i % 3 == 0) r * 0.08f else r * 0.04f
                    canvas.drawCircle(
                        cx + (mr * cos(angle)).toFloat(),
                        cy + (mr * sin(angle)).toFloat(),
                        dotR, paint
                    )
                }
            }
            ClockFace.SIMPLE -> {
                for (i in listOf(0, 3, 6, 9)) {
                    val angle = Math.toRadians(i * 30.0 - 90)
                    val mr = r * 0.82f
                    canvas.drawCircle(
                        cx + (mr * cos(angle)).toFloat(),
                        cy + (mr * sin(angle)).toFloat(),
                        r * 0.08f, paint
                    )
                }
            }
            else -> Unit
        }

        val hour = cal.get(Calendar.HOUR)
        val minute = cal.get(Calendar.MINUTE)

        paint.style = Paint.Style.STROKE
        paint.strokeCap = Paint.Cap.ROUND

        paint.strokeWidth = r * 0.10f
        val hourAngle = Math.toRadians((hour + minute / 60.0) * 30.0 - 90)
        canvas.drawLine(cx, cy,
            cx + (r * 0.50f * cos(hourAngle)).toFloat(),
            cy + (r * 0.50f * sin(hourAngle)).toFloat(),
            paint)

        paint.strokeWidth = r * 0.06f
        val minAngle = Math.toRadians(minute * 6.0 - 90)
        canvas.drawLine(cx, cy,
            cx + (r * 0.72f * cos(minAngle)).toFloat(),
            cy + (r * 0.72f * sin(minAngle)).toFloat(),
            paint)

        paint.style = Paint.Style.FILL
        canvas.drawCircle(cx, cy, r * 0.06f, paint)

        return bm
    }
}
