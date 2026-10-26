package dev.hram.androsaur

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.Build
import android.util.Log
import dev.hram.androsaur.weather.HourPoint
import dev.hram.androsaur.weather.Place
import dev.hram.androsaur.weather.WeatherReading
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.time.Instant
import java.util.Locale

/**
 * The weather's ends on this phone: where the phone is (coarse location, if the owner allowed it), the weather the hub handed
 * (and its place), the weather this phone fetched itself from MET Norway, all remembered in the preferences so the screen has
 * something to show after a restart and without the network. Which one to show and when to fetch is [dev.hram.androsaur.weather.WeatherChoice].
 */
class WeatherSource(private val context: Context, private val prefs: SharedPreferences) {

    /** The place set in the hub, remembered; null when the hub set none (or never answered). */
    val hubPlace: Place? get() = prefs.getString(KEY_HUB_PLACE, null)?.let { placeOf(it) }
    val hubWeather: WeatherReading? get() = prefs.getString(KEY_HUB_WEATHER, null)?.let { readingOf(it) }
    val ownWeather: WeatherReading? get() = prefs.getString(KEY_OWN_WEATHER, null)?.let { readingOf(it) }
    val ownPlace: Place? get() = prefs.getString(KEY_OWN_PLACE, null)?.let { placeOf(it) }
    /** Where the phone was last time it knew, or null (no permission yet, or no fix). */
    val devicePlace: Place? get() = prefs.getString(KEY_DEVICE_PLACE, null)?.let { placeOf(it) }

    /** What the hub said in its reply: its place (null — none set, the phones use their own) and its weather. */
    fun fromHub(place: JSONObject?, weather: JSONObject?) {
        prefs.edit().apply {
            if (place == null) remove(KEY_HUB_PLACE) else putString(KEY_HUB_PLACE, place.toString())
            // Weather for a place the hub no longer has is not the hub's to give.
            if (place == null) remove(KEY_HUB_WEATHER) else weather?.let { putString(KEY_HUB_WEATHER, it.toString()) }
        }.apply()
    }

    fun hasLocationPermission(): Boolean =
        context.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

