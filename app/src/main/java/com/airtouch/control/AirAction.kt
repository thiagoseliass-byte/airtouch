package com.airtouch.control

/** Ações de alto nível que um gesto pode disparar. Novas ações entram aqui. */
sealed interface AirAction {
    data object Confirm : AirAction
    data object Cancel : AirAction
    data object NextSlide : AirAction
    data object PrevSlide : AirAction
    data object ScrollUp : AirAction
    data object ScrollDown : AirAction
    data class Pause(val paused: Boolean) : AirAction
}
