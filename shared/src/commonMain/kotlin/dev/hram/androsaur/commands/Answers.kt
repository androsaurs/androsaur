package dev.hram.androsaur.commands

import dev.hram.androsaur.weather.Greeting
import dev.hram.androsaur.weather.WeatherReading
import dev.hram.androsaur.weather.WeatherText
import kotlin.math.roundToInt

/**
 * What the assistant says to the short voice commands (the audio module hears them, this app answers): the time, the weather
 * today and tomorrow, the timer. Words, not digits, so the voice reads them right; Russian agreement of numbers and nouns included.
 */
object Answers {
    /** «пять минут», «одна минута», «две минуты»: [n] in words with the noun in the right form; [accusative] for «на одну минуту». */
    fun count(n: Int, one: String, few: String, many: String, feminine: Boolean, accusative: Boolean = false): String {
        val word = when {
            n % 100 in 11..14 -> many
            n % 10 == 1 -> one
            n % 10 in 2..4 -> few
            else -> many
        }
        var number = Greeting.words(n)
        if (feminine && n % 100 !in 11..14) {
            if (n % 10 == 1) number = number.removeSuffix("один") + if (accusative) "одну" else "одна"
            if (n % 10 == 2) number = number.removeSuffix("два") + "две"
        }
        if (n == 0) number = "ноль"
        return "$number $word".replace("  ", " ")
    }

    private fun minutes(n: Int, acc: Boolean = false) = count(n, if (acc) "минуту" else "минута", "минуты", "минут", feminine = true, accusative = acc)
    private fun seconds(n: Int, acc: Boolean = false) = count(n, if (acc) "секунду" else "секунда", "секунды", "секунд", feminine = true, accusative = acc)
    private fun hours(n: Int) = count(n, "час", "часа", "часов", feminine = false)

    /** A length in words: «десять минут», «один час тридцать минут», «одну минуту тридцать секунд» ([accusative] after «на»). */
    fun duration(totalSeconds: Int, accusative: Boolean = false): String {
        val h = totalSeconds / 3600
        val m = totalSeconds % 3600 / 60
        val s = totalSeconds % 60
        if (totalSeconds == 1800) return "полчаса"
        return listOfNotNull(
            hours(h).takeIf { h > 0 },
            minutes(m, accusative).takeIf { m > 0 },
            seconds(s, accusative).takeIf { s > 0 && h == 0 },
        ).joinToString(" ").ifEmpty { seconds(0) }
    }

    /** «Сейчас девятнадцать пятьдесят пять.», «Сейчас семь ноль пять.», «Сейчас ровно восемь часов.» */
    fun time(hour: Int, minute: Int): String = when {
        minute == 0 -> "Сейчас ровно ${hours(hour)}."
        minute < 10 -> "Сейчас ${Greeting.words(hour)} ноль ${Greeting.words(minute)}."
        else -> "Сейчас ${Greeting.words(hour)} ${Greeting.words(minute)}."
    }

    private fun wet(symbol: String) = "rain" in symbol || "sleet" in symbol

    /** The weather now and the precipitation coming ([later] from [Greeting.whenLater]); null [weather] means there is none. */
    fun weatherNow(weather: WeatherReading?, later: String?): String {
        if (weather == null) return "Погоды пока нет: не было связи."
        val parts = mutableListOf("Сейчас ${Greeting.temperature(weather.temperature)}, ${WeatherText.describe(weather.symbol)}")
        weather.wind?.let { parts += "ветер ${count(it.roundToInt(), "метр", "метра", "метров", feminine = false)} в секунду" }
        var text = parts.joinToString(", ") + (if (wet(weather.symbol)) ", возьми зонт." else ".")
        val laterSymbol = weather.laterSymbol
        if (laterSymbol != null && later != null) text += " ${later.replaceFirstChar { it.uppercase() }} ${WeatherText.describe(laterSymbol)}."
        return text
    }

    /** «Завтра днём плюс одиннадцать, ночью плюс десять, облачно.» */
    fun weatherTomorrow(weather: WeatherReading?): String {
        val max = weather?.tomorrowMax
        val min = weather?.tomorrowMin
        if (weather == null || max == null || min == null) return "Прогноза на завтра пока нет."
        val sky = weather.tomorrowSymbol?.let { ", ${WeatherText.describe(it)}" + if (wet(it)) ", возьми зонт" else "" }.orEmpty()
        return "Завтра днём ${Greeting.temperature(max)}, ночью ${Greeting.temperature(min)}$sky."
    }

    fun timerSet(seconds: Int): String = "Ставлю таймер на ${duration(seconds, accusative = true)}."

    /** How long is left, rounded up to minutes when it is more than two of them. */
    fun timerLeft(secondsLeft: Int?): String {
        if (secondsLeft == null) return "Таймер не стоит."
        val shown = if (secondsLeft > 120) ((secondsLeft + 59) / 60) * 60 else secondsLeft
        return "До звонка ${duration(shown)}."
    }

    const val TIMER_UP = "Время вышло."
    const val TIMER_OFF = "Таймер выключен."

    /** The countdown over the face: «9:41», «1:05:00». */
    fun countdown(secondsLeft: Int): String {
        val s = secondsLeft.coerceAtLeast(0)
        val h = s / 3600
        val m = s % 3600 / 60
        val sec = (s % 60).toString().padStart(2, '0')
        return if (h > 0) "$h:${m.toString().padStart(2, '0')}:$sec" else "$m:$sec"
    }
}
