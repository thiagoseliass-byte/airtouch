package com.airtouch.gesture

import com.airtouch.model.Hand
import com.airtouch.model.LM
import com.airtouch.model.Landmark
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Testes na JVM com mãos sintéticas (coordenadas normalizadas, mão em pé,
 * pulso embaixo). Palma = distância pulso -> base do dedo médio = 0,2.
 */
class GestureClassifierTest {

    private enum class Thumb { CURLED, UP, DOWN }

    private fun hand(
        index: Boolean,
        middle: Boolean,
        ring: Boolean,
        pinky: Boolean,
        thumb: Thumb,
    ): Hand {
        val pts = Array(LM.COUNT) { Landmark(0f, 0f) }
        pts[LM.WRIST] = Landmark(0.5f, 0.8f)

        pts[LM.THUMB_CMC] = Landmark(0.42f, 0.76f)
        pts[LM.THUMB_MCP] = Landmark(0.38f, 0.70f)
        when (thumb) {
            Thumb.CURLED -> {
                pts[LM.THUMB_IP] = Landmark(0.43f, 0.68f)
                pts[LM.THUMB_TIP] = Landmark(0.46f, 0.66f)
            }
            Thumb.UP -> {
                pts[LM.THUMB_IP] = Landmark(0.37f, 0.56f)
                pts[LM.THUMB_TIP] = Landmark(0.36f, 0.46f)
            }
            Thumb.DOWN -> {
                pts[LM.THUMB_IP] = Landmark(0.37f, 0.85f)
                pts[LM.THUMB_TIP] = Landmark(0.36f, 0.98f)
            }
        }

        fun finger(mcp: Int, pip: Int, dip: Int, tip: Int, x: Float, mcpY: Float, extended: Boolean) {
            pts[mcp] = Landmark(x, mcpY)
            pts[pip] = Landmark(x, mcpY - 0.07f)
            if (extended) {
                pts[dip] = Landmark(x, mcpY - 0.11f)
                pts[tip] = Landmark(x, mcpY - 0.15f)
            } else {
                pts[dip] = Landmark(x, mcpY - 0.02f)
                pts[tip] = Landmark(x, mcpY + 0.03f)
            }
        }
        finger(LM.INDEX_MCP, LM.INDEX_PIP, LM.INDEX_DIP, LM.INDEX_TIP, 0.44f, 0.62f, index)
        finger(LM.MIDDLE_MCP, LM.MIDDLE_PIP, LM.MIDDLE_DIP, LM.MIDDLE_TIP, 0.50f, 0.60f, middle)
        finger(LM.RING_MCP, LM.RING_PIP, LM.RING_DIP, LM.RING_TIP, 0.56f, 0.62f, ring)
        finger(LM.PINKY_MCP, LM.PINKY_PIP, LM.PINKY_DIP, LM.PINKY_TIP, 0.62f, 0.66f, pinky)
        return Hand(pts.toList())
    }

    private fun Hand.withPoint(i: Int, p: Landmark): Hand =
        Hand(landmarks.toMutableList().also { it[i] = p })

    private fun gestureOf(h: Hand, previous: Gesture = Gesture.NONE) =
        GestureClassifier.classify(h, previous = previous).gesture

    @Test fun openPalm() =
        assertEquals(Gesture.OPEN_PALM, gestureOf(hand(true, true, true, true, Thumb.CURLED)))

    @Test fun fist() =
        assertEquals(Gesture.FIST, gestureOf(hand(false, false, false, false, Thumb.CURLED)))

    @Test fun pointing() =
        assertEquals(Gesture.POINTING, gestureOf(hand(true, false, false, false, Thumb.CURLED)))

    @Test fun thumbUp() =
        assertEquals(Gesture.THUMB_UP, gestureOf(hand(false, false, false, false, Thumb.UP)))

    @Test fun thumbDown() =
        assertEquals(Gesture.THUMB_DOWN, gestureOf(hand(false, false, false, false, Thumb.DOWN)))

    @Test fun twoFingers() =
        assertEquals(Gesture.TWO_FINGERS, gestureOf(hand(true, true, false, false, Thumb.CURLED)))

