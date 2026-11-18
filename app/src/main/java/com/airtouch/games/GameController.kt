package com.airtouch.games

import com.airtouch.gesture.Gesture
import kotlin.math.abs

/** Entrada de jogo derivada da mão: posição, direção discreta e botão de ação. */
data class GameInput(
    /** Posição do dedo na área do jogo (0..1), ou null se não há mão. */
    val x: Float?,
    val y: Float?,
    /** -1, 0, +1 conforme o dedo sai do centro (zona morta). */
    val dirX: Int,
    val dirY: Int,
    /** Pinça = botão de ação. */
    val action: Boolean,
)

/** Converte posição do ponteiro + gesto em controles de jogo. Kotlin puro. */
object GameController {
    const val DEADZONE = 0.14f

    fun map(pointer: Pair<Float, Float>?, gesture: Gesture): GameInput {
        if (pointer == null) return GameInput(null, null, 0, 0, false)
        val x = pointer.first.coerceIn(0f, 1f)
        val y = pointer.second.coerceIn(0f, 1f)
        return GameInput(x, y, dir(x), dir(y), gesture == Gesture.PINCH)
    }

    private fun dir(v: Float): Int {
        val d = v - 0.5f
        return if (abs(d) < DEADZONE) 0 else if (d > 0) 1 else -1
    }
}
