package dev.hram.androsaur.face

/** What the face on the screen shows. */
enum class Expression { NEUTRAL, ATTENTIVE, LISTENING, SLEEPY, SAD }

/**
 * The "reflexes": quick rules that turn what the modules report into an expression and a gaze, with no LLM in between.
 * Times are milliseconds on one clock (the caller's).
 */
object Reflexes {
    const val LISTENING_FOR_MS = 3_000L
    const val SLEEPY_AFTER_MS = 120_000L
    const val DIM_AFTER_MS = 30_000L
    const val DIM_LEVEL = 0.05f

    /**
     * The wake word wins for a few seconds, then a visible face makes the face attentive, a long silence makes it sleepy.
     * While it is [watching] and nobody is there it is sad until the screen dims (after [dimAfterMs]), then it falls asleep; when it
     * is not watching (no camera) it has no reason to be sad.
     */
    fun expression(now: Long, lastWakeAt: Long?, faceSeen: Boolean, lastActivityAt: Long, watching: Boolean = false, dimAfterMs: Long = DIM_AFTER_MS): Expression = when {
        lastWakeAt != null && now - lastWakeAt in 0..LISTENING_FOR_MS -> Expression.LISTENING
        faceSeen -> Expression.ATTENTIVE
        watching -> if (dimmed(now, lastActivityAt, dimAfterMs)) Expression.SLEEPY else Expression.SAD
        now - lastActivityAt > SLEEPY_AFTER_MS -> Expression.SLEEPY
        else -> Expression.NEUTRAL
    }

    /** Whether the screen is dimmed to [DIM_LEVEL]: nothing happened (no face, voice or touch) for [dimAfterMs] (the hub can change it). */
    fun dimmed(now: Long, lastActivityAt: Long, dimAfterMs: Long = DIM_AFTER_MS): Boolean = now - lastActivityAt > dimAfterMs

    const val FACE_GONE_MS = 4_000L

    /** Whether somebody is in front of the camera: a face was seen within [FACE_GONE_MS] (detections flicker, so a short gap is not a departure). A face stamped a moment after [now] was taken (another thread) still counts as present. */
    fun present(now: Long, lastFaceAt: Long?): Boolean = lastFaceAt != null && now - lastFaceAt <= FACE_GONE_MS

    /**
     * Whether the name of the person in front of the camera is shown: from the recognition until they leave
     * (no face for [FACE_GONE_MS]). It never shows without a recognition.
     */
    fun badgeVisible(now: Long, recognizedAt: Long?, lastFaceAt: Long?): Boolean = recognizedAt != null && present(now, lastFaceAt)

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
