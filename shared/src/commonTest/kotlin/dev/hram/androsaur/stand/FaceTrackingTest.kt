package dev.hram.androsaur.stand

import kotlin.test.*

class FaceTrackingTest {
    private fun box(cx: Double, cy: Double, size: Double = .1) = TrackingBox(cx - size / 2, cy - size / 2, size, size)
    @Test fun signedCoordinatesUseCentreAndUpwardY() {
        for ((point, expected) in listOf(box(.5, .5) to (0.0 to 0.0), box(.9, .1) to (.8 to .8), box(.1, .9) to (-.8 to -.8))) {
            val sample = assertNotNull(FaceTracking().ingest("one", 1000, listOf(point), 1000))
            assertEquals(expected.first, sample.x!!, 1e-6); assertEquals(expected.second, sample.y!!, 1e-6)
        }
    }
    @Test fun repeatedAndStaleFramesCannotIssueAnotherStep() {
        val t = FaceTracking()
        assertNotNull(t.ingest("a", 1000, listOf(box(.7, .5)), 1000))
        assertNull(t.ingest("a", 1000, listOf(box(.7, .5)), 1200))
        assertNotNull(t.lost(1501)).also { assertFalse(it.visible) }
        assertNull(t.lost(1600))
        assertNull(t.ingest("b", 1000, listOf(box(.7, .5)), 1601))
    }
    @Test fun lostFaceIsNullRatherThanArtificialZero() {
        val sample = assertNotNull(FaceTracking().ingest("a", 1000, emptyList(), 1000))
        assertFalse(sample.visible); assertNull(sample.x); assertNull(sample.y)
    }
    @Test fun targetDoesNotJumpWhenDetectorOrderChanges() {
        val t = FaceTracking()
        val first = assertNotNull(t.ingest("a", 1000, listOf(box(.2, .5, .2), box(.8, .5)), 1000))
        val next = assertNotNull(t.ingest("b", 1250, listOf(box(.8, .5, .3), box(.23, .5)), 1250))
        assertTrue(first.x!! < 0); assertTrue(next.x!! < 0)
        assertFalse(assertNotNull(t.ingest("c", 1500, listOf(box(.8, .5)), 1500)).visible)
    }
    @Test fun invalidAndFutureFramesCannotBecomeTargets() {
        assertFalse(assertNotNull(FaceTracking().ingest("a", 1000, listOf(TrackingBox(Double.NaN, .2, .1, .1)), 1000)).visible)
        assertNull(FaceTracking().ingest("future", 1200, listOf(box(.5, .5)), 1000))
        val t = FaceTracking()
        t.ingest("a", 1000, listOf(box(.7, .5)), 1000)
        assertFalse(assertNotNull(t.lost(1100, force = true)).visible)
    }
}
