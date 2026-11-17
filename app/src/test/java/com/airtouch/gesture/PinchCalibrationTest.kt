package com.airtouch.gesture

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PinchCalibrationTest {
    @Test fun needsEnoughSamples() {
        val c = PinchCalibration()
        repeat(10) { c.add(0.1f) }
        assertNull(c.result())
    }

    @Test fun tightPinchLowersScale() {
        val c = PinchCalibration()
        repeat(30) { c.add(0.10f) }
        val s = c.result()
        assertNotNull(s)
        assertTrue("escala $s", s!! < 1f)
    }

    @Test fun loosePinchRaisesScale() {
        val c = PinchCalibration()
        repeat(30) { c.add(0.26f) }
        assertTrue(c.result()!! > 1f)
    }

    @Test fun ignoresImplausibleSamples() {
        val c = PinchCalibration()
        repeat(40) { c.add(0.9f) }
        assertEquals(0, c.count)
    }
}
