package dev.hram.androsaur.commands

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class HoroscopeTest {
    private val reading = DailyHoroscope("katya", "aries", "Овен", "Хороший день.", "Europa Plus", 1_000, 86_400_000)

    @Test fun offlineForecastIsBoundToThePersonAndExpiresExactlyAtMidnight() {
        assertTrue(reading.usableFor("katya", 1_001))
        assertFalse(reading.usableFor("papa", 1_001))
        assertFalse(reading.usableFor("katya", 86_400_000))
        assertFalse(reading.usableFor("katya", 999))
        assertFalse(reading.copy(validUntilMs = 100 * 3_600_000L).usableFor("katya", 1_001))
    }

    @Test fun introductionConnectsTheSignAndUsesTheProvidersPronunciation() {
        val leo = reading.copy(sign = "leo", signName = "Лев", sourceSpeechName = "радио Европа Плюс")
        assertEquals("Гороскоп на сегодня для знака Лев, по данным радио Европа Плюс. Хороший день.", leo.spoken())
        assertFalse(leo.spoken().contains("Europa Plus"))
        assertTrue(reading.spoken().contains("Europa Plus")) // Older hubs/cache still work.
        assertTrue(reading.copy(sourceSpeechName = "").spoken().contains("Europa Plus"))
        val other = reading.copy(sourceName = "Другой источник", sourceSpeechName = "другой прогноз")
        assertTrue(other.spoken().contains("по данным другой прогноз"))
    }

    @Test fun malformedSuccessNeverTurnsIntoSpokenNullOrAnEmptyForecast() {
        for (bad in listOf(reading.copy(text = ""), reading.copy(sign = ""), reading.copy(signName = ""),
            reading.copy(sourceName = ""), reading.copy(text = "x".repeat(3001))))
            assertFalse(bad.usableFor("katya", 1_001))
        assertTrue(reading.spoken().contains("Europa Plus"))
        assertTrue(HoroscopeAnswers.problem("sign_missing").contains("знак зодиака"))
        assertTrue(HoroscopeAnswers.problem("disabled").contains("выключен"))
    }
}
