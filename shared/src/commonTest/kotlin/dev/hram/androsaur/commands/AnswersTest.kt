package dev.hram.androsaur.commands

import dev.hram.androsaur.weather.WeatherReading
import kotlin.test.Test
import kotlin.test.assertEquals

class AnswersTest {
    private fun weather(symbol: String = "cloudy", later: String? = null, tomorrow: Triple<Double, Double, String>? = Triple(10.3, 11.5, "cloudy")) =
        WeatherReading("СПб", 11.2, symbol, 4.9, 9.8, 12.0, 0, 0, later, later?.let { 1L }, tomorrow?.first, tomorrow?.second, tomorrow?.third)

    @Test fun theTimeInWords() {
        assertEquals("Сейчас девятнадцать пятьдесят пять.", Answers.time(19, 55))
        assertEquals("Сейчас семь ноль пять.", Answers.time(7, 5))
        assertEquals("Сейчас ровно восемь часов.", Answers.time(8, 0))
        assertEquals("Сейчас ровно двадцать один час.", Answers.time(21, 0))
        assertEquals("Сейчас ноль двадцать.", Answers.time(0, 20))
    }

    @Test fun numbersAgreeWithNouns() {
        assertEquals("одна минута", Answers.count(1, "минута", "минуты", "минут", feminine = true))
        assertEquals("одну минуту", Answers.count(1, "минуту", "минуты", "минут", feminine = true, accusative = true))
        assertEquals("двадцать две минуты", Answers.count(22, "минута", "минуты", "минут", feminine = true))
        assertEquals("одиннадцать минут", Answers.count(11, "минута", "минуты", "минут", feminine = true))
        assertEquals("пять метров", Answers.count(5, "метр", "метра", "метров", feminine = false))
        assertEquals("один час", Answers.count(1, "час", "часа", "часов", feminine = false))
    }

    @Test fun timerPhrases() {
        assertEquals("Ставлю таймер на десять минут.", Answers.timerSet(600))
        assertEquals("Ставлю таймер на одну минуту.", Answers.timerSet(60))
        assertEquals("Ставлю таймер на полчаса.", Answers.timerSet(1800))
        assertEquals("Ставлю таймер на одну минуту тридцать секунд.", Answers.timerSet(90))
        assertEquals("Ставлю таймер на один час тридцать минут.", Answers.timerSet(5400))
        assertEquals("До звонка десять минут.", Answers.timerLeft(581))
        assertEquals("До звонка одна минута двадцать секунд.", Answers.timerLeft(80))
        assertEquals("Таймер не стоит.", Answers.timerLeft(null))
        assertEquals("9:41", Answers.countdown(581))
        assertEquals("1:05:00", Answers.countdown(3900))
        assertEquals("0:07", Answers.countdown(7))
    }

    @Test fun theWeatherNowAndTomorrow() {
        assertEquals("Сейчас плюс одиннадцать, облачно, ветер пять метров в секунду.", Answers.weatherNow(weather(), null))
        assertEquals(
            "Сейчас плюс одиннадцать, облачно, ветер пять метров в секунду. К вечеру небольшой дождь.",
            Answers.weatherNow(weather(later = "lightrain"), "к вечеру"),
        )
        assertEquals("Сейчас плюс одиннадцать, дождь, ветер пять метров в секунду, возьми зонт.", Answers.weatherNow(weather(symbol = "rain"), null))
        assertEquals("Погоды пока нет: не было связи.", Answers.weatherNow(null, null))
        assertEquals("Завтра днём плюс двенадцать, ночью плюс десять, облачно.", Answers.weatherTomorrow(weather()))
        assertEquals("Завтра днём плюс двенадцать, ночью плюс десять, небольшой дождь, возьми зонт.", Answers.weatherTomorrow(weather(tomorrow = Triple(10.3, 11.5, "lightrain"))))
        assertEquals("Прогноза на завтра пока нет.", Answers.weatherTomorrow(weather(tomorrow = null)))
    }
}
