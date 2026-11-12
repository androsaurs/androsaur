package dev.hram.androsaur.health

/** Minutes, from the hub: the first reminder after [firstMin] of sitting, the final one [repeatMin] later, a break of [breakMin] starts afresh. */
data class SittingSettings(val firstMin: Int, val repeatMin: Int, val breakMin: Int)

/**
 * One person's current sitting session. [startMs]/[lastSeenMs] are wall-clock milliseconds (0 — no session yet), [level] is how
 * many reminders were given in it (0, 1 first, 2 final: the character is tired, then exhausted) and [praise] says «Молодец,
 * отдохнул» is still to be said (the person came back after a break while tired).
 */
data class SittingState(val startMs: Long = 0, val lastSeenMs: Long = 0, val level: Int = 0, val praise: Boolean = false)

/** The rules of «don't sit for hours»; pure, so the clock is an argument. */
object SittingRules {
    private const val MINUTE = 60_000L

    /**
     * The person is in front of the camera at [now]. Absence shorter than a break does not end the session; after a longer one a fresh
     * session starts and the character is itself again at once. A person who left tired is praised when they are back.
     */
    fun seen(settings: SittingSettings, state: SittingState, now: Long): SittingState {
        if (state.startMs == 0L || now - state.lastSeenMs >= settings.breakMin * MINUTE) {
            val rested = state.startMs != 0L && state.level >= 1
            return SittingState(startMs = now, lastSeenMs = now, level = 0, praise = state.praise || rested)
        }
        return state.copy(lastSeenMs = now)
    }

    /** The level of the reminder to give now (1 first, 2 final), or 0 when none is due: the session reached a threshold not reminded yet. */
    fun reminderDue(settings: SittingSettings, state: SittingState, now: Long): Int {
        // A caller may run before the presence watcher has processed a return from a break.
        if (state.startMs == 0L || now - state.lastSeenMs >= settings.breakMin * MINUTE) return 0
        val minutes = (now - state.startMs) / MINUTE
        val target = when {
            minutes >= settings.firstMin + settings.repeatMin -> 2
            minutes >= settings.firstMin -> 1
            else -> 0
        }
        return if (target > state.level) target else 0
    }

    /** An HTTP speech response may arrive after the watcher has started a new session. */
    fun reminded(state: SittingState, level: Int, sessionStartMs: Long = state.startMs) =
        if (state.startMs == sessionStartMs) state.copy(level = maxOf(state.level, level)) else state

    fun praised(state: SittingState, sessionStartMs: Long = state.startMs) =
        if (state.startMs == sessionStartMs) state.copy(praise = false) else state

    /** Whole minutes the session has lasted. */
    fun minutes(state: SittingState, now: Long): Int = if (state.startMs == 0L) 0 else ((now - state.startMs) / MINUTE).toInt()
}

/** The words of the reminders. */
object SittingTexts {
    const val RESTED = "Молодец, отдохнул."

    fun reminder(level: Int, name: String, minutes: Int): String = when (level) {
        1 -> "$name, ты сидишь уже ${duration(minutes)}. Встань, разомнись или пройдись."
        else -> "$name, уже ${duration(minutes)} без перерыва. Пожалуйста, встань и отдохни."
    }

    /** «40 минут», «41 минуту», «1 час 5 минут», «2 часа». */
    fun duration(minutes: Int): String {
        val hours = minutes / 60
        val rest = minutes % 60
        val parts = buildList {
            if (hours > 0) add("$hours ${plural(hours, "час", "часа", "часов")}")
            if (rest > 0 || hours == 0) add("$rest ${plural(rest, "минуту", "минуты", "минут")}")
        }
        return parts.joinToString(" ")
    }

    private fun plural(n: Int, one: String, few: String, many: String): String = when {
        n % 100 in 11..14 -> many
        n % 10 == 1 -> one
        n % 10 in 2..4 -> few
        else -> many
    }
}

/** 15-minute cells of the heat map: where a moment falls and what a cell holds so far. */
data class SittingCell(val presentS: Int = 0, val streakMin: Int = 0, val level: Int = 0) {
    /** Adds [seconds] of presence at a session of [streak] minutes and reminder [level]. */
    fun add(seconds: Int, streak: Int, level: Int) =
        SittingCell(presentS + seconds, maxOf(streakMin, streak), maxOf(this.level, level))
}
