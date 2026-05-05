package com.example.datetimewidget

import android.content.Context
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

data class WeatherData(
    val tempCelsius: Double?,
    val weatherCode: String?,
    val observationTime: String? = null
)

object JmaWeatherClient {

    private const val LATEST_TIME_URL = "https://www.jma.go.jp/bosai/amedas/data/latest_time.txt"
    private const val AMEDAS_MAP_URL = "https://www.jma.go.jp/bosai/amedas/data/map/%s.json"
    private const val FORECAST_URL = "https://www.jma.go.jp/bosai/forecast/data/forecast/%s.json"
    private const val AMEDAS_TABLE_URL = "https://www.jma.go.jp/bosai/amedas/const/amedastable.json"

    fun fetchWeather(stationId: String): WeatherData {
        val (temp, obsTime) = runCatching { fetchTempWithObsTime(stationId) }.onFailure {
            android.util.Log.e("JmaWeather", "fetchTemp failed for $stationId", it)
        }.getOrNull() ?: Pair(null, null)
        val code = runCatching { fetchWeatherCode(stationId) }.onFailure {
            android.util.Log.e("JmaWeather", "fetchWeatherCode failed for $stationId", it)
        }.getOrNull()
        return WeatherData(temp, code, obsTime)
    }

    private fun fetchTempWithObsTime(stationId: String): Pair<Double?, String?> {
        val raw = get(LATEST_TIME_URL).trim()
        val obsTime = raw.substringAfter("T").take(5).takeIf { it.length == 5 }
        val timestamp = raw.replace(":", "").replace("-", "").replace("T", "")
            .substring(0, 12) + "00"
        android.util.Log.d("JmaWeather", "latest_time raw=$raw → timestamp=$timestamp obsTime=$obsTime")
        val mapJson = JSONObject(get(AMEDAS_MAP_URL.format(timestamp)))
        val station = mapJson.optJSONObject(stationId)
        if (station == null) {
            android.util.Log.w("JmaWeather", "stationId $stationId not found in map")
            return Pair(null, obsTime)
        }
        val tempArr = station.optJSONArray("temp")
        if (tempArr == null) {
            android.util.Log.w("JmaWeather", "stationId $stationId has no temp field (keys=${station.keys().asSequence().take(5).toList()})")
            return Pair(null, obsTime)
        }
        val tempVal = if (tempArr.length() > 0) tempArr.getDouble(0) else null
        return Pair(tempVal, obsTime)
    }

    private fun fetchWeatherCode(stationId: String): String? {
        val areaCode = getAreaCode(stationId) ?: return null
        val forecastJson = get(FORECAST_URL.format(areaCode))
        val arr = org.json.JSONArray(forecastJson)
        // timeSeries/areasをすべて走査してweatherCodesが存在するエントリを探す
        val timeSeries = arr.getJSONObject(0).getJSONArray("timeSeries")
        for (i in 0 until timeSeries.length()) {
            val areas = timeSeries.getJSONObject(i).getJSONArray("areas")
            for (j in 0 until areas.length()) {
                val codes = areas.getJSONObject(j).optJSONArray("weatherCodes") ?: continue
                if (codes.length() > 0) return codes.getString(0)
            }
        }
        return null
    }

    private fun getAreaCode(stationId: String): String? {
        val prefix = stationId.take(2)
        return PREFECTURE_AREA_CODE[PREFIX_TO_PREF[prefix]]
    }

    // 都道府県を北から南の地理順で定義
    val PREFECTURE_ORDER = listOf(
        "北海道", "青森県", "岩手県", "宮城県", "秋田県", "山形県", "福島県",
        "茨城県", "栃木県", "群馬県", "埼玉県", "千葉県", "東京都", "神奈川県",
        "新潟県", "富山県", "石川県", "福井県", "山梨県", "長野県",
        "岐阜県", "静岡県", "愛知県", "三重県",
        "滋賀県", "京都府", "大阪府", "兵庫県", "奈良県", "和歌山県",
        "鳥取県", "島根県", "岡山県", "広島県", "山口県",
        "徳島県", "香川県", "愛媛県", "高知県",
        "福岡県", "佐賀県", "長崎県", "熊本県", "大分県", "宮崎県", "鹿児島県",
        "沖縄県"
    )

    val PREFIX_TO_PREF = mapOf(
        "11" to "北海道", "12" to "北海道", "13" to "北海道", "14" to "北海道",
        "15" to "北海道", "16" to "北海道", "17" to "北海道", "18" to "北海道",
        "19" to "北海道", "20" to "北海道", "21" to "北海道", "22" to "北海道",
        "23" to "北海道", "24" to "北海道",
        "31" to "青森県",
        "32" to "秋田県",
        "33" to "岩手県",
        "34" to "宮城県",
        "35" to "山形県",
        "36" to "福島県",
        "40" to "茨城県", "41" to "栃木県", "42" to "群馬県", "43" to "埼玉県",
        "44" to "東京都", "45" to "千葉県", "46" to "神奈川県", "47" to "東京都",
        "48" to "長野県", "49" to "山梨県",
        "50" to "静岡県", "51" to "愛知県", "52" to "岐阜県", "53" to "三重県",
        "54" to "新潟県", "55" to "富山県", "56" to "石川県", "57" to "福井県",
        "60" to "滋賀県", "61" to "京都府", "62" to "大阪府", "63" to "兵庫県",
        "64" to "奈良県", "65" to "和歌山県",
        "66" to "岡山県", "67" to "広島県", "68" to "島根県", "69" to "鳥取県",
        "71" to "徳島県", "72" to "香川県", "73" to "愛媛県", "74" to "高知県",
        "81" to "山口県",
        "82" to "福岡県", "83" to "大分県", "84" to "長崎県", "85" to "佐賀県",
        "86" to "熊本県", "87" to "宮崎県", "88" to "鹿児島県",
        "91" to "沖縄県", "92" to "沖縄県", "93" to "沖縄県", "94" to "沖縄県"
    )

