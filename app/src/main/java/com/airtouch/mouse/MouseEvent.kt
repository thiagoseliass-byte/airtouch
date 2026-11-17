package com.airtouch.mouse

/** Eventos gerados pelo mouse virtual. Coordenadas normalizadas da tela (0..1). */
sealed interface MouseEvent {
    /** [count] = 2 quando é o segundo clique de um duplo clique. */
    data class Click(val x: Float, val y: Float, val count: Int) : MouseEvent
    data class DragStart(val x: Float, val y: Float) : MouseEvent
    data class DragMove(val x: Float, val y: Float) : MouseEvent
    data class DragEnd(val x: Float, val y: Float) : MouseEvent
    data class ScrollStart(val x: Float, val y: Float) : MouseEvent

    /** [dy] em alturas de tela; negativo = mão subiu. */
    data class ScrollMove(val dy: Float) : MouseEvent
    data object ScrollEnd : MouseEvent
}
