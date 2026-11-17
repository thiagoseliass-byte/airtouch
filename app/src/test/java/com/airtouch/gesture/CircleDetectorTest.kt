package com.airtouch.gesture

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

class CircleDetectorTest {
    @Test fun fullCircleFires() {
        val d = CircleDetector()
        var fired = false
        for (i in 0..30) {
            val a = 2 * PI * i / 28
            if (d.update(0.5f + 0.12f * cos(a).toFloat(), 0.5f + 0.12f * sin(a).toFloat(), i * 40L)) fired = true
        }
        assertTrue(fired)
    }

    @Test fun straightLineDoesNotFire() {
        val d = CircleDetector()
        var fired = false
        for (i in 0..30) if (d.update(0.2f + 0.02f * i, 0.5f, i * 40L)) fired = true
        assertFalse(fired)
    }

    @Test fun tinyJitterDoesNotFire() {
        val d = CircleDetector()
        var fired = false
        for (i in 0..40) {
            val a = 2 * PI * i / 10
            if (d.update(0.5f + 0.005f * cos(a).toFloat(), 0.5f + 0.005f * sin(a).toFloat(), i * 30L)) fired = true
        }
        assertFalse(fired)
    }
}
