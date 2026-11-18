package com.airtouch.gesture

/**
 * Gestos de pose (estáticos) conhecidos. Para adicionar um gesto novo: inclua aqui e
 * implemente a regra em [GestureClassifier] (ou, na Fase 4, um modelo treinado).
 */
enum class Gesture(val label: String, val action: String) {
    NONE("Nenhum", "—"),
    UNKNOWN("Gesto não reconhecido", "—"),
    OPEN_PALM("Mão aberta", "Mostrar painel / retomar"),
    FIST("Punho fechado", "Pausar / bloquear comandos"),
    THUMB_UP("Polegar para cima", "Confirmar"),
    THUMB_DOWN("Polegar para baixo", "Cancelar"),
    POINTING("Indicador apontado", "Controlar cursor"),
    PINCH("Pinça", "Clique / arrastar"),
    TWO_FINGERS("Dois dedos", "Rolar página");

    /** Verdadeiro para gestos que representam um comando de verdade. */
    val isCommand: Boolean get() = this != NONE && this != UNKNOWN
}

/** Gestos de movimento (varrer a mão aberta). */
enum class SwipeDirection { LEFT, RIGHT, UP, DOWN }