    @Test fun threeFingersIsUnknown() =
        assertEquals(Gesture.UNKNOWN, gestureOf(hand(true, true, true, false, Thumb.CURLED)))

    @Test fun pinch() {
        // Indicador estendido e ponta do polegar encostada nele.
        val h = hand(true, false, false, false, Thumb.CURLED)
            .withPoint(LM.THUMB_TIP, Landmark(0.45f, 0.48f))
        assertEquals(Gesture.PINCH, gestureOf(h))
    }

    @Test fun pinchHasHysteresis() {
        // Distância de 0,38 palma: entre o limite de entrada (0,30) e o de saída (0,45).
        val h = hand(true, false, false, false, Thumb.CURLED)
            .withPoint(LM.THUMB_TIP, Landmark(0.516f, 0.47f))
        assertEquals(Gesture.POINTING, gestureOf(h, previous = Gesture.NONE))
        assertEquals(Gesture.PINCH, gestureOf(h, previous = Gesture.PINCH))
    }

    @Test fun fistIsNotPinch() {
        // Punho: polegar encosta no indicador dobrado, mas o indicador está perto do pulso.
        val h = hand(false, false, false, false, Thumb.CURLED)
            .withPoint(LM.THUMB_TIP, Landmark(0.44f, 0.65f))
        assertEquals(Gesture.FIST, gestureOf(h))
    }

    @Test fun engineNeedsConsecutiveFramesToConfirm() {
        val engine = GestureRecognitionEngine(framesToConfirm = 4, framesToRelease = 6)
        val palm = hand(true, true, true, true, Thumb.CURLED)
        val fist = hand(false, false, false, false, Thumb.CURLED)

        repeat(3) { assertEquals(Gesture.NONE, engine.process(palm, 1, 1).stable) }
        assertEquals(Gesture.OPEN_PALM, engine.process(palm, 1, 1).stable)

        // Um quadro ruidoso não troca o gesto estável.
        assertEquals(Gesture.OPEN_PALM, engine.process(fist, 1, 1).stable)
        assertEquals(Gesture.OPEN_PALM, engine.process(palm, 1, 1).stable)
    }

    @Test fun engineReleasesAfterHandDisappears() {
        val engine = GestureRecognitionEngine(framesToConfirm = 2, framesToRelease = 3)
        val palm = hand(true, true, true, true, Thumb.CURLED)
        repeat(2) { engine.process(palm, 1, 1) }
        assertEquals(Gesture.OPEN_PALM, engine.process(palm, 1, 1).stable)
        repeat(2) { assertEquals(Gesture.OPEN_PALM, engine.process(null, 1, 1).stable) }
        assertEquals(Gesture.NONE, engine.process(null, 1, 1).stable)
    }

    @Test fun pinchWithRelaxedCurledFingersIsNotFist() {
        // Pinça "de verdade": indicador meio dobrado tocando o polegar, demais dedos dobrados.
        val h = hand(false, false, false, false, Thumb.CURLED)
            .withPoint(LM.INDEX_DIP, Landmark(0.45f, 0.55f))
            .withPoint(LM.INDEX_TIP, Landmark(0.47f, 0.50f))
            .withPoint(LM.THUMB_TIP, Landmark(0.48f, 0.50f))
        assertEquals(Gesture.PINCH, gestureOf(h))
    }

    @Test fun fistNeedsMoreFramesThanPinch() {
        val engine = GestureRecognitionEngine(framesToConfirm = 3, framesToRelease = 6)
        val pinch = hand(true, false, false, false, Thumb.CURLED)
            .withPoint(LM.THUMB_TIP, Landmark(0.45f, 0.48f))
        val fist = hand(false, false, false, false, Thumb.CURLED)
            .withPoint(LM.THUMB_TIP, Landmark(0.44f, 0.65f))
        repeat(4) { engine.process(pinch, 1, 1) }
        assertEquals(Gesture.PINCH, engine.process(pinch, 1, 1).stable)
        // Alguns quadros "parecendo punho" não podem travar o sistema.
        repeat(5) { assertEquals(Gesture.PINCH, engine.process(fist, 1, 1).stable) }
    }
}
