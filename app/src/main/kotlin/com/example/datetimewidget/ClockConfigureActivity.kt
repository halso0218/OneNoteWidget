package com.example.datetimewidget

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.*
import java.util.Calendar

class ClockConfigureActivity : Activity() {

    private var widgetId = AppWidgetManager.INVALID_APPWIDGET_ID
    private var selectedBgColor = 0xAA000000.toInt()
    private var selectedHandColor = 0xFFFFFFFF.toInt()
    private var selectedFace = ClockFace.CLASSIC

    private lateinit var seekbarAlpha: SeekBar
    private lateinit var alphaValText: TextView
    private val faceViews = mutableMapOf<ClockFace, ImageView>()

    private val bgPresets = listOf(
        0xFF000000.toInt(), 0xFF1A237E.toInt(), 0xFF1B5E20.toInt(),
        0xFF4A148C.toInt(), 0xFFB71C1C.toInt(), 0xFFFFFFFF.toInt(), 0x00000000
    )

    private val handPresets = listOf(
        0xFFFFFFFF.toInt(), 0xFF000000.toInt(), 0xFFFFEB3B.toInt(),
        0xFF00BCD4.toInt(), 0xFFFF9800.toInt(), 0xFFF48FB1.toInt()
    )

    private val faceLabels = mapOf(
        ClockFace.CLASSIC to "クラシック",
        ClockFace.SIMPLE  to "シンプル",
        ClockFace.MINIMAL to "ミニマル",
        ClockFace.DIGITAL to "デジタル"
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setResult(RESULT_CANCELED)

        widgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
        if (widgetId == AppWidgetManager.INVALID_APPWIDGET_ID) { finish(); return }

        setContentView(R.layout.activity_clock_configure)

        seekbarAlpha = findViewById(R.id.seekbar_alpha)
        alphaValText = findViewById(R.id.text_alpha_val)

        val existing = WidgetPrefs.loadClockConfig(this, widgetId)
        selectedBgColor = existing.backgroundColor
        selectedHandColor = existing.handColor
        selectedFace = existing.face
        seekbarAlpha.progress = Color.alpha(selectedBgColor)

        setupColorButtons(existing)
        setupAlphaSeekbar()
        setupFaceButtons()

        findViewById<Button>(R.id.btn_apply).setOnClickListener { apply() }
    }

    private fun setupFaceButtons() {
        val layout = findViewById<LinearLayout>(R.id.layout_face_styles)
        val density = resources.displayMetrics.density
        val thumbPx = (56 * density).toInt()
        val renderPx = thumbPx * 2
        val cal = Calendar.getInstance()

        ClockFace.entries.forEach { face ->
            val col = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER_HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }

            val img = ImageView(this).apply {
                layoutParams = LinearLayout.LayoutParams(thumbPx, thumbPx)
                scaleType = ImageView.ScaleType.FIT_CENTER
                val bm = ClockFaceRenderer.render(renderPx, ClockConfig(selectedBgColor, selectedHandColor, face), cal)
                setImageBitmap(bm)
            }
            faceViews[face] = img

            val label = TextView(this).apply {
                text = faceLabels[face]
                textSize = 10f
                setTextColor(0xFF444444.toInt())
                gravity = Gravity.CENTER_HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).also { it.topMargin = (3 * density).toInt() }
            }

            col.addView(img)
            col.addView(label)
            layout.addView(col)

            col.setOnClickListener {
                selectedFace = face
                refreshFaceSelection()
            }
        }

        refreshFaceSelection()
    }

    private fun refreshFaceSelection() {
        val density = resources.displayMetrics.density
        val thumbPx = (56 * density).toInt()
        val cal = Calendar.getInstance()

        ClockFace.entries.forEach { face ->
            val img = faceViews[face] ?: return@forEach
            val bm = ClockFaceRenderer.render(thumbPx * 2, ClockConfig(selectedBgColor, selectedHandColor, face), cal)
            img.setImageBitmap(bm)
            img.alpha = if (face == selectedFace) 1f else 0.45f
            img.background = if (face == selectedFace) {
                GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    cornerRadius = thumbPx * 0.12f
                    setColor(0x00000000)
                    setStroke((3 * density).toInt(), 0xFF2196F3.toInt())
                }
            } else null
        }
    }

    private fun setupColorButtons(existing: ClockConfig) {
        val bgLayout = findViewById<LinearLayout>(R.id.layout_bg_colors)
        bgPresets.forEach { color ->
            val btn = makeColorButton(color, isTransparent = (Color.alpha(color) == 0))
            val isSelected = Color.rgb(Color.red(color), Color.green(color), Color.blue(color)) ==
                Color.rgb(Color.red(existing.backgroundColor), Color.green(existing.backgroundColor), Color.blue(existing.backgroundColor))
            btn.alpha = if (isSelected) 1f else 0.6f
            btn.setOnClickListener {
                selectedBgColor = Color.argb(seekbarAlpha.progress, Color.red(color), Color.green(color), Color.blue(color))
                if (Color.alpha(color) == 0) seekbarAlpha.progress = 0
                bgLayout.children().forEach { it.alpha = 0.6f }
                btn.alpha = 1f
                refreshFaceSelection()
            }
            bgLayout.addView(btn)
        }

        val handLayout = findViewById<LinearLayout>(R.id.layout_hand_colors)
        handPresets.forEach { color ->
            val btn = makeColorButton(color)
            btn.alpha = if (color == existing.handColor) 1f else 0.6f
            btn.setOnClickListener {
                selectedHandColor = color
                handLayout.children().forEach { it.alpha = 0.6f }
                btn.alpha = 1f
                refreshFaceSelection()
            }
            handLayout.addView(btn)
        }
    }

    private fun makeColorButton(color: Int, isTransparent: Boolean = false): View {
        val density = resources.displayMetrics.density
        val size = (40 * density).toInt()
        val margin = (6 * density).toInt()
        val view = View(this).apply {
            layoutParams = LinearLayout.LayoutParams(size, size).also { it.marginEnd = margin }
        }
        view.background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            if (isTransparent) {
                setColor(0x33000000)
                setStroke((2 * density).toInt(), 0xFF999999.toInt())
            } else {
                setColor(color or 0xFF000000.toInt())
            }
        }
        return view
    }

    private fun setupAlphaSeekbar() {
        alphaValText.text = "${Math.round(seekbarAlpha.progress / 255f * 100)}%"
        seekbarAlpha.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar, progress: Int, fromUser: Boolean) {
                alphaValText.text = "${Math.round(progress / 255f * 100)}%"
                selectedBgColor = Color.argb(progress,
                    Color.red(selectedBgColor), Color.green(selectedBgColor), Color.blue(selectedBgColor))
                refreshFaceSelection()
            }
            override fun onStartTrackingTouch(sb: SeekBar) {}
            override fun onStopTrackingTouch(sb: SeekBar) {}
        })
    }

    private fun apply() {
        val config = ClockConfig(selectedBgColor, selectedHandColor, selectedFace)
        WidgetPrefs.saveClockConfig(this, widgetId, config)

        val awm = AppWidgetManager.getInstance(this)
        ClockWidget.updateWidget(this, awm, widgetId)
        WidgetUpdateService.start(this)

        setResult(RESULT_OK, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId))
        finish()
    }

    private fun LinearLayout.children(): List<View> = (0 until childCount).map { getChildAt(it) }
}
