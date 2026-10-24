package dev.hram.androsaur.face

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ReflexesTest {
    @Test fun wakeWordWinsForAFewSeconds() {
        assertEquals(Expression.LISTENING, Reflexes.expression(10_000, lastWakeAt = 8_000, faceSeen = true, lastActivityAt = 0))
        assertEquals(Expression.ATTENTIVE, Reflexes.expression(12_500, lastWakeAt = 8_000, faceSeen = true, lastActivityAt = 0))
    }

    @Test fun faceMakesItAttentiveAndSilenceMakesItSleepy() {
        assertEquals(Expression.ATTENTIVE, Reflexes.expression(5_000, null, faceSeen = true, lastActivityAt = 0))
        assertEquals(Expression.NEUTRAL, Reflexes.expression(5_000, null, faceSeen = false, lastActivityAt = 0))
        assertEquals(Expression.SLEEPY, Reflexes.expression(200_000, null, faceSeen = false, lastActivityAt = 0))
        assertEquals(Expression.ATTENTIVE, Reflexes.expression(200_000, null, faceSeen = true, lastActivityAt = 0))
    }

    @Test fun aWakeWordInTheFutureOfTheClockIsIgnored() =
        assertEquals(Expression.NEUTRAL, Reflexes.expression(1_000, lastWakeAt = 5_000, faceSeen = false, lastActivityAt = 0))

    @Test fun gazeIsAMirror() {
        val (leftX, _) = Reflexes.gaze(0.1, 0.5)   // person on the left of the camera picture
        assertTrue(leftX > 0.5, "looks to the right of the screen")
        assertEquals(0.0, Reflexes.gaze(0.5, 0.5).first, 1e-9)
        assertTrue(Reflexes.gaze(0.5, 0.9).second > 0.5, "lower in the picture means down")
        assertEquals(1.0, Reflexes.gaze(-3.0, 0.5).first, 1e-9)
    }

    @Test fun driftMovesEveryPeriodAndStaysInRange() {
        val seen = (0 until 10).map { Reflexes.drift(it * 30_000L) }.toSet()
        assertTrue(seen.size >= 4)
        seen.forEach { assertTrue(it.first in -1.0..1.0 && it.second in -1.0..1.0) }
        assertEquals(Reflexes.drift(1_000), Reflexes.drift(29_000))
    }
}
