package dev.hram.androsaur.weather

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class GreetingTest {
    private val now = 100 * 3_600_000L
    private fun weather(symbol: String = "cloudy", later: String? = null, t: Double = 11.2, age: Long = 60_000) =
        WeatherReading("СПб", t, symbol, 4.9, 9.8, 12.0, now, now - age, later, later?.let { now + 8 * 3_600_000L })

    @Test fun onceADay() {
        assertTrue(Greeting.due(null, "2026-10-25"))
        assertFalse(Greeting.due("2026-10-25", "2026-10-25"))
        assertTrue(Greeting.due("2026-10-24", "2026-10-25"))
    }

    @Test fun theTimeOfDay() {
        assertEquals("Доброе утро", Greeting.salutation(7))
        assertEquals("Добрый день", Greeting.salutation(13))
        assertEquals("Добрый вечер", Greeting.salutation(19))
        assertEquals("Доброй ночи", Greeting.salutation(2))
    }

    @Test fun numbersInWords() {
        assertEquals("плюс одиннадцать", Greeting.temperature(11.2))
        assertEquals("минус двадцать три", Greeting.temperature(-23.4))
        assertEquals("ноль", Greeting.temperature(0.3))
        assertEquals("плюс сорок", Greeting.temperature(40.0))
        assertEquals("плюс один", Greeting.temperature(0.6))
    }

    @Test fun whenTheRainComes() {
        assertEquals("скоро", Greeting.whenLater(9, 0.5, false))
        assertEquals("к вечеру", Greeting.whenLater(18, 8.0, false))
        assertEquals("днём", Greeting.whenLater(14, 6.0, false))
        assertEquals("ночью", Greeting.whenLater(1, 5.0, true))
        assertEquals("завтра утром", Greeting.whenLater(7, 10.0, true))
    }

    @Test fun theMorningGreetingWithRainLater() = assertEquals(
        "Доброе утро, Папа. Плюс одиннадцать, облачно. К вечеру небольшой дождь, возьми зонт.",
        Greeting.text("Папа", 8, weather(later = "lightrain"), now, "к вечеру"),
    )

    @Test fun rainNowSaysTheUmbrellaAtOnce() = assertEquals(
        "Добрый день, Катя. Плюс одиннадцать, дождь, возьми зонт.",
        Greeting.text("Катя", 13, weather(symbol = "rain"), now, null),
    )

    @Test fun snowLaterNeedsNoUmbrella() = assertEquals(
        "Добрый вечер, Лёша. Минус три, ясно. Ночью снег.",
        Greeting.text("Лёша", 20, weather(symbol = "clearsky_night", later = "snow", t = -3.0), now, "ночью"),
    )

    @Test fun oldOrNoWeatherIsNotTold() {
        assertEquals("Доброе утро, Папа.", Greeting.text("Папа", 8, weather(age = 4 * 3_600_000L), now, null))
        assertEquals("Доброе утро, Папа.", Greeting.text("Папа", 8, null, now, null))
    }
}
