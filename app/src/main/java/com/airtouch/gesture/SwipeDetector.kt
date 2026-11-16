package com.airtouch.gesture

import kotlin.math.abs

/**
 * Detecta "varrer" a mão: deslocamento rápido do centro da palma em uma direção.
 * Coordenadas normalizadas (0..1) do quadro já espelhado, então "direita" é a
 * direita de quem olha a tela. Kotlin puro (testável na JVM).
 */
class SwipeDetector(
    private val windowMs: Long = 450,
    private val minDistance: Float = 0.25f,
    private val minSpeed: Float = 0.7f,
    private val cooldownMs: Long = 900,
) {
    private data class Sample(val x: Float, val y: Float, val t: Long)

    private val samples = ArrayDeque<Sample>()
    private var lastFire = Long.MIN_VALUE / 2

    fun reset() {
        samples.clear()
    }

    fun update(x: Float, y: Float, nowMs: Long): SwipeDirection? {
        samples.addLast(Sample(x, y, nowMs))
        while (samples.isNotEmpty() && nowMs - samples.first().t > windowMs) {
            samples.removeFirst()
        }
        if (nowMs - lastFire < cooldownMs || samples.size < 3) return null

        val first = samples.first()
        val dt = (nowMs - first.t) / 1000f
        if (dt <= 0f) return null

        val dx = x - first.x
        val dy = y - first.y
        val adx = abs(dx)
        val ady = abs(dy)

        val result = when {
            adx >= minDistance && adx / dt >= minSpeed && adx > 2f * ady ->
                if (dx > 0f) SwipeDirection.RIGHT else SwipeDirection.LEFT

            ady >= minDistance && ady / dt >= minSpeed && ady > 2f * adx ->
                if (dy > 0f) SwipeDirection.DOWN else SwipeDirection.UP

            else -> null
        }
        if (result != null) {
            lastFire = nowMs
            samples.clear()
        }
        return result
    }
}
