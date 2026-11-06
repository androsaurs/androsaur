package dev.hram.androsaur.commands

import dev.hram.androsaur.weather.Greeting

/** The existing daily greeting owns delivery; this only decides its optional morning addition. */
object MorningHoroscope {
    fun due(greetingOn: Boolean, lastGreeted: String?, today: String, hour: Int): Boolean =
        greetingOn && hour in 5..11 && Greeting.due(lastGreeted, today)

    fun stillFor(person: String, currentPerson: String?, present: Boolean, interrupted: Boolean): Boolean =
        person == currentPerson && present && !interrupted

    /** Audio accepts at most 600 characters per utterance. Preserve the whole forecast. */
    fun parts(reading: DailyHoroscope?, person: String, nowMs: Long): List<String> {
        if (reading == null || !reading.greetingEnabled || !reading.usableFor(person, nowMs)) return emptyList()
        val result = mutableListOf<String>()
        var rest = reading.spoken().trim()
        while (rest.length > 600) {
            val cut = rest.lastIndexOf(' ', 600).takeIf { it > 0 } ?: 600
            result += rest.substring(0, cut)
            rest = rest.substring(cut).trimStart()
        }
        if (rest.isNotEmpty()) result += rest
        return result
    }
}
