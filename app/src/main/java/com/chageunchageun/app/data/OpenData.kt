package com.chageunchageun.app.data

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.time.LocalDateTime
import java.time.ZoneId
import kotlin.math.abs
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * 키가 필요 없는 공개 데이터 (pleos 플레이버에서 사용, 실패하면 null → 앱은 해당 안내를 생략한다).
 *  - 날씨: Open-Meteo Forecast API (https://open-meteo.com, 비상업 무료·키 없음)
 *  - 장소 이름 → 좌표: OpenStreetMap Nominatim (이용 정책: 초당 1회 이하, 앱을 알 수 있는 User-Agent, 결과 캐시)
 *  - 주변 주유소·정비소·충전소: OpenStreetMap Overpass API (연속 호출 금지, timeout 10초, 결과 캐시)
 * 국내 API(기상청·카카오 로컬·오피넷)는 키가 필요해 쓰지 않는다. 키를 받으면 이 파일의 각 객체만 바꾸면 된다.
 */
internal object Http {
    const val USER_AGENT = "chageunchageun-app/1.0 (Pleos Playground contest prototype)"
    private const val TIMEOUT_MS = 10_000
    private const val TAG = "ChageunNet"

    /** 같은 서버에 연달아 보내지 않도록 서버별 마지막 호출 시각을 기억한다. */
    private val lastCall = mutableMapOf<String, Long>()
    private val lock = Mutex()

    suspend fun get(url: String, minIntervalMs: Long = 1_100): String? = request(url, null, minIntervalMs)

    suspend fun post(url: String, form: String, minIntervalMs: Long = 1_100): String? = request(url, form, minIntervalMs)

    private suspend fun request(url: String, form: String?, minIntervalMs: Long): String? = lock.withLock {
        withContext(Dispatchers.IO) {
            val host = URL(url).host
            val wait = (lastCall[host] ?: 0L) + minIntervalMs - System.currentTimeMillis()
            if (wait > 0) Thread.sleep(wait)
            lastCall[host] = System.currentTimeMillis()
            runCatching {
                val c = URL(url).openConnection() as HttpURLConnection
                c.connectTimeout = TIMEOUT_MS
                c.readTimeout = TIMEOUT_MS
                c.setRequestProperty("User-Agent", USER_AGENT)
                c.setRequestProperty("Accept-Language", "ko")
                if (form != null) {
                    c.requestMethod = "POST"
                    c.doOutput = true
                    c.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
                    c.outputStream.use { it.write(form.toByteArray()) }
                }
                try {
                    if (c.responseCode !in 200..299) error("HTTP ${c.responseCode}")
                    c.inputStream.bufferedReader().use { it.readText() }
                } finally {
                    c.disconnect()
                }
            }.onFailure { Log.w(TAG, "$host 실패: ${it.message}") }.getOrNull()
        }
    }

    fun enc(s: String): String = URLEncoder.encode(s, "UTF-8")
}

/** 두 좌표 사이 직선거리(km). */
fun GeoPoint.distanceKm(o: GeoPoint): Double {
    val r = 6371.0
    val la1 = Math.toRadians(lat)
    val la2 = Math.toRadians(o.lat)
    val dLa = la2 - la1
    val dLo = Math.toRadians(o.lon - lon)
    val h = sin(dLa / 2).pow(2) + cos(la1) * cos(la2) * sin(dLo / 2).pow(2)
    return 2 * r * asin(sqrt(h))
}

/** 이 좌표에서 o 로 가는 방위각(도, 0=북). */
fun GeoPoint.bearingTo(o: GeoPoint): Double {
    val la1 = Math.toRadians(lat)
    val la2 = Math.toRadians(o.lat)
    val dLo = Math.toRadians(o.lon - lon)
    val y = sin(dLo) * cos(la2)
    val x = cos(la1) * sin(la2) - sin(la1) * cos(la2) * cos(dLo)
    return (Math.toDegrees(atan2(y, x)) + 360) % 360
}

/** 일정 설명 칸의 "geo:위도,경도" (RFC 5870 형식의 앞부분)를 읽는다. 없으면 null. */
fun parseGeo(text: String?): GeoPoint? {
    val m = Regex("""geo:\s*(-?\d+(?:\.\d+)?)\s*,\s*(-?\d+(?:\.\d+)?)""").find(text ?: return null) ?: return null
    val lat = m.groupValues[1].toDouble()
    val lon = m.groupValues[2].toDouble()
    if (lat !in -90.0..90.0 || lon !in -180.0..180.0) return null
    return GeoPoint(lat, lon)
}

// ---------------- 장소: Nominatim(이름→좌표), Overpass(주변 검색) ----------------

data class FoundPlace(val name: String, val point: GeoPoint, val distanceKm: Double)

object OsmPlaces {
    private const val TAG = "ChageunPlace"
    private val geoCache = mutableMapOf<String, GeoPoint?>()
    private val nearbyCache = mutableMapOf<String, Pair<Long, List<FoundPlace>>>()
    private const val NEARBY_CACHE_MS = 10 * 60_000L
    private val OVERPASS = listOf(
        "https://overpass-api.de/api/interpreter",
        "https://maps.mail.ru/osm/tools/overpass/api/interpreter",
    )
    private var preferred = 0

    /**
     * 장소 이름 → 좌표 (Nominatim, 한국 안). "광명역"처럼 '역'으로 끝나면 철도역 결과를 우선한다
     * (Nominatim 첫 결과가 같은 이름의 고속도로 나들목인 경우가 있어서).
     */
    suspend fun geocode(name: String): GeoPoint? {
        if (geoCache.containsKey(name)) return geoCache[name]
        val items = nominatim(name) ?: return null // 실패는 캐시하지 않는다
        val station = name.trim().endsWith("역")
        // OSM 은 기차역 이름을 "광명"처럼 '역' 없이 적는 경우가 많다(#003 B3: "광명역"은 같은 이름의 나들목만 나옴).
        // '…역'인데 철도 결과가 없으면 '역'을 뗀 이름으로 한 번 더 찾아 철도역만 고른다.
        val pick = (if (station) items.firstOrNull { it.optString("class") == "railway" } else null)
            ?: (if (station) nominatim(name.trim().removeSuffix("역"))?.firstOrNull { it.optString("class") == "railway" } else null)
            ?: items.firstOrNull()
        val point = pick?.let { GeoPoint(it.getString("lat").toDouble(), it.getString("lon").toDouble()) }
        Log.i(TAG, "geocode '$name' -> $point (${pick?.optString("class")}/${pick?.optString("type")})")
        geoCache[name] = point
        return point
    }

    private suspend fun nominatim(q: String): List<JSONObject>? {
        val url = "https://nominatim.openstreetmap.org/search?format=json&limit=5&countrycodes=kr&accept-language=ko&q=${Http.enc(q)}"
        val arr = Http.get(url)?.let { runCatching { JSONArray(it) }.getOrNull() } ?: return null
        return (0 until arr.length()).map { arr.getJSONObject(it) }
    }

    enum class Kind(val label: String, val overpass: List<String>) {
        FUEL("주유소", listOf("""nwr["amenity"="fuel"]""")),
        CHARGER("충전소", listOf("""nwr["amenity"="charging_station"]""")),
        REPAIR("정비소", listOf("""nwr["shop"="car_repair"]""", """nwr["amenity"="car_repair"]""")),
    }

    /** 검색어 → 찾을 종류. 공기압·타이어는 정비소(타이어 전문점 포함)로 찾는다. */
    fun kindOf(query: String): Kind = when {
        "충전" in query -> Kind.CHARGER
        "주유" in query -> Kind.FUEL
        else -> Kind.REPAIR
    }

    /**
     * 현재 위치 주변 3km → 없으면 10km. toward 가 있으면 목적지 방향(각도 차가 작은 곳)을 우선한다.
     * 점수 = 거리(km) × (1 + 각도 차/45°). 이름 없는 곳은 "주유소" 같은 종류 이름으로 표시.
     */
    suspend fun nearby(kind: Kind, from: GeoPoint, toward: GeoPoint?): List<FoundPlace> {
        for (radius in listOf(3_000, 10_000)) {
            val list = search(kind, from, radius)
            if (list.isEmpty()) continue
            if (toward == null || from.distanceKm(toward) < 0.5) return list.sortedBy { it.distanceKm }
            val dir = from.bearingTo(toward)
            return list.sortedBy { p ->
                val diff = abs(((from.bearingTo(p.point) - dir + 540) % 360) - 180)
                p.distanceKm * (1 + diff / 45.0)
            }
        }
        return emptyList()
    }

    private suspend fun search(kind: Kind, from: GeoPoint, radius: Int): List<FoundPlace> {
        val key = "${kind.name}:${"%.3f".format(from.lat)}:${"%.3f".format(from.lon)}:$radius"
        nearbyCache[key]?.let { (t, v) -> if (System.currentTimeMillis() - t < NEARBY_CACHE_MS) return v }
        val parts = kind.overpass.joinToString("") { "$it(around:$radius,${from.lat},${from.lon});" }
        val q = "[out:json][timeout:10];($parts);out center 40;"
        // 공개 Overpass 서버(OSM 위키 공개 인스턴스 목록). 기본 서버가 붐비면("at capacity") 다음 서버, 성공한 서버를 기억한다.
        var body: String? = null
        for (i in OVERPASS.indices) {
            val idx = (preferred + i) % OVERPASS.size
            body = Http.post(OVERPASS[idx], "data=${Http.enc(q)}", minIntervalMs = 2_000)
            if (body != null) {
                preferred = idx
                break
            }
        }
        if (body == null) return emptyList()
        val els = runCatching { JSONObject(body).getJSONArray("elements") }.getOrNull() ?: return emptyList()
        val out = (0 until els.length()).mapNotNull { i ->
            val e = els.getJSONObject(i)
            val c = e.optJSONObject("center")
            val lat = if (e.has("lat")) e.getDouble("lat") else c?.optDouble("lat") ?: return@mapNotNull null
            val lon = if (e.has("lon")) e.getDouble("lon") else c?.optDouble("lon") ?: return@mapNotNull null
            val tags = e.optJSONObject("tags")
            val name = tags?.optString("name:ko")?.takeIf { it.isNotBlank() }
                ?: tags?.optString("name")?.takeIf { it.isNotBlank() }
                ?: tags?.optString("brand")?.takeIf { it.isNotBlank() }?.let { "$it ${kind.label}" }
                ?: kind.label
            val p = GeoPoint(lat, lon)
            FoundPlace(name, p, from.distanceKm(p))
        }
        Log.i(TAG, "overpass ${kind.name} r=$radius -> ${out.size}곳")
        nearbyCache[key] = System.currentTimeMillis() to out
        return out
    }
}

// ---------------- 날씨: Open-Meteo ----------------

data class Weather(val currentC: Float?, val tonightMinC: Float?)

interface WeatherSource {
    /** 실패하면 null. */
    suspend fun weather(at: GeoPoint): Weather?
}

/** mock 플레이버: 시나리오 값을 쓰므로 날씨를 가져오지 않는다. */
object NoWeather : WeatherSource {
    override suspend fun weather(at: GeoPoint): Weather? = null
}

/**
 * Open-Meteo 예보. 현재 기온 + "오늘 밤 최저기온"(지금부터 다음 날 오전 9시까지 시간별 기온의 최솟값).
 * 위치를 약 5km 격자로 묶어 30분 캐시.
 */
class OpenMeteoWeather : WeatherSource {
    private var cache: Triple<String, Long, Weather>? = null

    override suspend fun weather(at: GeoPoint): Weather? {
        val key = "%.2f,%.2f".format(Math.round(at.lat * 20) / 20.0, Math.round(at.lon * 20) / 20.0)
        cache?.let { (k, t, w) -> if (k == key && System.currentTimeMillis() - t < CACHE_MS) return w }
        val url = "https://api.open-meteo.com/v1/forecast?latitude=${at.lat}&longitude=${at.lon}" +
            "&current=temperature_2m&hourly=temperature_2m&forecast_days=2&timezone=auto"
        val body = Http.get(url, minIntervalMs = 0) ?: return null
        val w = runCatching { parse(JSONObject(body)) }
            .onFailure { Log.w(TAG, "parse 실패", it) }.getOrNull() ?: return null
        Log.i(TAG, "weather $key -> $w")
        cache = Triple(key, System.currentTimeMillis(), w)
        return w
    }

    private fun parse(j: JSONObject): Weather {
        val zone = runCatching { ZoneId.of(j.getString("timezone")) }.getOrDefault(ZoneId.systemDefault())
        val now = LocalDateTime.now(zone)
        val until = now.toLocalDate().plusDays(1).atTime(9, 0)
        val hourly = j.getJSONObject("hourly")
        val times = hourly.getJSONArray("time")
        val temps = hourly.getJSONArray("temperature_2m")
        var min: Double? = null
        for (i in 0 until times.length()) {
            val t = LocalDateTime.parse(times.getString(i))
            if (t.isBefore(now.withMinute(0).withSecond(0).withNano(0)) || t.isAfter(until) || temps.isNull(i)) continue
            val v = temps.getDouble(i)
            if (min == null || v < min) min = v
        }
        val current = j.optJSONObject("current")?.takeIf { !it.isNull("temperature_2m") }?.getDouble("temperature_2m")
        return Weather(current?.toFloat(), min?.toFloat())
    }

    private companion object {
        const val TAG = "ChageunWeather"
        const val CACHE_MS = 30 * 60_000L
    }
}
