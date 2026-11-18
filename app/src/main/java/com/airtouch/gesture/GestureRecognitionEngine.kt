package com.airtouch.gesture

import com.airtouch.model.Hand

/**
 * Transforma o gesto "bruto" de cada quadro em um gesto ESTÁVEL, evitando
 * acionamentos acidentais (debounce): um gesto só vira o gesto atual depois de
 * aparecer em vários quadros seguidos, e só some depois de vários quadros sem mão.
 *
 * O PUNHO (que pausa tudo) é mais "caro": exige o dobro de quadros, e o triplo
 * se a mão vinha de uma pinça/apontando — assim uma pinça mal vista não trava o sistema.
 *
 * Não é thread-safe: chame sempre da mesma thread.
 */
class GestureRecognitionEngine(
    private val framesToConfirm: Int = 3,
    private val framesToRelease: Int = 6,
) {
    data class State(
        val raw: Gesture,
        val confidence: Float,
        val stable: Gesture,
        val pinchDist: Float = 0f,
        val indexOpen: Float = 0f,
    )

    private var stable = Gesture.NONE
    private var candidate = Gesture.NONE
    private var candidateCount = 0

    fun reset() {
        stable = Gesture.NONE
        candidate = Gesture.NONE
        candidateCount = 0
    }

    private fun needed(c: Gesture): Int = when {
        c == Gesture.NONE -> framesToRelease
        c == Gesture.FIST ->
            if (stable == Gesture.PINCH || stable == Gesture.POINTING || stable == Gesture.TWO_FINGERS)
                framesToConfirm * 3 + 1
            else
                framesToConfirm * 2

        else -> framesToConfirm
    }

    fun process(hand: Hand?, imageWidth: Int, imageHeight: Int): State {
        val result = if (hand == null) {
            GestureClassifier.Result(Gesture.NONE, 0f)
        } else {
            GestureClassifier.classify(hand, imageWidth, imageHeight, previous = stable)
        }

        if (result.gesture == candidate) {
            candidateCount++
        } else {
            candidate = result.gesture
            candidateCount = 1
        }

        if (candidateCount >= needed(candidate)) stable = candidate

        return State(
            raw = result.gesture,
            confidence = result.confidence,
            stable = stable,
            pinchDist = result.pinchDist,
            indexOpen = result.indexOpen,
        )
    }
}
