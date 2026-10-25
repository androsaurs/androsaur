package dev.hram.androsaur.hub

import dev.hram.androsaur.face.Expression
import dev.hram.androsaur.face.Reflexes
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class HubSettingsTest {
    private val dim = HubSettings.DIM_AFTER

    @Test fun withoutTheHubTheDefaultHolds() = assertEquals(30, dim.resolve(null))

    @Test fun aValueFromTheHubIsUsedOnlyInRange() {
        assertEquals(45, dim.resolve(45))
        assertEquals(30, dim.resolve(1))
        assertEquals(30, dim.resolve(100_000))
    }

    @Test fun theTimeoutFromTheHubDecidesTheDimming() {
        val dimAfterMs = dim.resolve(60) * 1000
        assertFalse(Reflexes.dimmed(45_000, lastActivityAt = 0, dimAfterMs = dimAfterMs))
        assertTrue(Reflexes.dimmed(61_000, lastActivityAt = 0, dimAfterMs = dimAfterMs))
        assertEquals(Expression.SAD, Reflexes.expression(45_000, null, faceSeen = false, lastActivityAt = 0, watching = true, dimAfterMs = dimAfterMs))
        assertEquals(Expression.SLEEPY, Reflexes.expression(61_000, null, faceSeen = false, lastActivityAt = 0, watching = true, dimAfterMs = dimAfterMs))
    }
}
