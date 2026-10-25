package dev.hram.androsaur.weather

import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * The greeting at the first meeting of the day: «Доброе утро, Папа. Плюс одиннадцать, облачно. К вечеру небольшой дождь, возьми
 * зонт.» Numbers are words, so the voice does not stumble over «+11°». The person is the camera's permanent id: a rename does not
 * make it greet twice in a day.
 */
object Greeting {
    /** Weather older than this is not told. */
    const val WEATHER_FRESH_MS = 3 * 3_600_000L

    /** Whether to greet: not yet today ([today] and [lastGreeted] are local dates such as «2026-10-25»). */
    fun due(lastGreeted: String?, today: String): Boolean = lastGreeted != today

    fun salutation(hour: Int): String = when (hour) {
        in 5..11 -> "Доброе утро"
        in 12..16 -> "Добрый день"
        in 17..22 -> "Добрый вечер"
        else -> "Доброй ночи"
    }

    private val ones = listOf("ноль", "один", "два", "три", "четыре", "пять", "шесть", "семь", "восемь", "девять", "десять",
        "одиннадцать", "двенадцать", "тринадцать", "четырнадцать", "пятнадцать", "шестнадцать", "семнадцать", "восемнадцать", "девятнадцать")
    private val tens = listOf("", "", "двадцать", "тридцать", "сорок", "пятьдесят", "шестьдесят", "семьдесят", "восемьдесят", "девяносто")

    /** 0..99 in words («сорок два»); bigger numbers as digits (no weather goes there). */
    fun words(n: Int): String = when {
        n < 0 || n > 99 -> n.toString()
        n < 20 -> ones[n]
        n % 10 == 0 -> tens[n / 10]
        else -> "${tens[n / 10]} ${ones[n % 10]}"
    }

    /** «плюс одиннадцать», «минус три», «ноль». */
    fun temperature(celsius: Double): String {
        val t = celsius.roundToInt()
        return when {
            t > 0 -> "плюс ${words(t)}"
            t < 0 -> "минус ${words(abs(t))}"
            else -> "ноль"
        }
    }

    /**
     * When the precipitation comes, said from now: «скоро» within the hour, «утром», «днём», «к вечеру», «ночью», «завтра утром».
     * [laterHour] is its local hour, [hoursAhead] how far from now, [tomorrow] whether it is on the next local date.
     */
    fun whenLater(laterHour: Int, hoursAhead: Double, tomorrow: Boolean): String = when {
        hoursAhead < 1 -> "скоро"
        tomorrow && laterHour in 5..11 -> "завтра утром"
        laterHour in 5..11 -> "утром"
        laterHour in 12..16 -> "днём"
        laterHour in 17..22 -> "к вечеру"
        else -> "ночью"
    }

    private fun wet(symbol: String) = "rain" in symbol || "sleet" in symbol

    /**
     * The whole greeting. [weather] is told when fresh; [later] is when the precipitation comes, from [whenLater], or null when
     * none is expected.
     */
    fun text(name: String, hour: Int, weather: WeatherReading?, nowMs: Long, later: String?): String {
        val parts = mutableListOf("${salutation(hour)}, $name.")
        if (weather != null && nowMs - weather.fetchedAtMs < WEATHER_FRESH_MS) {
            val now = WeatherText.describe(weather.symbol)
            parts += "${temperature(weather.temperature).replaceFirstChar { it.uppercase() }}, $now" + if (wet(weather.symbol)) ", возьми зонт." else "."
            val laterSymbol = weather.laterSymbol
            if (laterSymbol != null && later != null) {
                parts += "${later.replaceFirstChar { it.uppercase() }} ${WeatherText.describe(laterSymbol)}" + if (wet(laterSymbol)) ", возьми зонт." else "."
            }
        }
        return parts.joinToString(" ")
    }
}
