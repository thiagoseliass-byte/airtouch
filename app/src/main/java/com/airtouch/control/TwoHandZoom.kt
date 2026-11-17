package com.airtouch.control

import com.airtouch.model.Hand
import com.airtouch.model.LM
import kotlin.math.abs
import kotlin.math.hypot

/**
 * Zoom com duas mãos: afastar = aumentar, aproximar = diminuir.
 * Acompanha a distância entre os centros das duas palmas e devolve, a cada quadro,
 * o fator de variação (1 = sem mudança, >1 afastou, <1 aproximou).
 *
 * Kotlin puro (testável na JVM).
 */
class TwoHandZoom(
    private val framesToStart: Int = 4,
    private val framesToStop: Int = 3,
    private val deadband: Float = 0.004f,
) {
    var active = false
        private set

    private var on = 0
    private var off = 0
    private var smooth = 0f
    private var prev = 0f

    fun reset() {
        active = false
        on = 0
        off = 0
        smooth = 0f
        prev = 0f
    }

    private fun center(h: Hand, w: Float, hh: Float): Pair<Float, Float> {
        val a = h.landmarks[LM.WRIST]
        val b = h.landmarks[LM.MIDDLE_MCP]
        return Pair((a.x + b.x) / 2f * w, (a.y + b.y) / 2f * hh)
    }

    fun update(hands: List<Hand>, imageWidth: Int, imageHeight: Int): Float {
        val w = imageWidth.toFloat()
        val h = imageHeight.toFloat()

        if (hands.size >= 2) {
            val two = hands.sortedByDescending { it.score }.take(2)
            val c1 = center(two[0], w, h)
            val c2 = center(two[1], w, h)
            val diag = hypot(w, h).coerceAtLeast(1e-3f)
            val d = hypot(c1.first - c2.first, c1.second - c2.second) / diag

            off = 0
            on++
            smooth = if (on == 1) d else 0.5f * smooth + 0.5f * d

            if (!active && on >= framesToStart) {
                active = true
                prev = smooth
            }
            if (active) {
                val ratio = if (prev > 1e-4f) smooth / prev else 1f
                prev = smooth
                return if (abs(ratio - 1f) < deadband) 1f else ratio.coerceIn(0.8f, 1.25f)
            }
            return 1f
        }

        on = 0
        off++
        if (active && off >= framesToStop) reset()
        return 1f
    }
}
