package com.airtouch.games

import com.airtouch.gesture.Gesture
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GameControllerTest {
    @Test fun noHand() {
        val i = GameController.map(null, Gesture.NONE)
        assertNull(i.x); assertEquals(0, i.dirX)
    }

    @Test fun centerIsDeadzone() {
        val i = GameController.map(0.55f to 0.45f, Gesture.POINTING)
        assertEquals(0, i.dirX); assertEquals(0, i.dirY); assertFalse(i.action)
    }

    @Test fun directionsAndAction() {
        val i = GameController.map(0.9f to 0.1f, Gesture.PINCH)
        assertEquals(1, i.dirX); assertEquals(-1, i.dirY); assertTrue(i.action)
    }
}