    /**
     * Learns where the phone is when it does not know or the fix is old (the phone on a stand does not move, so rarely): the
     * freshest last known fix of any provider, or a new one asked from the first enabled provider. Coarse only.
     */
    @SuppressLint("MissingPermission")
    fun refreshLocation(nowMs: Long) {
        if (!hasLocationPermission()) return
        if (devicePlace != null && nowMs - prefs.getLong(KEY_DEVICE_AT, 0) < LOCATION_EVERY_MS) return
        val lm = context.getSystemService(LocationManager::class.java) ?: return
        val providers = lm.getProviders(true)
        val last = providers.mapNotNull { runCatching { lm.getLastKnownLocation(it) }.getOrNull() }.maxByOrNull { it.time }
        if (last != null && nowMs - last.time < LOCATION_EVERY_MS) { remember(last, nowMs); return }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val provider = listOf("fused", LocationManager.NETWORK_PROVIDER, LocationManager.GPS_PROVIDER).firstOrNull { it in providers } ?: return
            lm.getCurrentLocation(provider, null, context.mainExecutor) { loc -> if (loc != null) remember(loc, System.currentTimeMillis()) }
        } else if (last != null) remember(last, nowMs)
    }

    private fun remember(loc: Location, nowMs: Long) {
        // Two decimals (about a kilometre) is plenty for the weather and keeps the exact spot out of the preferences.
        val place = Place("", Math.round(loc.latitude * 100) / 100.0, Math.round(loc.longitude * 100) / 100.0)
        prefs.edit().putString(KEY_DEVICE_PLACE, placeJson(place).toString()).putLong(KEY_DEVICE_AT, nowMs).apply()
    }

    /** Asks MET Norway for the weather at [place] and keeps it as this phone's own. Null when it did not answer. */
    fun fetch(place: Place, nowMs: Long): WeatherReading? {
        var connection: HttpURLConnection? = null
        return try {
            val url = String.format(Locale.US, "%s?lat=%.4f&lon=%.4f", FORECAST_URL, place.latitude, place.longitude)
            connection = (URL(url).openConnection() as HttpURLConnection).apply {
                connectTimeout = 8000; readTimeout = 10_000
                setRequestProperty("User-Agent", USER_AGENT)
            }
            if (connection.responseCode != 200) { Log.w(TAG, "weather: ${connection.responseCode}"); return null }
            val reading = parseMet(place.name, JSONObject(connection.inputStream.use { it.readBytes().toString(Charsets.UTF_8) }), nowMs)
            prefs.edit().putString(KEY_OWN_WEATHER, readingJson(reading).toString()).putString(KEY_OWN_PLACE, placeJson(place).toString()).apply()
            reading
        } catch (e: Exception) { Log.w(TAG, "weather failed: ${e.javaClass.simpleName}"); null }
        finally { connection?.disconnect() }
    }

    companion object {
        private const val TAG = "Androsaur"
        private const val FORECAST_URL = "https://api.met.no/weatherapi/locationforecast/2.0/compact"
        /** MET Norway asks every client to say who it is. */
        private const val USER_AGENT = "androsaur/0.1 (+https://github.com/androsaurs/androsaur)"
        private const val LOCATION_EVERY_MS = 6 * 3_600_000L
        private const val KEY_HUB_PLACE = "weather_hub_place"; private const val KEY_HUB_WEATHER = "weather_hub"
        private const val KEY_OWN_WEATHER = "weather_own"; private const val KEY_OWN_PLACE = "weather_own_place"
        private const val KEY_DEVICE_PLACE = "weather_device_place"; private const val KEY_DEVICE_AT = "weather_device_at"

        /** Weather from MET's answer: temperature, wind and the symbol of the next hour; the lowest and highest of 12 hours. */
        fun parseMet(place: String, data: JSONObject, nowMs: Long): WeatherReading {
            val series = data.getJSONObject("properties").getJSONArray("timeseries")
            val first = series.getJSONObject(0)
            val now = first.getJSONObject("data")
            val details = now.getJSONObject("instant").getJSONObject("details")
            val next = now.optJSONObject("next_1_hours") ?: now.optJSONObject("next_6_hours")
            val symbol = next?.optJSONObject("summary")?.optString("symbol_code").orEmpty()
            // The nearest hour with rain, sleet or snow in the next 12, when it is dry now.
            var later: Pair<String, Long>? = null
            if (!isPrecipitation(symbol)) for (i in 1 until minOf(12, series.length())) {
                val point = series.getJSONObject(i)
                val code = point.getJSONObject("data").optJSONObject("next_1_hours")?.optJSONObject("summary")?.optString("symbol_code").orEmpty()
                if (isPrecipitation(code)) { later = code to Instant.parse(point.getString("time")).toEpochMilli(); break }
            }
            // Tomorrow in local time: lowest and highest temperature, and the symbol of the point nearest to noon.
            val zone = java.time.ZoneId.systemDefault()
            val tomorrow = Instant.ofEpochMilli(nowMs).atZone(zone).toLocalDate().plusDays(1)
            val tomorrowTemps = mutableListOf<Double>()
            var noon: Pair<Double, String>? = null
            for (i in 0 until series.length()) {
                val point = series.getJSONObject(i)
                val at = Instant.parse(point.getString("time")).atZone(zone)
                if (at.toLocalDate() != tomorrow) continue
                val d = point.getJSONObject("data")
                d.getJSONObject("instant").getJSONObject("details").let { if (it.has("air_temperature")) tomorrowTemps += it.getDouble("air_temperature") }
                val code = (d.optJSONObject("next_6_hours") ?: d.optJSONObject("next_1_hours"))?.optJSONObject("summary")?.optString("symbol_code").orEmpty()
                val distance = kotlin.math.abs(at.hour + at.minute / 60.0 - 12)
                if (code.isNotEmpty() && (noon == null || distance < noon.first)) noon = distance to code
            }
            // The forecast by the hour for the next day, for the strip over the face.
            val hours = mutableListOf<HourPoint>()
            for (i in 0 until series.length()) {
                val point = series.getJSONObject(i)
                val at = Instant.parse(point.getString("time")).toEpochMilli()
                if (at > nowMs + 25 * 3_600_000L) break
                val d = point.getJSONObject("data")
                val hourly = d.optJSONObject("next_1_hours") ?: continue
                val det = d.getJSONObject("instant").getJSONObject("details")
                if (!det.has("air_temperature")) continue
                hours += HourPoint(at, det.getDouble("air_temperature"), hourly.optJSONObject("summary")?.optString("symbol_code").orEmpty(),
                    if (det.has("wind_speed")) det.getDouble("wind_speed") else null)
            }
            val temps = (0 until minOf(12, series.length())).mapNotNull { i ->
                series.getJSONObject(i).getJSONObject("data").getJSONObject("instant").getJSONObject("details").let { d -> if (d.has("air_temperature")) d.getDouble("air_temperature") else null }
            }
            return WeatherReading(
                place, details.getDouble("air_temperature"), symbol,
                if (details.has("wind_speed")) details.getDouble("wind_speed") else null, temps.minOrNull(), temps.maxOrNull(),
                Instant.parse(first.getString("time")).toEpochMilli(), nowMs, later?.first, later?.second,
                tomorrowTemps.minOrNull(), tomorrowTemps.maxOrNull(), noon?.second, hours = hours,
            )
        }

        private fun isPrecipitation(symbol: String) = listOf("rain", "sleet", "snow").any { it in symbol }

        fun readingOf(text: String): WeatherReading? = runCatching { readingOf(JSONObject(text)) }.getOrNull()

        /** The hub's form ({place, temperature, symbol, wind, min, max, timeMs, fetchedAtMs}), the one kept here too. */
        fun readingOf(o: JSONObject): WeatherReading = WeatherReading(
            o.optString("place"), o.getDouble("temperature"), o.optString("symbol"),
            o.optDouble("wind").takeUnless { it.isNaN() }, o.optDouble("min").takeUnless { it.isNaN() }, o.optDouble("max").takeUnless { it.isNaN() },
            o.optLong("timeMs"), o.getLong("fetchedAtMs"),
            o.optString("laterSymbol").takeIf { it.isNotEmpty() && it != "null" }, o.optLong("laterTimeMs").takeIf { it > 0 },
            o.optDouble("tomorrowMin").takeUnless { it.isNaN() }, o.optDouble("tomorrowMax").takeUnless { it.isNaN() },
            o.optString("tomorrowSymbol").takeIf { it.isNotEmpty() && it != "null" },
            o.optLong("validUntilMs").takeIf { it > 0 },
            o.optJSONArray("hours")?.let { a -> List(a.length()) { a.getJSONObject(it) }.mapNotNull { h ->
                if (!h.has("timeMs") || !h.has("temperature")) null
                else HourPoint(h.getLong("timeMs"), h.getDouble("temperature"), h.optString("symbol"),
                    h.optDouble("wind").takeUnless { it.isNaN() }, h.optDouble("precipProb").takeUnless { it.isNaN() })
            } }.orEmpty(),
        )

        fun readingJson(r: WeatherReading): JSONObject = JSONObject().put("place", r.place).put("temperature", r.temperature).put("symbol", r.symbol)
            .put("wind", r.wind ?: JSONObject.NULL).put("min", r.min ?: JSONObject.NULL).put("max", r.max ?: JSONObject.NULL)
            .put("timeMs", r.timeMs).put("fetchedAtMs", r.fetchedAtMs)
            .put("laterSymbol", r.laterSymbol ?: JSONObject.NULL).put("laterTimeMs", r.laterTimeMs ?: JSONObject.NULL)
            .put("tomorrowMin", r.tomorrowMin ?: JSONObject.NULL).put("tomorrowMax", r.tomorrowMax ?: JSONObject.NULL)
            .put("tomorrowSymbol", r.tomorrowSymbol ?: JSONObject.NULL).put("validUntilMs", r.validUntilMs ?: JSONObject.NULL)
            .put("hours", org.json.JSONArray(r.hours.map { h ->
                JSONObject().put("timeMs", h.timeMs).put("temperature", h.temperature).put("symbol", h.symbol)
                    .put("wind", h.wind ?: JSONObject.NULL).put("precipProb", h.precipProb ?: JSONObject.NULL)
            }))

        fun placeOf(text: String): Place? = runCatching { JSONObject(text).let { Place(it.optString("name"), it.getDouble("latitude"), it.getDouble("longitude")) } }.getOrNull()
        fun placeJson(p: Place): JSONObject = JSONObject().put("name", p.name).put("latitude", p.latitude).put("longitude", p.longitude)
    }
}
