package com.airtouch.control

import com.airtouch.gesture.Gesture
import com.airtouch.gesture.SwipeDirection

/**
 * Liga gestos a ações usando o [Profile] ativo (editável na tela Personalizar).
 *
 * Também controla o bloqueio: punho fechado pausa tudo; mão aberta retoma.
 * Kotlin puro (testável na JVM).
 */
class GestureActionDispatcher(
    @Volatile var profile: Profile = Profile.defaults().first(),
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
        return profile.actionFor(gesture.name)
    }

    fun onSwipe(direction: SwipeDirection): AirAction? {
        if (locked) return null
        return profile.actionFor(Triggers.swipe(direction))
    }

    /** Gesto personalizado reconhecido (id do gesto). */
    fun onCustom(id: String): AirAction? {
        if (locked) return null
        return profile.actionFor(Triggers.custom(id))
    }
}
