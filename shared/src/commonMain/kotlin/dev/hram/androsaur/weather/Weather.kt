package dev.hram.androsaur.weather

import kotlin.math.abs
import kotlin.math.roundToInt

/** A place to show the weather for. */
data class Place(val name: String, val latitude: Double, val longitude: Double) {
    /** Two places this close (about a kilometre) are the same for the weather. */
    fun near(other: Place): Boolean = abs(latitude - other.latitude) < 0.01 && abs(longitude - other.longitude) < 0.01
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

    /** What to show: the hub's weather while it is fresh, otherwise the newer of the two; nothing older than [DROP_MS]. */
    fun show(hub: WeatherReading?, own: WeatherReading?, nowMs: Long): WeatherReading? {
        if (hub != null && nowMs - hub.fetchedAtMs < HUB_FRESH_MS) return hub
        return listOfNotNull(hub, own).filter { nowMs - it.fetchedAtMs < DROP_MS }.maxByOrNull { it.fetchedAtMs }
    }

    /**
     * Whether the phone should ask MET itself now: not while the hub gives fresh weather; otherwise when it has none of its own
     * for [place] or its own is older than [OWN_EVERY_MS]. [ownPlace] is the place the phone's own weather is for.
     */
    fun fetchOwn(hub: WeatherReading?, own: WeatherReading?, ownPlace: Place?, place: Place?, nowMs: Long): Boolean {
        if (place == null) return false
        if (hub != null && nowMs - hub.fetchedAtMs < HUB_FRESH_MS) return false
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

    private val sky = mapOf("clearsky" to "ясно", "fair" to "малооблачно", "partlycloudy" to "переменная облачность", "cloudy" to "облачно", "fog" to "туман")
    private val precipitation = mapOf("rain" to "дождь", "sleet" to "мокрый снег", "snow" to "снег")

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
