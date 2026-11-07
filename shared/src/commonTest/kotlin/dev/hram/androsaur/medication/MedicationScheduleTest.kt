package dev.hram.androsaur.medication

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class MedicationScheduleTest {
    private val nov7 = 20764L // 2026-11-07
    private fun at(h: Int, m: Int = 0) = h * 60 + m
    private fun key(times: List<String>, day: Long, minute: Int) = MedicationSchedule.intervalStart(times, day, minute)?.let(MedicationSchedule::key)

    @Test fun timesAreParsedStrictly() {
        assertEquals(480, MedicationSchedule.minutes("08:00"))
        assertEquals(485, MedicationSchedule.minutes("8:05"))
        for (bad in listOf("", "24:00", "8", "08:60", "8:5", "ab:cd", "08:00:00")) assertNull(MedicationSchedule.minutes(bad), bad)
    }

    @Test fun anIntervalRunsToTheNextTimeAndThroughMidnight() {
        val times = listOf("08:00", "18:00")
        assertEquals("2026-11-07T08:00", key(times, nov7, at(12)))
        assertEquals("2026-11-07T18:00", key(times, nov7, at(18)))
        assertEquals("2026-11-07T18:00", key(times, nov7, at(23, 59)))
        // after midnight it is still the evening interval, not a new one
        assertEquals("2026-11-07T18:00", key(times, nov7 + 1, at(3)))
        assertEquals("2026-11-08T08:00", key(times, nov7 + 1, at(8)))
    }

    @Test fun anyNumberOfTimesUnsortedOrRepeated() {
        val three = listOf("20:00", "08:00", "14:00", "14:00")
        assertEquals("2026-11-07T14:00", key(three, nov7, at(15)))
        assertEquals("2026-11-06T20:00", key(three, nov7, at(7)))
        assertEquals("2026-11-06T09:00", key(listOf("09:00"), nov7, at(8)))
        assertEquals("2026-11-07T09:00", key(listOf("09:00"), nov7, at(10)))
        assertNull(MedicationSchedule.intervalStart(emptyList(), nov7, at(10)))
        assertNull(MedicationSchedule.intervalStart(listOf("bad"), nov7, at(10)))
    }

    @Test fun theKeyHandlesLeapDaysAndYearEnds() {
        assertEquals("2024-02-29T07:05", MedicationSchedule.key(19782L to at(7, 5)))
        assertEquals("1970-01-01T00:00", MedicationSchedule.key(0L to 0))
        assertEquals("2026-12-31T23:30", MedicationSchedule.key((nov7 + 54) to at(23, 30)))
    }

    @Test fun aReminderIsDueOncePerInterval() {
        val times = listOf("08:00", "18:00")
        val first = MedicationSchedule.reminderDue(times, nov7, at(9), null)
        assertEquals("2026-11-07T08:00", first)
        assertNull(MedicationSchedule.reminderDue(times, nov7, at(11), first))
        // a new interval wants its own reminder
        assertEquals("2026-11-07T18:00", MedicationSchedule.reminderDue(times, nov7, at(18, 1), first))
        // the evening interval is one across midnight: no second reminder at 02:00
        val evening = MedicationSchedule.reminderDue(times, nov7, at(19), first)
        assertNull(MedicationSchedule.reminderDue(times, nov7 + 1, at(2), evening))
        assertNull(MedicationSchedule.reminderDue(emptyList(), nov7, at(9), null))
    }

    @Test fun takenCountsOnlyInTheOpenWindow() {
        val times = listOf("08:00", "18:00")
        val morning = "2026-11-07T08:00"
        // no reminder yet: nothing to confirm
        assertNull(MedicationSchedule.windowOpen(times, nov7, at(9), null, null))
        // reminded in this interval and not taken: open
        assertEquals(morning, MedicationSchedule.windowOpen(times, nov7, at(9), morning, null))
        // already taken: closed
        assertNull(MedicationSchedule.windowOpen(times, nov7, at(9), morning, morning))
        // the reminder was in the morning interval, now it is the evening one: closed
        assertNull(MedicationSchedule.windowOpen(times, nov7, at(19), morning, null))
    }
}
