package dev.hram.androsaur.weather

import kotlin.math.abs
import kotlin.math.roundToInt

/** A place to show the weather for. */
data class Place(val name: String, val latitude: Double, val longitude: Double) {
    /** Two places this close (about a kilometre) are the same for the weather. */
    fun near(other: Place): Boolean = abs(latitude - other.latitude) < 0.01 && abs(longitude - other.longitude) < 0.01
}

/** One hour of the forecast, for the strip over the face: temperature, MET's symbol, wind. */
data class HourPoint(val timeMs: Long, val temperature: Double, val symbol: String, val wind: Double?, val precipProb: Double? = null)

/** One column of the strip: [now] is the first one, labelled «Сейчас» and showing the weather now. */
data class StripColumn(val timeMs: Long, val temperature: Double, val symbol: String, val wind: Double?, val now: Boolean)

/**
 * The forecast strip over the face (the owner's picture: one temperature line, under it the weather's icon, the wind and the time):
 * «Сейчас» and then every [stepHours] hours, [count] columns, so the stand shows the day at a glance without scrolling.
 */
object WeatherStrip {
    /** [zoneOffsetMs] — the local time zone's offset, so «round» hours are round on the local clock. */
    fun columns(reading: WeatherReading, nowMs: Long, stepHours: Int = 3, count: Int = 6, zoneOffsetMs: Long = 0): List<StripColumn> {
        val hours = reading.hours.sortedBy { it.timeMs }
        if (hours.isEmpty()) return emptyList()
        val hourMs = 3_600_000L
        val thisHour = nowMs - nowMs % hourMs
        val out = mutableListOf(StripColumn(thisHour, reading.temperature, reading.symbol, reading.wind, now = true))
        // The next columns fall on round hours of the step (12:00, 15:00…), so the labels read like a clock.
        var next = hours.firstOrNull { it.timeMs > thisHour && (((it.timeMs + zoneOffsetMs) / hourMs) % stepHours == 0L || hours.size < 6) }?.timeMs ?: return out
        while (out.size < count) {
            val h = hours.firstOrNull { it.timeMs == next } ?: hours.firstOrNull { it.timeMs >= next } ?: break
            out += StripColumn(h.timeMs, h.temperature, h.symbol, h.wind, now = false)
            next = h.timeMs + stepHours * hourMs
        }
        return out
    }
}

/**
 * The weather now and for the next 12 hours, as the hub hands it or as this phone fetched it from MET Norway. [symbol] is MET's
 * code (`cloudy`, `lightrain_day`…); times are Unix milliseconds: [timeMs] the forecast hour, [fetchedAtMs] when it was taken.
 */
data class WeatherReading(
    val place: String,
    val temperature: Double,
    val symbol: String,
    val wind: Double?,
    val min: Double?,
    val max: Double?,
    val timeMs: Long,
    val fetchedAtMs: Long,
    /** The nearest precipitation in the next 12 hours when there is none now: MET's code and its hour, or null. */
    val laterSymbol: String? = null,
    val laterTimeMs: Long? = null,
    /** Tomorrow in local time: the lowest and highest temperature and what is expected in the day. */
    val tomorrowMin: Double? = null,
    val tomorrowMax: Double? = null,
    val tomorrowSymbol: String? = null,
    /** Until when the hub counts this weather fresh (it may ask a source rarely, e.g. Yandex every two hours); null — an hour. */
    val validUntilMs: Long? = null,
    /** The forecast by the hour for the next day (from the hub, or from MET when the phone asks itself). */
    val hours: List<HourPoint> = emptyList(),
)

/**
 * Where the weather comes from. The phone is autonomous: the hub, when it answers, saves it the requests and may set the place
 * for the whole house; without the hub the phone asks MET Norway itself; without the internet it shows the last weather it has
 * and says how old it is.
 */
