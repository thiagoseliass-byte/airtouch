package com.airtouch.mouse

import com.airtouch.gesture.Gesture
import com.airtouch.model.Hand
import com.airtouch.model.LM
import com.airtouch.model.Landmark
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VirtualMouseTest {

    /** Mão sintética: só as pontas importam para o mouse virtual. */
    private fun hand(x: Float = 0.5f, y: Float = 0.5f): Hand {
        val pts = MutableList(LM.COUNT) { Landmark(0.5f, 0.5f) }
        pts[LM.INDEX_TIP] = Landmark(x, y)
        pts[LM.THUMB_TIP] = Landmark(x, y)
        pts[LM.MIDDLE_TIP] = Landmark(x, y)
        return Hand(pts)
    }

    private class Runner(val mouse: VirtualMouse = VirtualMouse()) {
        var t = 0L
        val events = mutableListOf<MouseEvent>()

        fun step(g: Gesture, h: Hand?, n: Int = 1, stepMs: Long = 33) {
            repeat(n) {
                events += mouse.update(g, h, t)
                t += stepMs
            }
        }
    }

    @Test fun pointingShowsCursorAndNoEvents() {
        val r = Runner()
        r.step(Gesture.POINTING, hand(), 5)
        assertTrue(r.events.isEmpty())
        val c = r.mouse.cursor
        assertNotNull(c)
        assertEquals(VirtualMouse.Mode.MOVE, c!!.mode)
        assertEquals(0.5f, c.x, 0.02f)
    }

    @Test fun cursorFollowsFingerWithSensitivityMapping() {
        val r = Runner(VirtualMouse(VirtualMouse.Config(sensitivity = 0.5f)))
        // Com sensibilidade 0,5 a região usada é 0,15..0,85: x = 0,85 chega no canto direito.
        r.step(Gesture.POINTING, hand(0.85f, 0.5f), 30)
        assertEquals(1f, r.mouse.cursor!!.x, 0.03f)
        r.step(Gesture.POINTING, hand(0.15f, 0.5f), 60)
        assertEquals(0f, r.mouse.cursor!!.x, 0.03f)
    }

    @Test fun quickPinchIsClick() {
        val r = Runner()
        r.step(Gesture.POINTING, hand(), 5)
        r.step(Gesture.PINCH, hand(), 3)
        r.step(Gesture.POINTING, hand(), 2)
        val clicks = r.events.filterIsInstance<MouseEvent.Click>()
        assertEquals(1, clicks.size)
        assertEquals(1, clicks[0].count)
        assertTrue(r.events.none { it is MouseEvent.DragStart })
    }

    @Test fun twoQuickPinchesAreDoubleClick() {
        val r = Runner()
        r.step(Gesture.POINTING, hand(), 3)
        r.step(Gesture.PINCH, hand(), 2)
        r.step(Gesture.POINTING, hand(), 1)
        r.step(Gesture.PINCH, hand(), 2)
        r.step(Gesture.POINTING, hand(), 2)
        val clicks = r.events.filterIsInstance<MouseEvent.Click>()
        assertEquals(listOf(1, 2), clicks.map { it.count })
    }

    @Test fun heldPinchIsDrag() {
        val r = Runner()
        r.step(Gesture.POINTING, hand(), 5)
        r.step(Gesture.PINCH, hand(0.5f, 0.5f), 20) // ~660 ms
        r.step(Gesture.PINCH, hand(0.6f, 0.5f), 10)
        r.step(Gesture.POINTING, hand(0.6f, 0.5f), 2)

        val types = r.events.map { it::class.simpleName }
        assertTrue(types.contains("DragStart"))
        assertTrue(types.contains("DragMove"))
        assertTrue(types.contains("DragEnd"))
        assertTrue(r.events.none { it is MouseEvent.Click })
        // DragStart vem antes de DragEnd.
        assertTrue(types.indexOf("DragStart") < types.indexOf("DragEnd"))
    }

    @Test fun dragEndsWhenGestureIsNotUsable() {
        val r = Runner()
        r.step(Gesture.POINTING, hand(), 3)
        r.step(Gesture.PINCH, hand(), 20)
        r.step(Gesture.FIST, hand(), 1)
        assertTrue(r.events.any { it is MouseEvent.DragEnd })
        assertNull(r.mouse.cursor)
    }

    @Test fun brieflyLostHandDoesNotDropDrag() {
        val r = Runner()
        r.step(Gesture.POINTING, hand(), 3)
        r.step(Gesture.PINCH, hand(), 20)
        r.step(Gesture.PINCH, null, 2)
        assertTrue(r.events.none { it is MouseEvent.DragEnd })
        r.step(Gesture.PINCH, null, 10)
        assertTrue(r.events.any { it is MouseEvent.DragEnd })
    }

    @Test fun twoFingersScrollFollowsHand() {
        val r = Runner()
        r.step(Gesture.TWO_FINGERS, hand(0.5f, 0.4f), 3)
        assertTrue(r.events.first() is MouseEvent.ScrollStart)

        for (i in 0 until 10) {
            r.step(Gesture.TWO_FINGERS, hand(0.5f, 0.4f + 0.02f * i))
        }
        val total = r.events.filterIsInstance<MouseEvent.ScrollMove>().sumOf { it.dy.toDouble() }
        assertTrue("mão desceu => dy positivo (total=$total)", total > 0.0)

        r.step(Gesture.POINTING, hand(), 1)
        assertTrue(r.events.any { it is MouseEvent.ScrollEnd })
    }

    @Test fun invertScrollFlipsSign() {
        val r = Runner(VirtualMouse(VirtualMouse.Config(invertScroll = true)))
        r.step(Gesture.TWO_FINGERS, hand(0.5f, 0.4f), 2)
        for (i in 0 until 10) r.step(Gesture.TWO_FINGERS, hand(0.5f, 0.4f + 0.02f * i))
        val total = r.events.filterIsInstance<MouseEvent.ScrollMove>().sumOf { it.dy.toDouble() }
        assertTrue(total < 0.0)
    }

    @Test fun resetEndsEverything() {
        val r = Runner()
        r.step(Gesture.POINTING, hand(), 3)
        r.step(Gesture.PINCH, hand(), 20)
        val out = r.mouse.reset()
        assertTrue(out.any { it is MouseEvent.DragEnd })
        assertNull(r.mouse.cursor)
    }
}
