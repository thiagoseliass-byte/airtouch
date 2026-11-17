package com.airtouch.gesture

/**
 * Calibração da pinça: o usuário faz a pinça do jeito dele por alguns segundos; usamos a
 * mediana da distância polegar-indicador para ajustar os limites do reconhecimento.
 * Kotlin puro (testável na JVM).
 */
class PinchCalibration(private val needed: Int = 25) {
    private val samples = ArrayList<Float>()

    val count: Int get() = samples.size
    val done: Boolean get() = samples.size >= needed

    /** Aceita só distâncias plausíveis de uma pinça (em tamanhos de palma). */
    fun add(pinchDist: Float) {
        if (pinchDist > 0f && pinchDist < 0.6f) samples += pinchDist
    }

    /** Fator para [GestureClassifier.pinchScale], ou null se ainda faltam amostras. */
    fun result(): Float? {
        if (samples.size < needed) return null
        val sorted = samples.sorted()
        val median = sorted[sorted.size / 2]
        // limite de entrada = 1,6x a pinça típica do usuário (entre 0,22 e 0,45 palma)
        val enter = (median * 1.6f).coerceIn(0.22f, 0.45f)
        return (enter / BASE_ENTER).coerceIn(0.7f, 1.6f)
    }

    companion object {
        const val BASE_ENTER = 0.30f
    }
}
