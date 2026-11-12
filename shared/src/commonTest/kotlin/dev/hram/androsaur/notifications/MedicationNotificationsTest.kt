package dev.hram.androsaur.notifications

import kotlin.test.*

class MedicationNotificationsTest {
    private val day = 20769L // 2026-11-12
    private val times = listOf("06:00", "14:00", "20:00")
    private val slot = "2026-11-12T20:00"
    private fun pending(person: String? = "person-a", name: String = "Женя", present: Boolean = true,
                        atDay: Long = day, minute: Int = 21 * 60, reminded: String? = slot, taken: String? = null) =
        MedicationNotifications.pending(person, name, present, times, atDay, minute, reminded, taken)

    @Test fun restoresAlreadySpokenUnconfirmedReminderWithoutRepeatingSpeech() {
        val first = assertNotNull(pending())
        val afterRestart = assertNotNull(pending())
        assertEquals(first, afterRestart)
        assertEquals("Женя, прими лекарство", first.title)
        assertEquals(slot, first.slot)
        assertEquals(NotificationStatus.PENDING, first.status)
    }

    @Test fun hidesPersonalReminderWhileAwayAndRestoresItOnReturn() {
        assertNull(pending(present = false))
        assertNull(pending(person = null))
        assertNull(pending(name = ""))
        assertNotNull(pending())
    }

    @Test fun acknowledgementClosesExactlyThisIntake() {
        assertNull(pending(taken = slot))
        assertNotNull(pending(taken = "2026-11-12T14:00"))
        val confirmed = assertNotNull(pending()).confirmed()
        assertEquals(NotificationStatus.CONFIRMED, confirmed.status)
        assertEquals("Приём отмечен", confirmed.title)
        assertEquals(assertNotNull(pending()).id, confirmed.id)
    }

    @Test fun nextIntakeExpiresOldReminderWithoutMarkingItTaken() {
        assertNotNull(pending(atDay = day + 1, minute = 5 * 60 + 59))
        assertNull(pending(atDay = day + 1, minute = 6 * 60))
        assertNull(pending(minute = 19 * 60))
        assertNull(pending(reminded = null))
    }

    @Test fun identitiesDoNotMixPeopleOrScheduledIntakes() {
        val mine = assertNotNull(pending())
        assertNotEquals(mine.id, assertNotNull(pending(person = "person-b", name = "Другой")).id)
        assertNull(pending(person = "person-b", name = "Другой", reminded = null))
        assertNull(MedicationNotifications.pending("person-a", "Женя", true, emptyList(), day, 21 * 60, slot, null))
    }

    @Test fun voiceContextCannotConfirmPreviousPersonOrAbsentPerson() {
        fun confirm(requested: String, visible: String?, present: Boolean, taken: String? = null) =
            MedicationNotifications.confirmableSlot(requested, visible, present, times, day, 21 * 60, slot, taken)
        assertEquals(slot, confirm("person-a", "person-a", true))
        assertNull(confirm("person-a", "person-b", true))
        assertNull(confirm("person-a", null, true))
        assertNull(confirm("person-a", "person-a", false))
        assertNull(confirm("", "", true))
        assertNull(confirm("person-a", "person-a", true, slot))
    }
}
