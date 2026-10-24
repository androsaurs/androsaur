package dev.hram.androsaur.delivery

/** A voice note that waits for somebody, as the audio module lists it. */
data class WaitingNote(val id: String, val to: String, val createdAtMs: Long)

object Delivery {
    /** The audio module compares recipients this way: lower case, ё as е, single spaces. */
    fun normalize(text: String): String = text.lowercase().replace('ё', 'е').trim().split(Regex("\\s+")).filter { it.isNotEmpty() }.joinToString(" ")

    /**
     * What a note for this person may be addressed to: the name the camera knows them by, and every role that [aliases]
     * (role to name, e.g. «жена» to «Маша») gives to that name.
     */
    fun addressedTo(name: String, aliases: Map<String, String>): Set<String> {
        val me = normalize(name)
        return setOf(me) + aliases.filter { normalize(it.value) == me }.keys.map { normalize(it) }
    }

    /** The notes for the person who is in front of the camera, oldest first. */
    fun forPerson(waiting: List<WaitingNote>, name: String, aliases: Map<String, String>): List<WaitingNote> {
        val mine = addressedTo(name, aliases)
        return waiting.filter { normalize(it.to) in mine }.sortedBy { it.createdAtMs }
    }
}

/**
 * Decides when to look for notes for a recognised person, so the module is not asked every quarter of a second and a note that
 * could not be played to the end is not retried in a loop.
 */
class DeliveryGate(private val idleCheckMs: Long = 3_000, private val afterDeliveryMs: Long = 20_000) {
    private val nextAt = mutableMapOf<String, Long>()

    fun shouldCheck(name: String, nowMs: Long): Boolean = nowMs >= (nextAt[Delivery.normalize(name)] ?: 0L)

    /** A look found nothing. */
    fun checkedEmpty(name: String, nowMs: Long) { nextAt[Delivery.normalize(name)] = nowMs + idleCheckMs }

    /** Delivery was tried (played to the end or interrupted). */
    fun delivered(name: String, nowMs: Long) { nextAt[Delivery.normalize(name)] = nowMs + afterDeliveryMs }

    /** The person went away: the next arrival is checked at once. */
    fun left(name: String) { nextAt.remove(Delivery.normalize(name)) }
}
