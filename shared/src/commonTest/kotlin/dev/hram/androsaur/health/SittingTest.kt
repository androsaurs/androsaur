package dev.hram.androsaur.health

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SittingTest {
    private val s = SittingSettings(firstMin = 40, repeatMin = 20, breakMin = 5)
    private fun min(n: Int) = n * 60_000L
    private val t0 = 1_000_000_000_000L

    /** The person is seen at each listed minute. */
    private fun walk(vararg minutes: Int): SittingState {
        var st = SittingState()
        for (m in minutes) st = SittingRules.seen(s, st, t0 + min(m))
        return st
    }

    /** The person is in front of the camera every minute from [from] to [to]. */
    private fun sit(st: SittingState, from: Int, to: Int): SittingState {
        var now = st
        for (m in from..to) now = SittingRules.seen(s, now, t0 + min(m))
        return now
    }

    @Test fun theFirstSightStartsASession() {
        val st = walk(0)
        assertEquals(t0, st.startMs); assertEquals(0, st.level); assertFalse(st.praise)
    }

    @Test fun aShortAbsenceKeepsTheSessionAndABreakStartsAnother() {
        var st = sit(SittingState(), 0, 10)
        st = SittingRules.seen(s, st, t0 + min(14)) // away 4 minutes: not a break
        assertEquals(t0, st.startMs)
        st = SittingRules.seen(s, st, t0 + min(18)) // 4 minutes again
        assertEquals(t0, st.startMs)
        st = SittingRules.seen(s, st, t0 + min(23)) // 5 minutes: a break
        assertEquals(t0 + min(23), st.startMs)
    }

    @Test fun remindersComeAtTheThresholdsAndOnlyOnce() {
        var st = sit(SittingState(), 0, 39)
        assertEquals(0, SittingRules.reminderDue(s, st, t0 + min(39)))
        st = sit(st, 40, 40)
        assertEquals(1, SittingRules.reminderDue(s, st, t0 + min(40)))
        st = SittingRules.reminded(st, 1)
        st = sit(st, 41, 59)
        assertEquals(0, SittingRules.reminderDue(s, st, t0 + min(59)))
        st = sit(st, 60, 60)
        assertEquals(2, SittingRules.reminderDue(s, st, t0 + min(60)))
        st = SittingRules.reminded(st, 2)
        // the second is final: nothing more however long the person sits
        st = sit(st, 61, 300)
        assertEquals(0, SittingRules.reminderDue(s, st, t0 + min(300)))
        assertEquals(2, st.level)
    }

    @Test fun aSessionNoticedLateGetsTheFinalReminderNotTwo() {
        val st = sit(SittingState(), 0, 60) // nobody was told yet (the phone was busy)
        assertEquals(2, SittingRules.reminderDue(s, st, t0 + min(60)))
    }

    @Test fun backAfterABreakWhileTiredIsPraisedOnceAndTheCharacterIsFreshAtOnce() {
        var st = sit(SittingState(), 0, 40)
        st = SittingRules.reminded(st, 1)
        st = SittingRules.seen(s, st, t0 + min(50)) // away 10 minutes
        assertEquals(0, st.level)
        assertEquals(t0 + min(50), st.startMs)
        assertTrue(st.praise)
        assertEquals(0, SittingRules.reminderDue(s, st, t0 + min(51)))
        st = SittingRules.praised(st)
        assertFalse(st.praise)
        st = SittingRules.seen(s, st, t0 + min(52))
        assertFalse(st.praise)
    }

    @Test fun noPraiseWhenTheSessionWasNotTired() {
        val st = SittingRules.seen(s, sit(SittingState(), 0, 20), t0 + min(30)) // back after a break, never reminded
        assertEquals(0, st.level); assertFalse(st.praise)
    }

    @Test fun aPendingPraiseSurvivesAnotherBreak() {
        var st = SittingState(startMs = t0, lastSeenMs = t0, level = 1)
        st = SittingRules.seen(s, st, t0 + min(10))
        assertTrue(st.praise)
        st = SittingRules.seen(s, st, t0 + min(30)) // again away, praise not spoken yet
        assertTrue(st.praise)
    }

    @Test fun returningAfterHoursCannotTriggerAnOverdueReminderBeforeTheWatcherTick() {
        val old = sit(SittingState(), 0, 13)
        val returnedAt = t0 + min(190)
        assertEquals(0, SittingRules.reminderDue(s, old, returnedAt))
        val current = SittingRules.seen(s, old, returnedAt)
        assertEquals(0, SittingRules.minutes(current, returnedAt))
        assertEquals(0, SittingRules.reminderDue(s, current, returnedAt))
        assertFalse(current.praise)
    }

    @Test fun returningAfterARemindedSessionOnlyPraisesAndStartsAfresh() {
        val old = SittingRules.reminded(sit(SittingState(), 0, 40), 1)
        val returnedAt = t0 + min(190)
        val current = SittingRules.seen(s, old, returnedAt)
        assertEquals(0, SittingRules.reminderDue(s, current, returnedAt))
        assertTrue(current.praise)
        val spoken = SittingRules.praised(current, current.startMs)
        assertFalse(SittingRules.seen(s, spoken, returnedAt + 1000).praise)
    }

    @Test fun speechResponseCannotMarkANewerSessionAsRemindedOrPraised() {
        val old = SittingRules.reminded(sit(SittingState(), 0, 40), 1)
        val current = SittingRules.seen(s, old, t0 + min(190))
        assertEquals(current, SittingRules.reminded(current, 2, old.startMs))
        assertEquals(current, SittingRules.praised(current, old.startMs))
        val nextTick = SittingRules.seen(s, old, t0 + min(41))
        assertEquals(nextTick.copy(level = 2), SittingRules.reminded(nextTick, 2, old.startMs))
    }

    @Test fun minutesAreWholeMinutes() {
        assertEquals(0, SittingRules.minutes(SittingState(), t0))
        assertEquals(41, SittingRules.minutes(SittingState(startMs = t0), t0 + min(41) + 59_000))
    }

    @Test fun durationsAreSaidInRussian() {
        assertEquals("40 минут", SittingTexts.duration(40))
        assertEquals("41 минуту", SittingTexts.duration(41))
        assertEquals("22 минуты", SittingTexts.duration(22))
        assertEquals("11 минут", SittingTexts.duration(11))
        assertEquals("1 час", SittingTexts.duration(60))
        assertEquals("1 час 5 минут", SittingTexts.duration(65))
        assertEquals("2 часа 21 минуту", SittingTexts.duration(141))
        assertEquals("5 часов", SittingTexts.duration(300))
        assertEquals("0 минут", SittingTexts.duration(0))
    }

    @Test fun theRemindersNameThePersonAndTheTime() {
        assertEquals("Женя, ты сидишь уже 40 минут. Встань, разомнись или пройдись.", SittingTexts.reminder(1, "Женя", 40))
        assertEquals("Женя, уже 1 час без перерыва. Пожалуйста, встань и отдохни.", SittingTexts.reminder(2, "Женя", 60))
    }

    @Test fun aCellKeepsTheLongestSessionAndTheSumOfPresence() {
        val cell = SittingCell().add(5, 12, 0).add(5, 30, 1).add(4, 20, 0)
        assertEquals(SittingCell(presentS = 14, streakMin = 30, level = 1), cell)
    }
}
