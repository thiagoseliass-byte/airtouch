package com.airtouch.control

import com.airtouch.model.Hand
import com.airtouch.model.LM
import kotlin.math.hypot
import kotlin.math.sqrt

/**
 * Gesto gravado pelo usuário: a "forma da mão" (poses) guardada como 40 números
 * (20 pontos x,y relativos ao pulso e ao tamanho da palma).
 */
data class CustomGesture(
    val id: String,
    val name: String,
    val template: List<Float>,
    /** 0 = exige pose muito parecida; 1 = aceita poses bem diferentes. */
    val tolerance: Float = 0.5f,
)

/**
 * Reconhece os gestos personalizados comparando a pose atual com cada gravação.
 * Dispara uma vez quando a pose é mantida por [framesToFire] quadros e só dispara de
 * novo depois de a pose sair por [framesToRearm] quadros. Kotlin puro (testável).
 */
class CustomGestureEngine(
    private val framesToFire: Int = 8,
    private val framesToRearm: Int = 6,
) {
    data class Match(val id: String, val name: String, val distance: Float, val limit: Float) {
        val matched: Boolean get() = distance <= limit
    }

    @Volatile var gestures: List<CustomGesture> = emptyList()

    /** O gesto gravado mais parecido com a mão atual (mesmo que ainda não "bata"). */
    var live: Match? = null
        private set

    private var holdId: String? = null
    private var holdCount = 0
    private var armed = true
    private var missCount = 0

    fun reset() {
        holdId = null
        holdCount = 0
        armed = true
        missCount = 0
        live = null
    }

    fun nearest(hand: Hand, imageWidth: Int, imageHeight: Int): Match? {
        val f = features(hand, imageWidth, imageHeight) ?: return null
        var best: Match? = null
        for (g in gestures) {
            if (g.template.size != f.size) continue
            val d = distance(f, g.template)
            val m = Match(g.id, g.name, d, limitFor(g.tolerance))
            val b = best
            if (b == null || (m.distance - m.limit) < (b.distance - b.limit)) best = m
        }
        return best
    }

    /** Devolve o id do gesto no quadro em que ele é confirmado, senão null. */
    fun update(hand: Hand?, imageWidth: Int, imageHeight: Int): String? {
        if (hand == null || gestures.isEmpty()) {
            live = null
            return miss()
        }
        val m = nearest(hand, imageWidth, imageHeight)
        live = m
        if (m == null || !m.matched) return miss()

        missCount = 0
        if (m.id == holdId) holdCount++ else {
            holdId = m.id
            holdCount = 1
        }
        if (armed && holdCount >= framesToFire) {
            armed = false
            return m.id
        }
        return null
    }

    private fun miss(): String? {
        holdId = null
        holdCount = 0
        missCount++
        if (missCount >= framesToRearm) armed = true
        return null
    }

    companion object {
        fun limitFor(tolerance: Float): Float = 0.10f + 0.20f * tolerance.coerceIn(0f, 1f)

        /** 40 números descrevendo a forma da mão, ou null se a mão for inválida. */
        fun features(hand: Hand, imageWidth: Int, imageHeight: Int): FloatArray? {
            val w = imageWidth.toFloat()
            val h = imageHeight.toFloat()
            val lm = hand.landmarks
            val wx = lm[LM.WRIST].x * w
            val wy = lm[LM.WRIST].y * h
            val palm = hypot(lm[LM.MIDDLE_MCP].x * w - wx, lm[LM.MIDDLE_MCP].y * h - wy)
            if (palm < 1e-3f) return null
            val out = FloatArray(40)
            for (i in 1..20) {
                out[(i - 1) * 2] = (lm[i].x * w - wx) / palm
                out[(i - 1) * 2 + 1] = (lm[i].y * h - wy) / palm
            }
            return out
        }

        /** Raiz da média das distâncias entre os 20 pontos, em tamanhos de palma. */
        fun distance(a: FloatArray, b: List<Float>): Float {
            var sum = 0f
            for (i in 0 until 20) {
                val dx = a[i * 2] - b[i * 2]
                val dy = a[i * 2 + 1] - b[i * 2 + 1]
                sum += dx * dx + dy * dy
            }
            return sqrt(sum / 20f)
        }

        fun average(samples: List<FloatArray>): List<Float> {
            if (samples.isEmpty()) return emptyList()
            return List(40) { i -> samples.map { it[i] }.average().toFloat() }
        }
    }
}
