package com.airtouch.control

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TwoHandZoomTest {
    private fun two(gap: Float) = listOf(TestHands.palm(0.5f - gap / 2), TestHands.palm(0.5f + gap / 2))

    @Test fun oneHandNeverZooms() {
        val z = TwoHandZoom()
        repeat(10) { assertEquals(1f, z.update(listOf(TestHands.palm()), 100, 100), 0f) }
        assertFalse(z.active)
    }

    @Test fun spreadingHandsZoomsIn() {
        val z = TwoHandZoom()
        repeat(6) { z.update(two(0.3f), 100, 100) }
        assertTrue(z.active)
        var product = 1f
        for (i in 0..10) product *= z.update(two(0.3f + 0.03f * i), 100, 100)
        assertTrue("fator $product", product > 1.1f)
    }

    @Test fun closingHandsZoomsOut() {
        val z = TwoHandZoom()
        repeat(6) { z.update(two(0.6f), 100, 100) }
        var product = 1f
        for (i in 0..10) product *= z.update(two(0.6f - 0.03f * i), 100, 100)
        assertTrue("fator $product", product < 0.9f)
    }

    @Test fun endsAfterHandsDisappear() {
        val z = TwoHandZoom()
        repeat(6) { z.update(two(0.3f), 100, 100) }
        repeat(4) { z.update(emptyList(), 100, 100) }
        assertFalse(z.active)
    }
}
