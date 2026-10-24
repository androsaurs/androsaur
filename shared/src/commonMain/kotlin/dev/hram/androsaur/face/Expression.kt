package dev.hram.androsaur.face

/** What the face on the screen shows. */
enum class Expression { NEUTRAL, ATTENTIVE, LISTENING, SLEEPY }

/**
 * The "reflexes": quick rules that turn what the modules report into an expression and a gaze, with no LLM in between.
 * Times are milliseconds on one clock (the caller's).
 */
object Reflexes {
    const val LISTENING_FOR_MS = 3_000L
    const val SLEEPY_AFTER_MS = 120_000L

    /** The wake word wins for a few seconds, then a visible face makes the face attentive, a long silence makes it sleepy. */
    fun expression(now: Long, lastWakeAt: Long?, faceSeen: Boolean, lastActivityAt: Long): Expression = when {
        lastWakeAt != null && now - lastWakeAt in 0..LISTENING_FOR_MS -> Expression.LISTENING
        faceSeen -> Expression.ATTENTIVE
        now - lastActivityAt > SLEEPY_AFTER_MS -> Expression.SLEEPY
        else -> Expression.NEUTRAL
    }

    const val BADGE_MS = 10_000L
    const val FACE_GONE_MS = 4_000L

    /**
     * Whether the name of the person who woke the assistant is shown. It appears when the camera has recognized them and stays while the
     * conversation goes on: [BADGE_MS] after the later of the recognition and the last wake word. It goes at once when the person is not
     * in front of the camera any more (no face for [FACE_GONE_MS]), and never shows without a recognition.
     */
    fun badgeVisible(now: Long, recognizedAt: Long?, lastWakeAt: Long?, lastFaceAt: Long?): Boolean {
        if (recognizedAt == null || lastFaceAt == null) return false
        if (now - lastFaceAt > FACE_GONE_MS) return false
        val last = maxOf(recognizedAt, lastWakeAt ?: Long.MIN_VALUE)
        return now - last in 0..BADGE_MS
    }

    /**
     * Where the eyes look, -1..1 on each axis (x to the right of the screen, y down), for a face whose centre is at
     * ([cx], [cy]) as fractions 0..1 of the unmirrored camera image. The screen is a mirror: a person on the left of the picture
     * is on the right of the screen, so x is flipped.
     */
    fun gaze(cx: Double, cy: Double): Pair<Double, Double> =
        ((0.5 - cx) * 2).coerceIn(-1.0, 1.0) to ((cy - 0.5) * 2).coerceIn(-1.0, 1.0)

    /**
     * Burn-in protection for an always-on screen: the picture sits in one of a few places and moves to the next one every
     * [periodMs]. Returns the offset in the range -1..1 on each axis; the caller multiplies it by the room it has.
     */
    fun drift(now: Long, periodMs: Long = 30_000L): Pair<Double, Double> {
        val steps = listOf(0.0 to 0.0, 1.0 to 0.5, -0.5 to 1.0, -1.0 to -0.5, 0.5 to -1.0)
        return steps[((now / periodMs) % steps.size).toInt()]
    }
}
