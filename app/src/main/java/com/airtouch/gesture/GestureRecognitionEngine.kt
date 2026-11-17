package com.airtouch.gesture

import com.airtouch.model.Hand

/**
 * Transforma o gesto "bruto" de cada quadro em um gesto ESTÁVEL, evitando
 * acionamentos acidentais (debounce): um gesto só vira o gesto atual depois de
 * aparecer em vários quadros seguidos, e só some depois de vários quadros sem mão.
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
    )

    private var stable = Gesture.NONE
    private var candidate = Gesture.NONE
    private var candidateCount = 0

    fun reset() {
        stable = Gesture.NONE
        candidate = Gesture.NONE
        candidateCount = 0
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

        val needed = if (candidate == Gesture.NONE) framesToRelease else framesToConfirm
        if (candidateCount >= needed) stable = candidate

        return State(raw = result.gesture, confidence = result.confidence, stable = stable)
    }
}