    val PREFECTURE_AREA_CODE = mapOf(
        "北海道" to "016000", "青森県" to "020000", "岩手県" to "030000",
        "宮城県" to "040000", "秋田県" to "050000", "山形県" to "060000",
        "福島県" to "070000", "茨城県" to "080000", "栃木県" to "090000",
        "群馬県" to "100000", "埼玉県" to "110000", "千葉県" to "120000",
        "東京都" to "130000", "神奈川県" to "140000", "新潟県" to "150000",
        "富山県" to "160000", "石川県" to "170000", "福井県" to "180000",
        "山梨県" to "190000", "長野県" to "200000", "岐阜県" to "210000",
        "静岡県" to "220000", "愛知県" to "230000", "三重県" to "240000",
        "滋賀県" to "250000", "京都府" to "260000", "大阪府" to "270000",
        "兵庫県" to "280000", "奈良県" to "290000", "和歌山県" to "300000",
        "鳥取県" to "310000", "島根県" to "320000", "岡山県" to "330000",
        "広島県" to "340000", "山口県" to "350000", "徳島県" to "360000",
        "香川県" to "370000", "愛媛県" to "380000", "高知県" to "390000",
        "福岡県" to "400000", "佐賀県" to "410000", "長崎県" to "420000",
        "熊本県" to "430000", "大分県" to "440000", "宮崎県" to "450000",
        "鹿児島県" to "460100", "沖縄県" to "471000"
    )

    fun weatherCodeToEmoji(code: String?): String {
        if (code == null) return "🌡"
        val n = code.toIntOrNull() ?: return "🌡"
        return when {
            n in 100..199 -> "☀️"
            n in 200..299 -> "☁️"
            n in 300..399 -> "🌧️"
            n in 400..499 -> "❄️"
            else -> "🌡"
        }
    }

    fun fetchAmedasTable(): JSONObject {
        return JSONObject(get(AMEDAS_TABLE_URL))
    }

    private fun get(urlStr: String): String {
        val conn = URL(urlStr).openConnection() as HttpURLConnection
        conn.connectTimeout = 10_000
        conn.readTimeout = 10_000
        return try {
            conn.inputStream.bufferedReader().readText()
        } finally {
            conn.disconnect()
        }
    }
}

object AmedasCache {
    var table: JSONObject? = null

    fun load(context: Context) {
        val file = java.io.File(context.filesDir, "amedas_table.json")
        if (file.exists()) {
            table = JSONObject(file.readText())
        } else {
            // ネットワーク不要：同梱 assets をフォールバックとして使用
            try {
                val json = context.assets.open("amedastable.json").bufferedReader().readText()
                table = JSONObject(json)
                file.writeText(json)  // 次回はディスクキャッシュから読む
            } catch (e: Exception) {
                android.util.Log.e("AmedasCache", "assets load failed", e)
            }
        }
    }

    fun save(context: Context, json: JSONObject) {
        table = json
        java.io.File(context.filesDir, "amedas_table.json").writeText(json.toString())
    }

    fun isLoaded() = table != null

    fun getPrefectureMap(): Map<String, List<Pair<String, String>>> {
        val t = table ?: return emptyMap()
        val map = mutableMapOf<String, MutableList<Pair<String, String>>>()
        val keys = t.keys()
        while (keys.hasNext()) {
            val id = keys.next()
            val s = t.optJSONObject(id) ?: continue
            val name = s.optString("kjName").takeIf { it.isNotEmpty() } ?: continue
            val pref = JmaWeatherClient.PREFIX_TO_PREF[id.take(2)] ?: continue
            map.getOrPut(pref) { mutableListOf() }.add(id to name)
        }
        // knName（カタカナ読み）でソート — カタカナはUnicode上で連続しており自然順が五十音順と一致する
        map.forEach { (_, list) ->
            list.sortWith { a, b ->
                val kanaA = t.optJSONObject(a.first)?.optString("knName") ?: a.second
                val kanaB = t.optJSONObject(b.first)?.optString("knName") ?: b.second
                kanaA.compareTo(kanaB)
            }
        }
        // 北から南の地理順で並べる（LinkedHashMapで挿入順を維持）
        return JmaWeatherClient.PREFECTURE_ORDER
            .filter { it in map }
            .associateWith { map[it]!! }
    }
}
