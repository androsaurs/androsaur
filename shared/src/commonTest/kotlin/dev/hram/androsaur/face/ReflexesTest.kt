package dev.hram.androsaur.face

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
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

    @Test fun nobodyThereMakesItSadOnlyWhileWatching() {
        assertEquals(Expression.SAD, Reflexes.expression(5_000, null, faceSeen = false, lastActivityAt = 0, watching = true))
        assertEquals(Expression.NEUTRAL, Reflexes.expression(5_000, null, faceSeen = false, lastActivityAt = 0, watching = false))
        assertEquals(Expression.ATTENTIVE, Reflexes.expression(5_000, null, faceSeen = true, lastActivityAt = 0, watching = true))
        assertEquals(Expression.SAD, Reflexes.expression(200_000, null, faceSeen = false, lastActivityAt = 0, watching = true))
    }

    @Test fun longLonelinessDimsTheScreenAndPutsItToSleep() {
        assertFalse(Reflexes.dimmed(Reflexes.DIM_AFTER_MS, lastActivityAt = 0))
        assertTrue(Reflexes.dimmed(Reflexes.DIM_AFTER_MS + 1, lastActivityAt = 0))
        assertEquals(Expression.SLEEPY, Reflexes.expression(Reflexes.DIM_AFTER_MS + 1, null, faceSeen = false, lastActivityAt = 0, watching = true))
    }

    @Test fun aPersonBringsTheBrightnessBack() {
        val now = Reflexes.DIM_AFTER_MS * 2
        assertFalse(Reflexes.dimmed(now, lastActivityAt = now - 100))
        assertEquals(Expression.ATTENTIVE, Reflexes.expression(now, null, faceSeen = true, lastActivityAt = now - 100, watching = true))
    }

    @Test fun aShortGapInDetectionIsNotADeparture() {
        assertTrue(Reflexes.present(1_000, lastFaceAt = 500))
        assertTrue(Reflexes.present(4_500, lastFaceAt = 500))
        assertFalse(Reflexes.present(4_501, lastFaceAt = 500))
        assertTrue(Reflexes.present(1_000, lastFaceAt = 1_030), "stamped just after the clock was read")
        assertFalse(Reflexes.present(1_000, lastFaceAt = null))
    }

    @Test fun theNameStaysWhileThePersonIsThereAndGoesWhenTheyLeave() {
        assertTrue(Reflexes.badgeVisible(2_000, recognizedAt = 1_500, lastFaceAt = 1_900))
        assertTrue(Reflexes.badgeVisible(600_000, recognizedAt = 1_500, lastFaceAt = 599_900))
        assertFalse(Reflexes.badgeVisible(6_000, recognizedAt = 1_500, lastFaceAt = 1_900))
        assertFalse(Reflexes.badgeVisible(5_000, recognizedAt = 1_500, lastFaceAt = null))
    }

    @Test fun theNameNeverShowsUnrecognized() =
        assertFalse(Reflexes.badgeVisible(5_000, recognizedAt = null, lastFaceAt = 4_900))

    @Test fun driftMovesEveryPeriodAndStaysInRange() {
        val seen = (0 until 10).map { Reflexes.drift(it * 30_000L) }.toSet()
        assertTrue(seen.size >= 4)
        seen.forEach { assertTrue(it.first in -1.0..1.0 && it.second in -1.0..1.0) }
        assertEquals(Reflexes.drift(1_000), Reflexes.drift(29_000))
    }
}
