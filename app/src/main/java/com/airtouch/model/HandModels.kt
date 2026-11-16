package com.airtouch.model

/** Ponto normalizado (0..1) no quadro analisado. y cresce para baixo. */
data class Landmark(val x: Float, val y: Float, val z: Float = 0f)

/** Uma mão detectada: 21 pontos no padrão do MediaPipe. */
data class Hand(
    val landmarks: List<Landmark>,
    val label: String = "?",
    val score: Float = 0f,
) {
    init {
        require(landmarks.size == LM.COUNT) { "Uma mão precisa ter ${LM.COUNT} pontos" }
    }
}

/** Resultado de um quadro: mãos + dimensões do quadro analisado (já rotacionado/espelhado). */
data class HandFrame(
    val hands: List<Hand>,
    val imageWidth: Int,
    val imageHeight: Int,
    val latencyMs: Long = 0,
    val timestampMs: Long = 0,
)

/** Índices dos pontos da mão (MediaPipe Hand Landmarker). */
object LM {
    const val COUNT = 21
    const val WRIST = 0
    const val THUMB_CMC = 1
    const val THUMB_MCP = 2
    const val THUMB_IP = 3
    const val THUMB_TIP = 4
    const val INDEX_MCP = 5
    const val INDEX_PIP = 6
    const val INDEX_DIP = 7
    const val INDEX_TIP = 8
    const val MIDDLE_MCP = 9
    const val MIDDLE_PIP = 10
    const val MIDDLE_DIP = 11
    const val MIDDLE_TIP = 12
    const val RING_MCP = 13
    const val RING_PIP = 14
    const val RING_DIP = 15
    const val RING_TIP = 16
    const val PINKY_MCP = 17
    const val PINKY_PIP = 18
    const val PINKY_DIP = 19
    const val PINKY_TIP = 20
}
