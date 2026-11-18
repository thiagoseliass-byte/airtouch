package com.airtouch.control

/**
 * Controla a taxa de análise para poupar bateria:
 *  - sem nenhuma mão por [idleAfterMs]: analisa só um quadro a cada [idleIntervalMs] (~5/s);
 *  - com "economia de bateria": no máximo ~15 quadros/s;
 *  - com mão visível: velocidade total.
 * Kotlin puro (testável na JVM).
 */
class AdaptiveRate(
    private val idleAfterMs: Long = 3000,
    private val idleIntervalMs: Long = 200,
    private val saverIntervalMs: Long = 66,
) {
    @Volatile var powerSaver: Boolean = false

    @Volatile private var lastHandMs: Long = Long.MIN_VALUE / 2
    @Volatile private var lastProcessedMs: Long = Long.MIN_VALUE / 2

    /** Chame com cada resultado de detecção. */
    fun onResult(handPresent: Boolean, nowMs: Long) {
        if (handPresent) lastHandMs = nowMs
    }

    val idle: Boolean get() = lastProcessedMs - lastHandMs > idleAfterMs

    /** Devolve true se este quadro deve ser analisado. */
    fun shouldProcess(nowMs: Long): Boolean {
        val interval = when {
            nowMs - lastHandMs > idleAfterMs -> idleIntervalMs
            powerSaver -> saverIntervalMs
            else -> 0L
        }
        if (nowMs - lastProcessedMs < interval) return false
        lastProcessedMs = nowMs
        return true
    }

    fun reset() {
        lastHandMs = Long.MIN_VALUE / 2
        lastProcessedMs = Long.MIN_VALUE / 2
    }
}
