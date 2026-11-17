package com.airtouch.mouse

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.max

/**
 * Filtro "One Euro": suaviza o tremor da mão quando o movimento é lento e
 * mantém a resposta rápida quando o movimento é rápido.
 * Valores de x em unidades normalizadas (0..1) e tempo em milissegundos.
 */
class OneEuroFilter(
    private val minCutoff: Float = 1.5f,
    private val beta: Float = 8f,
    private val dCutoff: Float = 1f,
) {
    private var initialized = false
    private var xPrev = 0f
    private var dxPrev = 0f
    private var tPrev = 0L

    private fun alpha(cutoff: Float, dt: Float): Float {
        val tau = 1f / (2f * PI.toFloat() * cutoff)
        return 1f / (1f + tau / dt)
    }

    fun filter(x: Float, tMs: Long): Float {
        if (!initialized) {
            initialized = true
            xPrev = x
            dxPrev = 0f
            tPrev = tMs
            return x
        }
        val dt = max((tMs - tPrev) / 1000f, 1e-3f)
        tPrev = tMs

        val dx = (x - xPrev) / dt
        val aD = alpha(dCutoff, dt)
        val dxHat = aD * dx + (1f - aD) * dxPrev

        val cutoff = minCutoff + beta * abs(dxHat)
        val a = alpha(cutoff, dt)
        val xHat = a * x + (1f - a) * xPrev

        xPrev = xHat
        dxPrev = dxHat
        return xHat
    }

    fun reset() {
        initialized = false
    }
}
