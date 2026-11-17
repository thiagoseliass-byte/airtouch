package com.airtouch.control

import com.airtouch.gesture.Gesture
import com.airtouch.gesture.SwipeDirection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ProfileTest {
    @Test fun defaultsHaveFiveProfiles() = assertEquals(5, Profile.defaults().size)

    @Test fun readingProfileZoomsWithThumbs() {
        val d = GestureActionDispatcher(Profile.defaults().first { it.name == "Leitura" })
        assertEquals(AirAction.ZoomStep(true), d.onStableGesture(Gesture.THUMB_UP))
        assertEquals(AirAction.ZoomStep(false), d.onStableGesture(Gesture.THUMB_DOWN))
    }

    @Test fun noneActionMeansNothing() {
        val p = Profile.defaults().first().with(Triggers.SWIPE_RIGHT, ActionId.NONE)
        assertNull(GestureActionDispatcher(p).onSwipe(SwipeDirection.RIGHT))
    }

    @Test fun customGestureUsesBinding() {
        val p = Profile.defaults().first().with(Triggers.custom("abc"), ActionId.HOME)
        assertEquals(AirAction.Home, GestureActionDispatcher(p).onCustom("abc"))
        assertNull(GestureActionDispatcher(p).onCustom("zzz"))
    }

    @Test fun profileSwitchChangesBehaviour() {
        val d = GestureActionDispatcher()
        assertEquals(AirAction.Confirm, d.onStableGesture(Gesture.THUMB_UP))
        d.profile = Profile.defaults().first { it.name == "Apresentação" }
        d.onStableGesture(Gesture.NONE)
        assertEquals(AirAction.NextSlide, d.onStableGesture(Gesture.THUMB_UP))
    }
}
