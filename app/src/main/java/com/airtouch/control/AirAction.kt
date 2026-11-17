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

    data object Back : AirAction
    data object Home : AirAction
    data object Recents : AirAction
    data object VolumeUp : AirAction
    data object VolumeDown : AirAction
    data object PlayPause : AirAction
    data class ZoomStep(val zoomIn: Boolean) : AirAction

    /** Movimento circular: abre o menu de ações. */
    data object OpenMenu : AirAction
}
