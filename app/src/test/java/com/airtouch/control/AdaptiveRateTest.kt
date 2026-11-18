package com.airtouch.control

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AdaptiveRateTest {
    @Test fun fullSpeedWithHand() {
        val r = AdaptiveRate()
        r.onResult(true, 1000)
        assertTrue((1001..1020L).all { r.shouldProcess(it) })
    }

    @Test fun idleThrottlesAfterNoHand() {
        val r = AdaptiveRate()
        r.onResult(true, 0)
        val processed = (4000..5000L step 33).count { r.shouldProcess(it) }
        assertTrue("processou $processed", processed in 3..8)
    }

    @Test fun powerSaverLimitsRate() {
        val r = AdaptiveRate()
        r.powerSaver = true
        r.onResult(true, 0)
        val processed = (100..1100L step 10).count { r.onResult(true, it).let { _ -> r.shouldProcess(it) } }
        assertTrue("processou $processed", processed in 12..17)
    }

    @Test fun resumesFullSpeedWhenHandReturns() {
        val r = AdaptiveRate()
        r.onResult(true, 0)
        r.shouldProcess(5000)
        r.onResult(true, 5100)
        assertTrue(r.shouldProcess(5101))
        assertTrue(r.shouldProcess(5102))
        assertFalse(r.idle)
    }
}