object WeatherChoice {
    /** The hub's weather is used while it is this fresh. */
    const val HUB_FRESH_MS = 60 * 60_000L
    /** The phone asks the weather service itself no more often than this. */
    const val OWN_EVERY_MS = 30 * 60_000L
    /** Older weather is shown with its age. */
    const val STALE_MS = 2 * 60 * 60_000L
    /** Older weather is not shown at all. */
    const val DROP_MS = 12 * 60 * 60_000L

    /** The place: the one set in the hub wins over where the phone is. */
    fun place(hubPlace: Place?, devicePlace: Place?): Place? = hubPlace ?: devicePlace

    /** Whether the hub's weather is still fresh: until the moment the hub gave (at most six hours), or an hour without one. */
    fun hubFresh(hub: WeatherReading, nowMs: Long): Boolean =
        nowMs < (hub.validUntilMs?.coerceAtMost(hub.fetchedAtMs + 6 * 3_600_000L) ?: (hub.fetchedAtMs + HUB_FRESH_MS))

    /** What to show: the hub's weather while it is fresh, otherwise the newer of the two; nothing older than [DROP_MS]. */
    fun show(hub: WeatherReading?, own: WeatherReading?, nowMs: Long): WeatherReading? {
        if (hub != null && hubFresh(hub, nowMs)) return hub
        return listOfNotNull(hub, own).filter { nowMs - it.fetchedAtMs < DROP_MS }.maxByOrNull { it.fetchedAtMs }
    }

    /**
     * Whether the phone should ask MET itself now: not while the hub gives fresh weather; otherwise when it has none of its own
     * for [place] or its own is older than [OWN_EVERY_MS]. [ownPlace] is the place the phone's own weather is for.
     */
    fun fetchOwn(hub: WeatherReading?, own: WeatherReading?, ownPlace: Place?, place: Place?, nowMs: Long): Boolean {
        if (place == null) return false
        if (hub != null && hubFresh(hub, nowMs)) return false
        return own == null || ownPlace == null || !ownPlace.near(place) || nowMs - own.fetchedAtMs >= OWN_EVERY_MS
    }
}

/** The weather in words for the screen. */
object WeatherText {
    /** «+11°», «−3°», «0°». */
    fun temperature(celsius: Double): String {
        val t = celsius.roundToInt()
        return when {
            t > 0 -> "+$t°"
            t < 0 -> "−${-t}°"
            else -> "0°"
        }
    }

    private val sky = mapOf("clearsky" to "ясно", "fair" to "малооблачно", "partlycloudy" to "переменная облачность", "cloudy" to "облачно",
        "fog" to "туман", "thunder" to "гроза")
    private val precipitation = mapOf("rain" to "дождь", "sleet" to "мокрый снег", "snow" to "снег", "hail" to "град")

    /** MET's code in words: `lightrainshowers_day` is «небольшой кратковременный дождь». */
    fun describe(symbol: String): String {
        var code = symbol.substringBefore('_')
        sky[code]?.let { return it }
        val thunder = "thunder" in code
        code = code.replace("andthunder", "")
        var intensity = ""
        for ((prefix, word) in listOf("lights" to "небольшой ", "light" to "небольшой ", "heavy" to "сильный ")) {
            if (code.startsWith(prefix)) { intensity = word; code = code.removePrefix(prefix); break }
        }
        val showers = code.endsWith("showers")
        val base = precipitation[code.removeSuffix("showers")] ?: "осадки"
        val text = intensity + (if (showers && base != "мокрый снег") "кратковременный " else "") + base
        return if (thunder) "$text, гроза" else text
    }

    /** One line under the clock: «+11° · облачно · ветер 5 м/с», with the age when it is old («· 3 ч назад»). */
    fun line(reading: WeatherReading, nowMs: Long): String {
        val parts = mutableListOf(temperature(reading.temperature), describe(reading.symbol))
        reading.wind?.let { parts += "ветер ${it.roundToInt()} м/с" }
        val age = nowMs - reading.fetchedAtMs
        if (age >= WeatherChoice.STALE_MS) parts += "${age / 3_600_000} ч назад"
        return parts.joinToString(" · ")
    }
}
