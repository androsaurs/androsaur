package dev.hram.androsaur.commands

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MorningHoroscopeTest {
    private val today = "2026-11-06"
    private val reading = DailyHoroscope("katya", "aries", "Овен", "Хороший день.", "Europa Plus",
        1_000, 86_400_000, "радио Европа Плюс", greetingEnabled = true)

    @Test fun morningOnlyFirstGreetingAndGlobalGreetingSwitchAreRespected() {
        for (hour in 0..23) assertEquals(hour in 5..11, MorningHoroscope.due(true, null, today, hour))
        assertTrue(MorningHoroscope.due(true, "2026-11-05", today, 8))
        assertFalse(MorningHoroscope.due(true, today, today, 8)) // Also after restart.
        assertFalse(MorningHoroscope.due(false, null, today, 8))
    }

    @Test fun noAdditionWithoutExplicitPermissionOrTodaysPersonalReading() {
        assertEquals(listOf(reading.spoken()), MorningHoroscope.parts(reading, "katya", 2_000))
        assertTrue(MorningHoroscope.parts(null, "katya", 2_000).isEmpty())
        assertTrue(MorningHoroscope.parts(reading.copy(greetingEnabled = false), "katya", 2_000).isEmpty())
        assertTrue(MorningHoroscope.parts(reading, "papa", 2_000).isEmpty())
        assertTrue(MorningHoroscope.parts(reading, "katya", 86_400_000).isEmpty())
    }

    @Test fun leavingChangingPersonOrUserInteractionCancelTheAutomaticAddition() {
        assertTrue(MorningHoroscope.stillFor("katya", "katya", true, false))
        assertFalse(MorningHoroscope.stillFor("katya", null, false, false))
        assertFalse(MorningHoroscope.stillFor("katya", "papa", true, false))
        assertFalse(MorningHoroscope.stillFor("katya", "katya", true, true))
        assertFalse(MorningHoroscope.stillFor("katya", "katya", false, false))
    }

    @Test fun longForecastIsDeliveredInOrderWithoutTruncationBeyondTheAudioLimit() {
        val long = reading.copy(text = "Хороший день. ".repeat(200).trim())
        val parts = MorningHoroscope.parts(long, "katya", 2_000)
        assertTrue(parts.size > 1)
        assertTrue(parts.all { it.isNotBlank() && it.length <= 600 })
        assertEquals(long.spoken(), parts.joinToString(" "))
        assertEquals(1, parts.count { "Гороскоп на сегодня" in it })
    }
}
