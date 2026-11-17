package com.airtouch.control

import com.airtouch.gesture.SwipeDirection

/** Ações que o usuário pode associar a um gesto (lista usada na tela Personalizar). */
enum class ActionId(val label: String) {
    NONE("Nenhuma"),
    CONFIRM("Confirmar"),
    CANCEL("Cancelar / sair"),
    NEXT("Avançar (slide/página)"),
    PREV("Voltar (slide/página)"),
    SCROLL_UP("Rolar para cima"),
    SCROLL_DOWN("Rolar para baixo"),
    BACK("Voltar (sistema)"),
    HOME("Início"),
    RECENTS("Menu de apps recentes"),
    VOL_UP("Volume +"),
    VOL_DOWN("Volume −"),
    PLAY_PAUSE("Play / Pause"),
    ZOOM_IN("Zoom +"),
    ZOOM_OUT("Zoom −"),
    MENU("Menu de ações");

    fun toAction(): AirAction? = when (this) {
        NONE -> null
        CONFIRM -> AirAction.Confirm
        CANCEL -> AirAction.Cancel
        NEXT -> AirAction.NextSlide
        PREV -> AirAction.PrevSlide
        SCROLL_UP -> AirAction.ScrollUp
        SCROLL_DOWN -> AirAction.ScrollDown
        BACK -> AirAction.Back
        HOME -> AirAction.Home
        RECENTS -> AirAction.Recents
        VOL_UP -> AirAction.VolumeUp
        VOL_DOWN -> AirAction.VolumeDown
        PLAY_PAUSE -> AirAction.PlayPause
        ZOOM_IN -> AirAction.ZoomStep(true)
        ZOOM_OUT -> AirAction.ZoomStep(false)
        MENU -> AirAction.OpenMenu
    }
}

/** Chaves dos gatilhos (o que o usuário faz com a mão) usadas nos perfis. */
object Triggers {
    const val THUMB_UP = "THUMB_UP"
    const val THUMB_DOWN = "THUMB_DOWN"
    const val SWIPE_LEFT = "SWIPE_LEFT"
    const val SWIPE_RIGHT = "SWIPE_RIGHT"
    const val SWIPE_UP = "SWIPE_UP"
    const val SWIPE_DOWN = "SWIPE_DOWN"

    fun swipe(dir: SwipeDirection): String = "SWIPE_" + dir.name
    fun custom(id: String): String = "custom:$id"
    fun isCustom(key: String): Boolean = key.startsWith("custom:")

    /** Gatilhos embutidos editáveis, na ordem em que aparecem na tela. */
    val builtIn: List<Pair<String, String>> = listOf(
        THUMB_UP to "Polegar para cima",
        THUMB_DOWN to "Polegar para baixo",
        SWIPE_LEFT to "Varrer a mão aberta ←",
        SWIPE_RIGHT to "Varrer a mão aberta →",
        SWIPE_UP to "Varrer a mão aberta ↑",
        SWIPE_DOWN to "Varrer a mão aberta ↓",
    )
}

/**
 * Perfil: conjunto de associações gatilho -> ação. Punho/mão aberta (pausa/retoma),
 * cursor, clique, arrastar e rolagem de dois dedos são fixos em todos os perfis.
 */
data class Profile(
    val name: String,
    val bindings: Map<String, ActionId>,
    val builtIn: Boolean = false,
) {
    fun actionFor(key: String): AirAction? = bindings[key]?.toAction()

    fun with(key: String, action: ActionId): Profile = copy(bindings = bindings + (key to action))

    fun without(key: String): Profile = copy(bindings = bindings - key)

    companion object {
        const val GENERAL = "Controle geral"

        fun defaults(): List<Profile> = listOf(
            Profile(
                GENERAL,
                mapOf(
                    Triggers.THUMB_UP to ActionId.CONFIRM,
                    Triggers.THUMB_DOWN to ActionId.CANCEL,
                    Triggers.SWIPE_LEFT to ActionId.PREV,
                    Triggers.SWIPE_RIGHT to ActionId.NEXT,
                    Triggers.SWIPE_UP to ActionId.SCROLL_UP,
                    Triggers.SWIPE_DOWN to ActionId.SCROLL_DOWN,
                ),
                builtIn = true,
            ),
            Profile(
                "Apresentação",
                mapOf(
                    Triggers.THUMB_UP to ActionId.NEXT,
                    Triggers.THUMB_DOWN to ActionId.CANCEL,
                    Triggers.SWIPE_LEFT to ActionId.PREV,
                    Triggers.SWIPE_RIGHT to ActionId.NEXT,
                    Triggers.SWIPE_UP to ActionId.NONE,
                    Triggers.SWIPE_DOWN to ActionId.NONE,
                ),
                builtIn = true,
            ),
            Profile(
                "Leitura",
                mapOf(
                    Triggers.THUMB_UP to ActionId.ZOOM_IN,
                    Triggers.THUMB_DOWN to ActionId.ZOOM_OUT,
                    Triggers.SWIPE_LEFT to ActionId.PREV,
                    Triggers.SWIPE_RIGHT to ActionId.NEXT,
                    Triggers.SWIPE_UP to ActionId.SCROLL_UP,
                    Triggers.SWIPE_DOWN to ActionId.SCROLL_DOWN,
                ),
                builtIn = true,
            ),
            Profile(
                "Navegação",
                mapOf(
                    Triggers.THUMB_UP to ActionId.HOME,
                    Triggers.THUMB_DOWN to ActionId.BACK,
                    Triggers.SWIPE_LEFT to ActionId.BACK,
                    Triggers.SWIPE_RIGHT to ActionId.NONE,
                    Triggers.SWIPE_UP to ActionId.SCROLL_UP,
                    Triggers.SWIPE_DOWN to ActionId.SCROLL_DOWN,
                ),
                builtIn = true,
            ),
            Profile(
                "Mídia",
                mapOf(
                    Triggers.THUMB_UP to ActionId.PLAY_PAUSE,
                    Triggers.THUMB_DOWN to ActionId.CANCEL,
                    Triggers.SWIPE_LEFT to ActionId.PREV,
                    Triggers.SWIPE_RIGHT to ActionId.NEXT,
                    Triggers.SWIPE_UP to ActionId.VOL_UP,
                    Triggers.SWIPE_DOWN to ActionId.VOL_DOWN,
                ),
                builtIn = true,
            ),
            Profile(
                "Jogos",
                mapOf(
                    Triggers.THUMB_UP to ActionId.CONFIRM,
                    Triggers.THUMB_DOWN to ActionId.CANCEL,
                    Triggers.SWIPE_LEFT to ActionId.NONE,
                    Triggers.SWIPE_RIGHT to ActionId.NONE,
                    Triggers.SWIPE_UP to ActionId.NONE,
                    Triggers.SWIPE_DOWN to ActionId.NONE,
                ),
                builtIn = true,
            ),
        )
    }
}
