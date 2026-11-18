package com.airtouch.gesture

import com.airtouch.model.Hand
import com.airtouch.model.LM
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.min

/**
 * Classificador por regras geométricas sobre os 21 pontos da mão.
 * Kotlin puro (sem Android) para poder ser testado na JVM.
 *
 * Premissas: a mão está "em pé" na imagem já rotacionada (necessário para
 * diferenciar polegar para cima/baixo) e a palma/dorso está voltada para a câmera.
 */
object GestureClassifier {

    /**
     * Fator de calibração da pinça (1 = padrão). A calibração do usuário (aba Personalizar)
     * aumenta ou diminui os limites de pinça conforme o jeito dele de fazer o gesto.
     */
    @Volatile var pinchScale: Float = 1f


    data class Result(
        val gesture: Gesture,
        val confidence: Float,
        /** Distância polegar-indicador (ponta com ponta), em tamanhos de palma. */
        val pinchDist: Float = 0f,
        /** Quão "aberto" está o indicador: ponta até a base do dedo, em tamanhos de palma. */
        val indexOpen: Float = 0f,
    )

    // Um dedo conta como estendido se a ponta está mais longe do pulso que a articulação PIP.
    private const val EXTENDED_RATIO = 1.10f

    // Polegar estendido: ponta afastada da base do indicador (em tamanhos de palma).
    private const val THUMB_EXTENDED_REACH = 0.60f

    // Polegar "vertical": componente vertical do vetor MCP->ponta acima de 70%.
    private const val THUMB_VERTICAL = 0.70f

    // Pinça: distância entre as pontas do polegar e do indicador, em tamanhos de palma.
    // Histerese: para ENTRAR precisa chegar a 0,30; para SAIR precisa passar de 0,50.
    private const val PINCH_ENTER = 0.30f
    private const val PINCH_EXIT = 0.50f

    // Na pinça o indicador fica "aberto" (ponta longe da própria base); no punho ele
    // fica enrolado na palma. Vindo do punho exige-se mais abertura (evita punho virar pinça).
    private const val INDEX_OPEN_ENTER = 0.45f
    private const val INDEX_OPEN_STAY = 0.30f
    private const val INDEX_OPEN_FROM_FIST = 0.60f

    private class Pt(val x: Float, val y: Float)

    /**
     * @param imageWidth/imageHeight dimensões do quadro; usadas para converter as
     * coordenadas normalizadas em pixels e não distorcer distâncias.
     * @param previous gesto estável anterior (usado na histerese da pinça).
     */
    fun classify(
        hand: Hand,
        imageWidth: Int = 1,
        imageHeight: Int = 1,
        previous: Gesture = Gesture.NONE,
    ): Result {
        val w = imageWidth.toFloat()
        val h = imageHeight.toFloat()
        val p = hand.landmarks.map { Pt(it.x * w, it.y * h) }

        fun d(a: Int, b: Int) = hypot(p[a].x - p[b].x, p[a].y - p[b].y)

        val palmSize = d(LM.WRIST, LM.MIDDLE_MCP)
        if (palmSize < 1e-3f) return Result(Gesture.UNKNOWN, 0f)

        val pinchDist = d(LM.THUMB_TIP, LM.INDEX_TIP) / palmSize
        val indexOpen = d(LM.INDEX_TIP, LM.INDEX_MCP) / palmSize

        fun res(g: Gesture, c: Float) = Result(g, c, pinchDist, indexOpen)

        fun ratio(pip: Int, tip: Int): Float {
            val base = d(pip, LM.WRIST)
            return if (base < 1e-6f) 0f else d(tip, LM.WRIST) / base
        }

        val ratios = floatArrayOf(
            ratio(LM.INDEX_PIP, LM.INDEX_TIP),
            ratio(LM.MIDDLE_PIP, LM.MIDDLE_TIP),
            ratio(LM.RING_PIP, LM.RING_TIP),
            ratio(LM.PINKY_PIP, LM.PINKY_TIP),
        )
        val extended = ratios.map { it > EXTENDED_RATIO }
        val extendedCount = extended.count { it }

        // Confiança heurística: quão longe cada dedo está do limiar de decisão.
        fun confidence(values: List<Float>): Float {
            val margins = values.map { min(1f, abs(it - EXTENDED_RATIO) / 0.4f) }
            return 0.5f + 0.5f * margins.average().toFloat()
        }

        // --- Pinça (verificada primeiro: o indicador dobrado poderia parecer punho) ---
        val pinchLimit = (if (previous == Gesture.PINCH) PINCH_EXIT else PINCH_ENTER) * pinchScale
        val openLimit = when (previous) {
            Gesture.PINCH -> INDEX_OPEN_STAY
            Gesture.FIST -> INDEX_OPEN_FROM_FIST
            else -> INDEX_OPEN_ENTER
        }
        if (pinchDist < pinchLimit && indexOpen > openLimit) {
            val c = 0.5f + 0.5f * (1f - pinchDist / pinchLimit).coerceIn(0f, 1f)
            return res(Gesture.PINCH, c)
        }

        // --- Polegar ---
        val thumbReach = d(LM.THUMB_TIP, LM.INDEX_MCP) / palmSize
        val thumbExtended = thumbReach > THUMB_EXTENDED_REACH
        val tvx = p[LM.THUMB_TIP].x - p[LM.THUMB_MCP].x
        val tvy = p[LM.THUMB_TIP].y - p[LM.THUMB_MCP].y
        val tlen = hypot(tvx, tvy)
        val thumbVertical = tlen > 1e-6f && abs(tvy) / tlen > THUMB_VERTICAL

        return when {
            extendedCount == 0 -> {
                val c = confidence(ratios.toList())
                if (thumbExtended && thumbVertical) {
                    res(if (tvy < 0f) Gesture.THUMB_UP else Gesture.THUMB_DOWN, c)
                } else {
                    res(Gesture.FIST, c)
                }
            }

            extendedCount == 4 -> res(Gesture.OPEN_PALM, confidence(ratios.toList()))

            extended[0] && extendedCount == 1 ->
                res(Gesture.POINTING, confidence(ratios.toList()))

            // Indicador + médio estendidos, apontando para cima.
            extended[0] && extended[1] && !extended[2] && !extended[3] &&
                p[LM.INDEX_TIP].y < p[LM.WRIST].y ->
                res(Gesture.TWO_FINGERS, confidence(ratios.toList()))

            else -> res(Gesture.UNKNOWN, 0.3f)
        }
    }
}
