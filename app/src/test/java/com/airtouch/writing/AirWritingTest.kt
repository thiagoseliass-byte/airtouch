package com.airtouch.writing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AirWritingTest {
    @Test fun penDownAndUpMakesAStroke() {
        val w = AirWriting()
        for (i in 0..5) w.update(true, 0.1f * i, 0.1f, i * 10L)
        assertTrue(w.penDown)
        w.update(false, 0f, 0f, 100L)
        assertFalse(w.penDown)
        assertEquals(1, w.snapshot().size)
        assertEquals(100L, w.lastPenUpMs)
    }

    @Test fun undoAndClear() {
        val w = AirWriting()
        repeat(2) { s ->
            for (i in 0..3) w.update(true, 0.1f * i, 0.1f * s, i * 10L)
            w.update(false, 0f, 0f, 50L)
        }
        assertEquals(2, w.snapshot().size)
        w.undo()
        assertEquals(1, w.snapshot().size)
        w.clear()
        assertTrue(w.isEmpty())
    }
}
