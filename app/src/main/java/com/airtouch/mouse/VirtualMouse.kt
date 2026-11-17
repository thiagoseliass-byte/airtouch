package com.airtouch.mouse

import com.airtouch.gesture.Gesture
import com.airtouch.model.Hand
import com.airtouch.model.LM
import kotlin.math.hypot

/**
 * Mouse virtual: converte gesto estável + posição da mão em eventos de mouse.
 *
 *  - Indicador apontado  -> move o cursor
 *  - Pinça rápida        -> clique (no ponto onde a pinça começou; 2 seguidos = duplo clique)
 *  - Pinça segurada      -> arrastar
 *  - Dois dedos          -> rolar (acompanha o movimento vertical da mão)
 *
 * Kotlin puro (sem Android). Chame [update] uma vez por quadro, sempre da mesma thread.
 */
class VirtualMouse(@Volatile var config: Config = Config()) {

    data class Config(
        /** 0 = precisa mexer muito a mão; 1 = pouco movimento percorre a tela toda. */
        val sensitivity: Float = 0.5f,
        val invertScroll: Boolean = false,
    )

    enum class Mode { MOVE, PRESS, DRAG, SCROLL }

    data class Cursor(val x: Float, val y: Float, val mode: Mode)

    private enum class S { IDLE, MOVING, PRESSED, DRAGGING, SCROLLING }

    private data class Sample(val x: Float, val y: Float, val t: Long)

    /** Cursor atual, ou null se não há mão usando o mouse. */
    var cursor: Cursor? = null
        private set

    /** Última posição conhecida (usada para rolagens discretas). */
    var lastX = 0.75f
        private set
    var lastY = 0.5f
        private set

    private var state = S.IDLE
    private val fx = OneEuroFilter()
    private val fy = OneEuroFilter()
    private val history = ArrayDeque<Sample>()

    private var curX = 0.5f
    private var curY = 0.5f
    private var pressX = 0f
    private var pressY = 0f
    private var pressT = 0L
    private var scrollLastY = 0f
    private var lastClickT = Long.MIN_VALUE / 2
    private var lastClickX = 0f
    private var lastClickY = 0f
    private var missing = 0

    fun update(gesture: Gesture, hand: Hand?, nowMs: Long): List<MouseEvent> {
        val out = ArrayList<MouseEvent>(2)
        val usable = gesture == Gesture.POINTING ||
            gesture == Gesture.PINCH ||
            gesture == Gesture.TWO_FINGERS

        if (!usable) {
            endAll(out)
            return out
        }
        if (hand == null) {
            // Pequenas falhas de detecção não devem soltar um arraste.
            missing++
            if (missing > MAX_MISSING_FRAMES) endAll(out)
            return out
        }
        missing = 0

        val (ax, ay) = anchor(gesture, hand)
        val (sx, sy) = toScreen(ax, ay)
        val x = fx.filter(sx, nowMs)
        val y = fy.filter(sy, nowMs)
        curX = x
        curY = y
        lastX = x
        lastY = y

        when (gesture) {
            Gesture.POINTING -> {
                when (state) {
                    S.PRESSED -> emitClick(out, nowMs)
                    S.DRAGGING -> out += MouseEvent.DragEnd(x, y)
                    S.SCROLLING -> out += MouseEvent.ScrollEnd
                    else -> Unit
                }
                state = S.MOVING
                record(x, y, nowMs)
                cursor = Cursor(x, y, Mode.MOVE)
            }

            Gesture.PINCH -> {
                when (state) {
                    S.PRESSED -> {
                        if (nowMs - pressT >= DRAG_HOLD_MS) {
                            state = S.DRAGGING
                            out += MouseEvent.DragStart(pressX, pressY)
                        }
                    }

                    S.DRAGGING -> out += MouseEvent.DragMove(x, y)

                    else -> {
                        if (state == S.SCROLLING) out += MouseEvent.ScrollEnd
                        // O gesto só é confirmado alguns quadros depois de começar;
                        // volta no tempo para pegar onde o cursor estava ao iniciar a pinça.
                        val p = history.firstOrNull { nowMs - it.t <= PRESS_LOOKBACK_MS }
                        pressX = p?.x ?: x
                        pressY = p?.y ?: y
                        pressT = nowMs
                        state = S.PRESSED
                    }
                }
                cursor = if (state == S.PRESSED) {
                    Cursor(pressX, pressY, Mode.PRESS)
                } else {
                    Cursor(x, y, Mode.DRAG)
                }
            }

            else -> { // TWO_FINGERS
                if (state == S.SCROLLING) {
                    var dy = y - scrollLastY
                    if (config.invertScroll) dy = -dy
                    out += MouseEvent.ScrollMove(dy)
                } else {
                    endActive(out)
                    state = S.SCROLLING
                    out += MouseEvent.ScrollStart(x, y)
                }
                scrollLastY = y
                cursor = Cursor(x, y, Mode.SCROLL)
            }
        }
        return out
    }

