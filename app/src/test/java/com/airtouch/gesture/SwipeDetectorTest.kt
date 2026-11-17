package com.airtouch.gesture

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SwipeDetectorTest {

    private fun run(
        detector: SwipeDetector,
        fromX: Float, toX: Float,
        fromY: Float, toY: Float,
        startMs: Long, durationMs: Long, stepMs: Long = 50,
    ): List<SwipeDirection> {
        val found = mutableListOf<SwipeDirection>()
        var t = 0L
        while (t <= durationMs) {
            val f = t.toFloat() / durationMs
            detector.update(
                fromX + (toX - fromX) * f,
                fromY + (toY - fromY) * f,
                startMs + t,
            )?.let { found += it }
            t += stepMs
        }
        return found
    }

    @Test fun fastMoveRight() =
        assertEquals(listOf(SwipeDirection.RIGHT), run(SwipeDetector(), 0.2f, 0.9f, 0.5f, 0.5f, 0, 300))

    @Test fun fastMoveLeft() =
        assertEquals(listOf(SwipeDirection.LEFT), run(SwipeDetector(), 0.9f, 0.2f, 0.5f, 0.5f, 0, 300))

    @Test fun fastMoveUp() =
        assertEquals(listOf(SwipeDirection.UP), run(SwipeDetector(), 0.5f, 0.5f, 0.9f, 0.2f, 0, 300))

    @Test fun fastMoveDown() =
        assertEquals(listOf(SwipeDirection.DOWN), run(SwipeDetector(), 0.5f, 0.5f, 0.2f, 0.9f, 0, 300))

    @Test fun slowMoveIsIgnored() =
        assertEquals(emptyList<SwipeDirection>(), run(SwipeDetector(), 0.2f, 0.8f, 0.5f, 0.5f, 0, 3000))

    @Test fun diagonalMoveIsIgnored() =
        assertEquals(emptyList<SwipeDirection>(), run(SwipeDetector(), 0.3f, 0.8f, 0.3f, 0.8f, 0, 300))

    @Test fun cooldownBlocksImmediateSecondSwipe() {
        val d = SwipeDetector()
        assertEquals(1, run(d, 0.2f, 0.9f, 0.5f, 0.5f, 0, 300).size)
        // Logo depois (dentro do tempo de espera) não dispara de novo.
        assertEquals(0, run(d, 0.9f, 0.2f, 0.5f, 0.5f, 400, 300).size)
        // Depois do tempo de espera, dispara.
        assertEquals(1, run(d, 0.2f, 0.9f, 0.5f, 0.5f, 3000, 300).size)
    }

    @Test fun needsThreeSamples() {
        val d = SwipeDetector()
        assertNull(d.update(0.1f, 0.5f, 0))
        assertNull(d.update(0.9f, 0.5f, 50))
    }
}
