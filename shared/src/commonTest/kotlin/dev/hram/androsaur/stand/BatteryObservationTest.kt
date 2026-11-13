package dev.hram.androsaur.stand
import kotlin.test.*
class BatteryObservationTest {
    @Test fun normalisesScaleAndKeepsChargingSeparate() {
        val value = BatteryObservation.from(1, 100, 160, 200, false)!!
        assertEquals(80, value.percent)
        assertFalse(value.charging)
        assertEquals(25, value.age(125))
        assertTrue(BatteryObservation.from(2, 200, 0, 100, true)!!.charging)
    }
    @Test fun unknownAndInvalidReadingsAreNotZeroPercent() {
        assertNull(BatteryObservation.from(1, 100, -1, 100, true))
        assertNull(BatteryObservation.from(1, 100, 50, 0, true))
        assertNull(BatteryObservation.from(1, 100, 101, 100, false))
        assertNull(BatteryObservation.from(1, 100, 50, 100, null))
        assertEquals(100, BatteryObservation.from(1, 100, Int.MAX_VALUE, Int.MAX_VALUE, false)!!.percent)
    }
}
