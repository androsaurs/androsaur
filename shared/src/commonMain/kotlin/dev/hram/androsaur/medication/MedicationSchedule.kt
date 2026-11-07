package dev.hram.androsaur.medication

/**
 * Medicine times of one person. The times of the day (09:00, 14:00, 20:00; any number) cut the day into intervals, and an interval
 * flows into the next day: with 08:00 and 18:00 the interval «from 18:00» lasts until 08:00 tomorrow. One interval, one reminder.
 * Days are epoch days (days since 1970-01-01, local), time is minutes of the day, so no date library is needed. The same rules as
 * the hub's (`app/medication.py`); the key of an interval is the local start «YYYY-MM-DDTHH:MM», the same on both sides.
 */
object MedicationSchedule {
    /** Minutes of the day for «HH:MM» (also «H:MM»), or null when it is not a time of day. */
    fun minutes(time: String): Int? {
        val parts = time.trim().split(":")
        if (parts.size != 2) return null
        val h = parts[0].toIntOrNull() ?: return null
        val m = parts[1].toIntOrNull() ?: return null
        return if (parts[1].length == 2 && h in 0..23 && m in 0..59) h * 60 + m else null
    }

    /** The start of the interval running now: the latest time not after now, yesterday's last one before today's first. Null without times. */
    fun intervalStart(times: List<String>, day: Long, minute: Int): Pair<Long, Int>? {
        val at = times.mapNotNull { minutes(it) }.distinct().sorted()
        if (at.isEmpty()) return null
        at.lastOrNull { it <= minute }?.let { return day to it }
        return (day - 1) to at.last()
    }

    fun key(start: Pair<Long, Int>): String {
        val (y, m, d) = civil(start.first)
        fun two(n: Int) = n.toString().padStart(2, '0')
        return "${y.toString().padStart(4, '0')}-${two(m)}-${two(d)}T${two(start.second / 60)}:${two(start.second % 60)}"
    }

    /** The interval key when a reminder is due now: an interval is running and no reminder has been given in it. */
    fun reminderDue(times: List<String>, day: Long, minute: Int, reminded: String?): String? =
        intervalStart(times, day, minute)?.let(::key)?.takeIf { it != reminded }

    /**
     * The open window: a reminder was given in the interval running now and the person has not said «принял» yet. «Принял» counts
     * only here; a confirmation in another interval, or without a reminder, is nothing.
     */
    fun windowOpen(times: List<String>, day: Long, minute: Int, reminded: String?, taken: String?): String? =
        intervalStart(times, day, minute)?.let(::key)?.takeIf { it == reminded && it != taken }

    /** Year, month, day of an epoch day (Hinnant's civil-from-days). */
    private fun civil(epochDay: Long): Triple<Int, Int, Int> {
        val z = epochDay + 719468
        val era = (if (z >= 0) z else z - 146096) / 146097
        val doe = z - era * 146097
        val yoe = (doe - doe / 1460 + doe / 36524 - doe / 146096) / 365
        val doy = doe - (365 * yoe + yoe / 4 - yoe / 100)
        val mp = (5 * doy + 2) / 153
        val d = (doy - (153 * mp + 2) / 5 + 1).toInt()
        val m = (if (mp < 10) mp + 3 else mp - 9).toInt()
        val y = (yoe + era * 400 + if (m <= 2) 1 else 0).toInt()
        return Triple(y, m, d)
    }
}
