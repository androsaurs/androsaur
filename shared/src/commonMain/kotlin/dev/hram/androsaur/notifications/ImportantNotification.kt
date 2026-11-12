package dev.hram.androsaur.notifications

import dev.hram.androsaur.medication.MedicationSchedule

enum class NotificationKind { MEDICATION }
enum class NotificationStatus { PENDING, CONFIRMED }

/** Stable identity binds acknowledgement to a person and one scheduled intake, not to spoken text. */
data class ImportantNotification(
    val id: String,
    val personId: String,
    val slot: String,
    val kind: NotificationKind,
    val status: NotificationStatus = NotificationStatus.PENDING,
    val title: String,
) {
    fun confirmed() = copy(status = NotificationStatus.CONFIRMED, title = "Приём отмечен")
}

object MedicationNotifications {
    /** Derived from persistent reminder/taken marks: an already spoken reminder survives app recreation. */
    fun pending(personId: String?, name: String, present: Boolean, times: List<String>, day: Long,
                minute: Int, reminded: String?, taken: String?): ImportantNotification? {
        if (!present || personId.isNullOrBlank() || name.isBlank()) return null
        val slot = MedicationSchedule.windowOpen(times, day, minute, reminded, taken) ?: return null
        return ImportantNotification("medication:$personId:$slot", personId, slot, NotificationKind.MEDICATION,
            title = "$name, прими лекарство")
    }

    /** A stale voice context must not confirm the previous person's intake after another person arrives. */
    fun confirmableSlot(personId: String, visiblePerson: String?, present: Boolean, times: List<String>,
                        day: Long, minute: Int, reminded: String?, taken: String?): String? {
        if (!present || personId.isBlank() || personId != visiblePerson) return null
        return MedicationSchedule.windowOpen(times, day, minute, reminded, taken)
    }
}