    /** Encerra qualquer arraste/rolagem em andamento e esconde o cursor. */
    fun reset(): List<MouseEvent> {
        val out = ArrayList<MouseEvent>(1)
        endAll(out)
        return out
    }

    private fun emitClick(out: MutableList<MouseEvent>, nowMs: Long) {
        val isDouble = nowMs - lastClickT <= DOUBLE_CLICK_MS &&
            hypot(pressX - lastClickX, pressY - lastClickY) < DOUBLE_CLICK_RADIUS
        out += MouseEvent.Click(pressX, pressY, if (isDouble) 2 else 1)
        lastClickT = if (isDouble) Long.MIN_VALUE / 2 else nowMs
        lastClickX = pressX
        lastClickY = pressY
    }

    private fun endActive(out: MutableList<MouseEvent>) {
        when (state) {
            S.DRAGGING -> out += MouseEvent.DragEnd(curX, curY)
            S.SCROLLING -> out += MouseEvent.ScrollEnd
            else -> Unit
        }
        state = S.IDLE
    }

    private fun endAll(out: MutableList<MouseEvent>) {
        endActive(out)
        state = S.IDLE
        cursor = null
        fx.reset()
        fy.reset()
        history.clear()
        missing = 0
    }

    private fun record(x: Float, y: Float, nowMs: Long) {
        history.addLast(Sample(x, y, nowMs))
        while (history.isNotEmpty() && nowMs - history.first().t > HISTORY_MS) {
            history.removeFirst()
        }
    }

    /** Ponto da mão que controla o cursor, em coordenadas normalizadas do quadro. */
    private fun anchor(gesture: Gesture, hand: Hand): Pair<Float, Float> {
        val lm = hand.landmarks
        return when (gesture) {
            Gesture.PINCH -> Pair(
                (lm[LM.THUMB_TIP].x + lm[LM.INDEX_TIP].x) / 2f,
                (lm[LM.THUMB_TIP].y + lm[LM.INDEX_TIP].y) / 2f,
            )

            Gesture.TWO_FINGERS -> Pair(
                (lm[LM.INDEX_TIP].x + lm[LM.MIDDLE_TIP].x) / 2f,
                (lm[LM.INDEX_TIP].y + lm[LM.MIDDLE_TIP].y) / 2f,
            )

            else -> Pair(lm[LM.INDEX_TIP].x, lm[LM.INDEX_TIP].y)
        }
    }

    /** Mapeia um ponto do quadro da câmera (0..1) para a tela inteira (0..1), pela sensibilidade. */
    fun mapToScreen(ax: Float, ay: Float): Pair<Float, Float> = toScreen(ax, ay)

    /** Mapeia a região central do quadro da câmera para a tela inteira. */
    private fun toScreen(ax: Float, ay: Float): Pair<Float, Float> {
        val half = 0.5f - 0.3f * config.sensitivity.coerceIn(0f, 1f) // 0,5 .. 0,2
        val x = ((ax - 0.5f) / (2f * half) + 0.5f).coerceIn(0f, 1f)
        val y = ((ay - 0.5f) / (2f * half) + 0.5f).coerceIn(0f, 1f)
        return Pair(x, y)
    }

    private companion object {
        const val DRAG_HOLD_MS = 450L
        const val PRESS_LOOKBACK_MS = 200L
        const val HISTORY_MS = 250L
        const val DOUBLE_CLICK_MS = 350L
        const val DOUBLE_CLICK_RADIUS = 0.06f
        const val MAX_MISSING_FRAMES = 4
    }
}
