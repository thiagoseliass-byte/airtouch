package com.airtouch.control

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CustomGestureEngineTest {
    private val hand = TestHands.palm()

    private fun engineWithSelf(tol: Float = 0.5f): CustomGestureEngine {
        val f = CustomGestureEngine.features(hand, 100, 100)!!
        return CustomGestureEngine(framesToFire = 3, framesToRearm = 2).also {
            it.gestures = listOf(CustomGesture("g1", "Meu gesto", f.toList(), tol))
        }
    }

    @Test fun firesOnceWhenHeld() {
        val e = engineWithSelf()
        val fired = (1..10).mapNotNull { e.update(hand, 100, 100) }
        assertEquals(listOf("g1"), fired)
    }

    @Test fun rearmsAfterLeaving() {
        val e = engineWithSelf()
        repeat(5) { e.update(hand, 100, 100) }
        repeat(3) { e.update(null, 100, 100) }
        val again = (1..5).mapNotNull { e.update(hand, 100, 100) }
        assertEquals(listOf("g1"), again)
    }

    @Test fun differentPoseDoesNotMatch() {
        val e = engineWithSelf(tol = 0f)
        val other = com.airtouch.model.Hand(
            hand.landmarks.mapIndexed { i, l -> if (i > 0) l.copy(x = l.x + 0.3f, y = l.y - 0.2f * (i % 3)) else l }
        )
        assertNull((1..10).firstNotNullOfOrNull { e.update(other, 100, 100) })
        assertTrue(e.live != null)
    }
}
