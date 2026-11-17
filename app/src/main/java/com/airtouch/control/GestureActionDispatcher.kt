package com.airtouch.control

import com.airtouch.gesture.Gesture
import com.airtouch.gesture.SwipeDirection

/**
 * Liga gestos a ações. As tabelas [gestureBindings] e [swipeBindings] são o
 * "perfil" atual; na Fase 4 serão editáveis pelo usuário.
 *
 * Também controla o bloqueio: punho fechado pausa tudo; mão aberta retoma.
 * Kotlin puro (testável na JVM).
 */
class GestureActionDispatcher(
    private val gestureBindings: Map<Gesture, AirAction> = mapOf(
        Gesture.THUMB_UP to AirAction.Confirm,
        Gesture.THUMB_DOWN to AirAction.Cancel,
    ),
    private val swipeBindings: Map<SwipeDirection, AirAction> = mapOf(
        SwipeDirection.LEFT to AirAction.PrevSlide,
        SwipeDirection.RIGHT to AirAction.NextSlide,
        SwipeDirection.UP to AirAction.ScrollUp,
        SwipeDirection.DOWN to AirAction.ScrollDown,
    ),
) {
    var locked = false
        private set

    fun reset() {
        locked = false
    }

    /** Chame quando o gesto estável MUDAR. */
    fun onStableGesture(gesture: Gesture): AirAction? {
        if (gesture == Gesture.FIST && !locked) {
            locked = true
            return AirAction.Pause(true)
        }
        if (gesture == Gesture.OPEN_PALM && locked) {
            locked = false
            return AirAction.Pause(false)
        }
        if (locked) return null
        return gestureBindings[gesture]
    }

    fun onSwipe(direction: SwipeDirection): AirAction? {
        if (locked) return null
        return swipeBindings[direction]
    }
}
