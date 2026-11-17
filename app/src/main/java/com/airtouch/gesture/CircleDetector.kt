package com.airtouch.gesture

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.sqrt

/**
 * Detecta um movimento circular (uma volta quase completa) do dedo indicador.
 * Usado para abrir o menu de ações. Coordenadas normalizadas da tela (0..1).
 * Kotlin puro (testável na JVM).
 */
class CircleDetector(
    private val windowMs: Long = 1600,
    private val cooldownMs: Long = 2500,
    private val minRadius: Float = 0.04f,
    private val maxRadius: Float = 0.35f,
    private val minTurn: Double = 2.0 * PI * 0.88,
) {
    private data class S(val x: Float, val y: Float, val t: Long)

    private val samples = ArrayDeque<S>()
    private var lastFire = Long.MIN_VALUE / 2

    fun reset() {
        samples.clear()
    }

    /** Devolve true no quadro em que a volta é completada. */
    fun update(x: Float, y: Float, nowMs: Long): Boolean {
        samples.addLast(S(x, y, nowMs))
        while (samples.isNotEmpty() && nowMs - samples.first().t > windowMs) {
            samples.removeFirst()
        }
        if (nowMs - lastFire < cooldownMs || samples.size < 14) return false

        var cx = 0f
        var cy = 0f
        for (s in samples) {
            cx += s.x
            cy += s.y
        }
        cx /= samples.size
        cy /= samples.size

        var rSum = 0f
        for (s in samples) rSum += hypot(s.x - cx, s.y - cy)
        val r = rSum / samples.size
        if (r < minRadius || r > maxRadius) return false

        var varSum = 0f
        for (s in samples) {
            val dr = hypot(s.x - cx, s.y - cy) - r
            varSum += dr * dr
        }
        if (sqrt(varSum / samples.size) / r > 0.30f) return false

        var total = 0.0
        var prev = atan2((samples.first().y - cy).toDouble(), (samples.first().x - cx).toDouble())
        var first = true
        for (s in samples) {
            if (first) {
                first = false
                continue
            }
            val a = atan2((s.y - cy).toDouble(), (s.x - cx).toDouble())
            var da = a - prev
            while (da > PI) da -= 2.0 * PI
            while (da < -PI) da += 2.0 * PI
            total += da
            prev = a
        }

        if (abs(total) >= minTurn) {
            lastFire = nowMs
            samples.clear()
            return true
        }
        return false
    }
}
