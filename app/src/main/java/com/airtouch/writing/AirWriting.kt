package com.airtouch.writing

/** Ponto de um traço: x,y normalizados (0..1) na área de escrita; t em ms. */
data class Pt(val x: Float, val y: Float, val t: Long)

/**
 * Guarda os traços escritos no ar. A "caneta" desce quando o gesto de pinça é feito e
 * sobe quando ele termina. Kotlin puro (testável na JVM); seguro entre threads.
 */
class AirWriting {
    private val done = ArrayList<List<Pt>>()
    private var current: ArrayList<Pt>? = null

    @get:Synchronized var lastPenUpMs: Long = 0L
        private set

    @get:Synchronized val penDown: Boolean get() = current != null

    @Synchronized fun isEmpty(): Boolean = done.isEmpty() && current == null

    /** Chame a cada quadro. Devolve true se o desenho mudou. */
    @Synchronized fun update(down: Boolean, x: Float, y: Float, t: Long): Boolean {
        val cur = current
        if (down) {
            if (cur == null) {
                current = arrayListOf(Pt(x, y, t))
                return true
            }
            val last = cur.last()
            if (kotlin.math.abs(last.x - x) + kotlin.math.abs(last.y - y) < 0.002f) return false
            cur.add(Pt(x, y, t))
            return true
        }
        if (cur != null) {
            if (cur.size >= 2) done.add(cur.toList())
            current = null
            lastPenUpMs = t
            return true
        }
        return false
    }

    /** Troca o último traço (usado para "endireitar" formas). */
    @Synchronized fun replaceLast(points: List<Pt>) {
        if (done.isNotEmpty()) done[done.lastIndex] = points
    }

    /** Remove os [n] primeiros traços (os que acabaram de ser reconhecidos). */
    @Synchronized fun dropFirst(n: Int) {
        repeat(minOf(n, done.size)) { done.removeAt(0) }
    }

    @Synchronized fun undo() {
        current = null
        if (done.isNotEmpty()) done.removeAt(done.lastIndex)
    }

    @Synchronized fun clear() {
        done.clear()
        current = null
    }

    /** Cópia dos traços, incluindo o que está em andamento. */
    @Synchronized fun snapshot(): List<List<Pt>> {
        val c = current
        return if (c != null) done + listOf(c.toList()) else done.toList()
    }
}
