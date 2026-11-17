package com.airtouch.control

import com.airtouch.gesture.Gesture
import com.airtouch.gesture.SwipeDirection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GestureActionDispatcherTest {

    @Test fun thumbsMapToConfirmAndCancel() {
        val d = GestureActionDispatcher()
        assertEquals(AirAction.Confirm, d.onStableGesture(Gesture.THUMB_UP))
        assertEquals(AirAction.Cancel, d.onStableGesture(Gesture.THUMB_DOWN))
    }

    @Test fun swipesMapToSlidesAndScroll() {
        val d = GestureActionDispatcher()
        assertEquals(AirAction.NextSlide, d.onSwipe(SwipeDirection.RIGHT))
        assertEquals(AirAction.PrevSlide, d.onSwipe(SwipeDirection.LEFT))
        assertEquals(AirAction.ScrollUp, d.onSwipe(SwipeDirection.UP))
        assertEquals(AirAction.ScrollDown, d.onSwipe(SwipeDirection.DOWN))
    }

    @Test fun fistLocksAndOpenPalmUnlocks() {
        val d = GestureActionDispatcher()
        assertEquals(AirAction.Pause(true), d.onStableGesture(Gesture.FIST))
        assertTrue(d.locked)

        // Bloqueado: nada é executado.
        assertNull(d.onStableGesture(Gesture.THUMB_UP))
        assertNull(d.onSwipe(SwipeDirection.RIGHT))

        assertEquals(AirAction.Pause(false), d.onStableGesture(Gesture.OPEN_PALM))
        assertFalse(d.locked)
        assertEquals(AirAction.Confirm, d.onStableGesture(Gesture.THUMB_UP))
    }

    @Test fun openPalmWhileUnlockedDoesNothing() {
        assertNull(GestureActionDispatcher().onStableGesture(Gesture.OPEN_PALM))
    }
}
