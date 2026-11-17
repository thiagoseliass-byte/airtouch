package com.airtouch.control

import com.airtouch.gesture.Gesture
import com.airtouch.model.Hand
import com.airtouch.model.LM
import com.airtouch.model.Landmark
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Testa o encadeamento reconhecimento -> mouse -> ações com mãos sintéticas. */
class ControlPipelineTest {

    /** Mão em pé; [fingers] = indicador, médio, anelar, mínimo estendidos. [dx] desloca na horizontal. */
    private fun hand(
        index: Boolean, middle: Boolean, ring: Boolean, pinky: Boolean,
        dx: Float = 0f,
    ): Hand {
        val pts = Array(LM.COUNT) { Landmark(0f, 0f) }
        fun p(i: Int, x: Float, y: Float) { pts[i] = Landmark(x + dx, y) }

        p(LM.WRIST, 0.5f, 0.8f)
        p(LM.THUMB_CMC, 0.42f, 0.76f)
        p(LM.THUMB_MCP, 0.38f, 0.70f)
        p(LM.THUMB_IP, 0.43f, 0.68f)
        p(LM.THUMB_TIP, 0.46f, 0.66f)

        fun finger(mcp: Int, pip: Int, dip: Int, tip: Int, x: Float, mcpY: Float, ext: Boolean) {
            p(mcp, x, mcpY)
            p(pip, x, mcpY - 0.07f)
            if (ext) {
                p(dip, x, mcpY - 0.11f); p(tip, x, mcpY - 0.15f)
            } else {
                p(dip, x, mcpY - 0.02f); p(tip, x, mcpY + 0.03f)
            }
        }
        finger(LM.INDEX_MCP, LM.INDEX_PIP, LM.INDEX_DIP, LM.INDEX_TIP, 0.44f, 0.62f, index)
        finger(LM.MIDDLE_MCP, LM.MIDDLE_PIP, LM.MIDDLE_DIP, LM.MIDDLE_TIP, 0.50f, 0.60f, middle)
        finger(LM.RING_MCP, LM.RING_PIP, LM.RING_DIP, LM.RING_TIP, 0.56f, 0.62f, ring)
        finger(LM.PINKY_MCP, LM.PINKY_PIP, LM.PINKY_DIP, LM.PINKY_TIP, 0.62f, 0.66f, pinky)
        return Hand(pts.toList())
    }

    private fun fist(dx: Float = 0f) = hand(false, false, false, false, dx)
    private fun palm(dx: Float = 0f) = hand(true, true, true, true, dx)
    private fun pointing() = hand(true, false, false, false)

    private class Clock { var t = 0L; fun next(): Long { val v = t; t += 33; return v } }

    @Test fun noHandProducesNothing() {
        val p = ControlPipeline()
        val out = p.process(null, 1, 1, 0)
        assertEquals(Gesture.NONE, out.stable)
        assertTrue(out.actions.isEmpty())
        assertTrue(out.mouseEvents.isEmpty())
        assertNull(out.cursor)
    }

    @Test fun fistPausesAndOpenPalmResumes() {
        val p = ControlPipeline()
        val c = Clock()
        val actions = mutableListOf<AirAction>()

        repeat(4) { actions += p.process(fist(), 1, 1, c.next()).actions }
        assertTrue(actions.contains(AirAction.Pause(true)))
        assertTrue(p.process(fist(), 1, 1, c.next()).locked)

        actions.clear()
        repeat(4) { actions += p.process(palm(), 1, 1, c.next()).actions }
        assertTrue(actions.contains(AirAction.Pause(false)))
        assertFalse(p.process(palm(), 1, 1, c.next()).locked)
    }

    @Test fun lockedHidesCursor() {
        val p = ControlPipeline()
        val c = Clock()
        repeat(4) { p.process(pointing(), 1, 1, c.next()) }
        assertNotNull(p.process(pointing(), 1, 1, c.next()).cursor)

        repeat(5) { p.process(fist(), 1, 1, c.next()) }
        assertNull(p.process(fist(), 1, 1, c.next()).cursor)
    }

    @Test fun pointingShowsCursor() {
        val p = ControlPipeline()
        val c = Clock()
        var out = p.process(pointing(), 1, 1, c.next())
        repeat(5) { out = p.process(pointing(), 1, 1, c.next()) }
        assertEquals(Gesture.POINTING, out.stable)
        assertNotNull(out.cursor)
    }

    @Test fun openPalmFlagsPanel() {
        val p = ControlPipeline()
        val c = Clock()
        var out = p.process(palm(), 1, 1, c.next())
        repeat(5) { out = p.process(palm(), 1, 1, c.next()) }
        assertTrue(out.palmShown)
    }

    @Test fun fastSwipeRightGivesNextSlide() {
        val p = ControlPipeline()
        val c = Clock()
        val actions = mutableListOf<AirAction>()

        // Mão aberta parada (confirma o gesto) e depois varrida para a direita.
        repeat(5) { actions += p.process(palm(-0.3f), 1, 1, c.next()).actions }
        for (i in 0..8) {
            val dx = -0.3f + 0.6f * i / 8f
            actions += p.process(palm(dx), 1, 1, c.next()).actions
        }
        assertTrue("esperava NextSlide, veio $actions", actions.contains(AirAction.NextSlide))
        assertFalse(actions.contains(AirAction.PrevSlide))
    }

    @Test fun swipeIsIgnoredWhileLocked() {
        val p = ControlPipeline()
        val c = Clock()
        repeat(5) { p.process(fist(), 1, 1, c.next()) } // trava
        val actions = mutableListOf<AirAction>()
        for (i in 0..8) {
            actions += p.process(palm(-0.3f + 0.6f * i / 8f), 1, 1, c.next()).actions
        }
        // A mão aberta destrava; o importante é que a varredura nunca vire ação enquanto travado.
        assertFalse(actions.contains(AirAction.NextSlide) && actions.indexOf(AirAction.NextSlide) < actions.indexOf(AirAction.Pause(false)))
    }

    @Test fun resetClearsState() {
        val p = ControlPipeline()
        val c = Clock()
        repeat(5) { p.process(fist(), 1, 1, c.next()) }
        p.reset()
        val out = p.process(null, 1, 1, c.next())
        assertFalse(out.locked)
        assertEquals(Gesture.NONE, out.stable)
    }
}
