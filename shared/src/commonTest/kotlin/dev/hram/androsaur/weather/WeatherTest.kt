package dev.hram.androsaur.weather

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class WeatherTest {
    private val spb = Place("Санкт-Петербург", 59.9386, 30.3141)
    private val here = Place("", 55.7558, 37.6173)
    private val hour = 3_600_000L
    private fun reading(at: Long, t: Double = 11.2) = WeatherReading("СПб", t, "cloudy", 4.9, 9.8, 12.0, at, at)

    @Test fun theHubsPlaceWinsOverTheDevicesOwn() {
        assertEquals(spb, WeatherChoice.place(spb, here))
        assertEquals(here, WeatherChoice.place(null, here))
        assertNull(WeatherChoice.place(null, null))
    }

    @Test fun freshHubWeatherIsShownAndSavesTheRequest() {
        val now = 10 * hour
        val hub = reading(now - 10 * 60_000)
        assertEquals(hub, WeatherChoice.show(hub, reading(now - 1000, t = 5.0), now))
        assertFalse(WeatherChoice.fetchOwn(hub, null, null, spb, now))
    }

    @Test fun withoutTheHubThePhoneAsksItselfAndNotTooOften() {
        val now = 10 * hour
        assertTrue(WeatherChoice.fetchOwn(null, null, null, here, now))
        assertFalse(WeatherChoice.fetchOwn(null, reading(now - 10 * 60_000), here, here, now))
        assertTrue(WeatherChoice.fetchOwn(null, reading(now - 31 * 60_000), here, here, now))
        assertTrue(WeatherChoice.fetchOwn(null, reading(now - 60_000), here, spb, now), "the place changed")
        assertFalse(WeatherChoice.fetchOwn(null, null, null, null, now), "no place, nothing to ask")
    }

    @Test fun oldHubWeatherGivesWayToNewerOwn() {
        val now = 10 * hour
        val own = reading(now - 60_000, t = 5.0)
        assertEquals(own, WeatherChoice.show(reading(now - 3 * hour), own, now))
        assertTrue(WeatherChoice.fetchOwn(reading(now - 3 * hour), null, null, spb, now))
    }

    @Test fun offlineTheLastWeatherStaysUntilItIsTooOld() {
        val now = 20 * hour
        assertEquals(11.2, WeatherChoice.show(null, reading(now - 5 * hour), now)?.temperature)
        assertNull(WeatherChoice.show(reading(now - 13 * hour), null, now))
    }

    @Test fun temperatureHasASign() {
        assertEquals("+11°", WeatherText.temperature(11.2))
        assertEquals("−3°", WeatherText.temperature(-2.6))
        assertEquals("0°", WeatherText.temperature(-0.3))
    }

    @Test fun symbolsInWords() {
        assertEquals("ясно", WeatherText.describe("clearsky_night"))
        assertEquals("переменная облачность", WeatherText.describe("partlycloudy_day"))
        assertEquals("небольшой дождь", WeatherText.describe("lightrain"))
        assertEquals("сильный кратковременный дождь", WeatherText.describe("heavyrainshowers_day"))
        assertEquals("мокрый снег", WeatherText.describe("sleet"))
        assertEquals("небольшой кратковременный снег, гроза", WeatherText.describe("lightssnowshowersandthunder_day"))
    }

    @Test fun oneLineAndItsAgeWhenOld() {
        val now = 10 * hour
        assertEquals("+11° · облачно · ветер 5 м/с", WeatherText.line(reading(now - 60_000), now))
        assertEquals("+11° · облачно · ветер 5 м/с · 3 ч назад", WeatherText.line(reading(now - 3 * hour), now))
    }

    @Test fun theHubSaysHowLongItsWeatherIsFresh() {
        val now = 10 * hour
        val yandex = reading(now - 90 * 60_000).copy(validUntilMs = now + 45 * 60_000)
        assertEquals(yandex, WeatherChoice.show(yandex, reading(now - 60_000, t = 5.0), now), "fresh for two hours, not one")
        assertFalse(WeatherChoice.fetchOwn(yandex, null, null, spb, now))
        val tooLong = reading(now - 7 * hour).copy(validUntilMs = now + hour)
        assertTrue(WeatherChoice.fetchOwn(tooLong, null, null, spb, now), "never longer than six hours")
    }

    @Test fun hailAndThunderInWords() {
        assertEquals("гроза", WeatherText.describe("thunder"))
        assertEquals("град, гроза", WeatherText.describe("hailandthunder"))
        assertEquals("град", WeatherText.describe("hail"))
    }

    @Test fun theStripShowsNowAndThenEveryThreeHours() {
        val h = hour
        val now = 999 * h + 20 * 60_000          // 999 h is a multiple of 3 h, plus 20 minutes
        val hours = (0..24).map { HourPoint(999 * h + it * h, 10.0 + it % 5, "cloudy", 5.0) }
        val r = reading(now - 60_000).copy(temperature = 9.6, symbol = "lightrain", hours = hours)
        val cols = WeatherStrip.columns(r, now)
        assertEquals(6, cols.size)
        assertTrue(cols[0].now)
        assertEquals(9.6, cols[0].temperature, "«Сейчас» is the weather now, not the forecast of the hour")
        assertEquals("lightrain", cols[0].symbol)
        assertEquals(listOf(3L, 6L, 9L, 12L, 15L), cols.drop(1).map { (it.timeMs - 999 * h) / h })
    }

    @Test fun noHoursNoStrip() = assertEquals(emptyList(), WeatherStrip.columns(reading(0), 0))
}
