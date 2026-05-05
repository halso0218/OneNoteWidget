package com.example.datetimewidget

import android.Manifest
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.view.View
import android.widget.*
import android.app.Activity
import kotlinx.coroutines.*
import kotlin.math.pow

class WidgetConfigureActivity : Activity() {

    private var widgetId = AppWidgetManager.INVALID_APPWIDGET_ID
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private var selectedBgColor = 0xAA000000.toInt()
    private var selectedTextColor = 0xFFFFFFFF.toInt()
    private var prefMap: Map<String, List<Pair<String, String>>> = emptyMap()

    // 現在地から選ぶ時に次のupdateStationSpinnerで使うIDを一時保持
    private var autoSelectId: String? = null

    private val bgPresets = listOf(
        0xFF000000.toInt(),
        0xFF1A237E.toInt(),
        0xFF1B5E20.toInt(),
        0xFF4A148C.toInt(),
        0xFFB71C1C.toInt(),
        0xFFFFFFFF.toInt(),
        0x00000000
    )

    private val textPresets = listOf(
        0xFFFFFFFF.toInt(),
        0xFF000000.toInt(),
        0xFFFFEB3B.toInt(),
        0xFF00BCD4.toInt(),
        0xFFFF9800.toInt(),
        0xFFF48FB1.toInt()
    )

    private lateinit var spinnerPref: Spinner
    private lateinit var spinnerStation: Spinner
    private lateinit var stationInfoText: TextView
    private lateinit var seekbarAlpha: SeekBar
    private lateinit var alphaValText: TextView
    private lateinit var switchShadow: Switch
    private lateinit var previewWidget: View
    private lateinit var previewTemp: TextView
    private lateinit var previewStation: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setResult(RESULT_CANCELED)

        widgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
        if (widgetId == AppWidgetManager.INVALID_APPWIDGET_ID) { finish(); return }

        setContentView(R.layout.activity_widget_configure)

        spinnerPref = findViewById(R.id.spinner_pref)
        spinnerStation = findViewById(R.id.spinner_station)
        stationInfoText = findViewById(R.id.text_station_info)
        seekbarAlpha = findViewById(R.id.seekbar_alpha)
        alphaValText = findViewById(R.id.text_alpha_val)
        switchShadow = findViewById(R.id.switch_shadow)
        previewWidget = findViewById(R.id.preview_widget)
        previewStation = findViewById(R.id.preview_station)
        previewTemp = findViewById(R.id.preview_temp)

        val existing = WidgetPrefs.load(this, widgetId)
        selectedBgColor = existing.backgroundColor
        selectedTextColor = existing.textColor
        switchShadow.isChecked = existing.textShadow
        seekbarAlpha.progress = Color.alpha(selectedBgColor)

        setupColorButtons(existing)
        setupAlphaSeekbar()
        switchShadow.setOnCheckedChangeListener { _, _ -> updatePreview() }

        // データ取得中はローディング画面を表示
        findViewById<View>(R.id.loading_overlay).visibility = View.VISIBLE
        findViewById<View>(R.id.scroll_content).visibility = View.GONE
        loadAmedasData(existing)

        findViewById<Button>(R.id.btn_nearest).setOnClickListener { requestNearestStation() }
        findViewById<Button>(R.id.btn_apply).setOnClickListener { apply() }
        updatePreview()
    }

    // ─── 現在地から最寄り観測点を選ぶ ────────────────────────────────

    private fun requestNearestStation() {
        if (checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            fetchLocationAndSelect()
        } else {
            requestPermissions(
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION),
                REQ_LOCATION
            )
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQ_LOCATION && grantResults.any { it == PackageManager.PERMISSION_GRANTED }) {
            fetchLocationAndSelect()
        } else {
            Toast.makeText(this, "位置情報の許可が必要です", Toast.LENGTH_SHORT).show()
        }
    }

    private fun fetchLocationAndSelect() {
        val lm = getSystemService(LocationManager::class.java)
        // キャッシュされた最終位置を優先（高速）
        val providers = listOf(
            LocationManager.GPS_PROVIDER,
            LocationManager.NETWORK_PROVIDER,
            LocationManager.PASSIVE_PROVIDER
        )
        for (provider in providers) {
            if (!lm.isProviderEnabled(provider)) continue
            val loc = try { lm.getLastKnownLocation(provider) } catch (_: SecurityException) { null }
            if (loc != null) { selectNearestStation(loc.latitude, loc.longitude); return }
        }
        // 最終位置がない場合は新規取得
        Toast.makeText(this, "位置情報を取得中…", Toast.LENGTH_SHORT).show()
        val provider = providers.firstOrNull { lm.isProviderEnabled(it) }
            ?: run { Toast.makeText(this, "位置情報が利用できません", Toast.LENGTH_SHORT).show(); return }
        try {
            lm.requestLocationUpdates(provider, 0L, 0f, object : LocationListener {
                override fun onLocationChanged(loc: Location) {
                    lm.removeUpdates(this)
                    selectNearestStation(loc.latitude, loc.longitude)
                }
                override fun onProviderDisabled(p: String) {
                    lm.removeUpdates(this)
                    Toast.makeText(this@WidgetConfigureActivity, "位置情報が無効になりました", Toast.LENGTH_SHORT).show()
                }
            }, mainLooper)
        } catch (_: SecurityException) {
            Toast.makeText(this, "位置情報の許可が必要です", Toast.LENGTH_SHORT).show()
        }
    }

    private fun selectNearestStation(lat: Double, lon: Double) {
        val t = AmedasCache.table
        if (t == null) { Toast.makeText(this, "観測点データ未読み込み", Toast.LENGTH_SHORT).show(); return }

        var nearestId: String? = null
        var minDist = Double.MAX_VALUE
        val keys = t.keys()
        while (keys.hasNext()) {
            val id = keys.next()
            val s = t.optJSONObject(id) ?: continue
            val latArr = s.optJSONArray("lat") ?: continue
            val lonArr = s.optJSONArray("lon") ?: continue
            val sLat = latArr.getDouble(0) + latArr.getDouble(1) / 60.0
            val sLon = lonArr.getDouble(0) + lonArr.getDouble(1) / 60.0
            val d = (sLat - lat).pow(2) + (sLon - lon).pow(2)
            if (d < minDist) { minDist = d; nearestId = id }
        }

        val id = nearestId ?: run {
            Toast.makeText(this, "近くの観測点が見つかりません", Toast.LENGTH_SHORT).show(); return
        }
        val pref = JmaWeatherClient.PREFIX_TO_PREF[id.take(2)] ?: return
        val prefs = prefMap.keys.toList()
        val prefIdx = prefs.indexOf(pref).takeIf { it >= 0 } ?: return

        val stationName = t.optJSONObject(id)?.optString("kjName") ?: id
        Toast.makeText(this, "最寄り観測点: $stationName ($pref)", Toast.LENGTH_SHORT).show()

        autoSelectId = id
        if (spinnerPref.selectedItemPosition == prefIdx) {
            // 既に同じ都道府県なので listener が発火しない → 直接呼ぶ
            updateStationSpinner(pref)
        } else {
            spinnerPref.setSelection(prefIdx)
            // listener が autoSelectId を拾って updateStationSpinner を呼ぶ
        }
    }

    // ─── AMeDAS スピナー ───────────────────────────────────────────────

    private fun loadAmedasData(existing: WidgetConfig) {
        spinnerPref.isEnabled = false
        spinnerStation.isEnabled = false

        scope.launch {
            // JSON解析とprefMap構築をまとめてIOスレッドで実行（メインスレッドをブロックしない）
            val (loaded, map) = withContext(Dispatchers.IO) {
                if (!AmedasCache.isLoaded()) AmedasCache.load(this@WidgetConfigureActivity)
                Pair(AmedasCache.isLoaded(), AmedasCache.getPrefectureMap())
            }

            if (!loaded || map.isEmpty()) {
                Toast.makeText(this@WidgetConfigureActivity, R.string.load_error, Toast.LENGTH_LONG).show()
                return@launch
            }

            prefMap = map
            val prefs = prefMap.keys.toList()
            if (prefs.isEmpty()) {
                Toast.makeText(this@WidgetConfigureActivity, R.string.load_error, Toast.LENGTH_SHORT).show()
                return@launch
            }

            // データ取得完了 → ローディング非表示、コンテンツ表示
            findViewById<View>(R.id.loading_overlay).visibility = View.GONE
            findViewById<View>(R.id.scroll_content).visibility = View.VISIBLE

            val prefAdapter = ArrayAdapter(this@WidgetConfigureActivity, android.R.layout.simple_spinner_item, prefs)
            prefAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            spinnerPref.adapter = prefAdapter
            spinnerPref.isEnabled = true

            val existingPref = AmedasCache.table?.optJSONObject(existing.stationId)?.optString("kjState")
                ?: JmaWeatherClient.PREFIX_TO_PREF[existing.stationId.take(2)]
            val prefIdx = prefs.indexOf(existingPref).takeIf { it >= 0 } ?: 0
            spinnerPref.setSelection(prefIdx)

            spinnerPref.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(parent: AdapterView<*>, view: View?, pos: Int, id: Long) {
                    updateStationSpinner(prefs[pos])
                }
                override fun onNothingSelected(parent: AdapterView<*>) {}
            }
            updateStationSpinner(prefs[prefIdx], existing.stationId)
        }
    }

    private fun updateStationSpinner(pref: String, fallbackId: String? = null) {
        // autoSelectId（現在地選択）を優先し、なければ fallbackId（既存設定）
        val selectId = autoSelectId ?: fallbackId
        autoSelectId = null

        val stations = prefMap[pref] ?: return
        val names = stations.map { it.second }
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, names)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerStation.adapter = adapter
        spinnerStation.isEnabled = true

        val idx = stations.indexOfFirst { it.first == selectId }.takeIf { it >= 0 } ?: 0
        spinnerStation.setSelection(idx)

        spinnerStation.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, view: View?, pos: Int, id: Long) {
                val sid = stations[pos].first
                val s = AmedasCache.table?.optJSONObject(sid)
                stationInfoText.text = if (s != null) "ID: $sid　標高: ${s.optInt("elev", 0)}m" else ""
            }
            override fun onNothingSelected(parent: AdapterView<*>) {}
        }
    }

    // ─── カラー / プレビュー ───────────────────────────────────────────

    private fun setupColorButtons(existing: WidgetConfig) {
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
                updatePreview()
            }
            bgLayout.addView(btn)
        }

        val textLayout = findViewById<LinearLayout>(R.id.layout_text_colors)
        textPresets.forEach { color ->
            val btn = makeColorButton(color)
            btn.alpha = if (color == existing.textColor) 1f else 0.6f
            btn.setOnClickListener {
                selectedTextColor = color
                textLayout.children().forEach { it.alpha = 0.6f }
                btn.alpha = 1f
                updatePreview()
            }
            textLayout.addView(btn)
        }
    }

    private fun makeColorButton(color: Int, isTransparent: Boolean = false): View {
        val size = (40 * resources.displayMetrics.density).toInt()
        val margin = (6 * resources.displayMetrics.density).toInt()
        val view = View(this).apply {
            layoutParams = LinearLayout.LayoutParams(size, size).also { it.marginEnd = margin }
        }
        val drawable = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            if (isTransparent) {
                setColor(0x33000000)
                setStroke((2 * resources.displayMetrics.density).toInt(), 0xFF999999.toInt())
            } else {
                setColor(color or 0xFF000000.toInt())
            }
        }
        view.background = drawable
        return view
    }

    private fun setupAlphaSeekbar() {
        alphaValText.text = "${Math.round(seekbarAlpha.progress / 255f * 100)}%"
        seekbarAlpha.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar, progress: Int, fromUser: Boolean) {
                alphaValText.text = "${Math.round(progress / 255f * 100)}%"
                selectedBgColor = Color.argb(progress,
                    Color.red(selectedBgColor), Color.green(selectedBgColor), Color.blue(selectedBgColor))
                updatePreview()
            }
            override fun onStartTrackingTouch(sb: SeekBar) {}
            override fun onStopTrackingTouch(sb: SeekBar) {}
        })
    }

    private fun updatePreview() {
        previewWidget.setBackgroundColor(selectedBgColor)
        previewTemp.setTextColor(selectedTextColor)
        val subColor = Color.argb(
            (Color.alpha(selectedTextColor) * 0.7).toInt(),
            Color.red(selectedTextColor), Color.green(selectedTextColor), Color.blue(selectedTextColor)
        )
        previewStation.setTextColor(subColor)
        val sl = if (switchShadow.isChecked) 3f else 0f
        previewTemp.setShadowLayer(sl, 1f, 1f, 0x80000000.toInt())
        previewStation.setShadowLayer(sl, 1f, 1f, 0x80000000.toInt())
    }

    // ─── 適用 ─────────────────────────────────────────────────────────

    private fun apply() {
        val pref = spinnerPref.selectedItem as? String ?: return
        val stations = prefMap[pref] ?: return
        val stationIdx = spinnerStation.selectedItemPosition
        if (stationIdx < 0 || stationIdx >= stations.size) return
        val (stationId, stationName) = stations[stationIdx]

        val config = WidgetConfig(
            stationId = stationId,
            stationName = stationName,
            backgroundColor = selectedBgColor,
            textColor = selectedTextColor,
            textShadow = switchShadow.isChecked
        )
        WidgetPrefs.save(this, widgetId, config)

        getSharedPreferences("widget_weather", MODE_PRIVATE)
            .edit()
            .putLong("last_fetch", 0)
            .putString("icon_$stationId", "🌡")
            .putString("temp_$stationId", "--°C")
            .apply()

        val awm = AppWidgetManager.getInstance(this)
        DateTimeWidget.updateWidget(this, awm, widgetId)
        WidgetUpdateService.start(this)

        // WorkManagerで即時天気取得（finish()後もプロセスフリーズを気にせず実行される）
        WidgetAlarmReceiver.enqueueWeatherFetch(this)

        setResult(RESULT_OK, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId))
        finish()
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun LinearLayout.children(): List<View> = (0 until childCount).map { getChildAt(it) }

    companion object {
        private const val REQ_LOCATION = 100
    }
}
