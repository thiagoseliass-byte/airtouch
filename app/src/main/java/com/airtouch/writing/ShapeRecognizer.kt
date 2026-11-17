package com.airtouch.writing

import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Transforma um traço à mão livre em forma perfeita: linha, círculo/elipse,
 * triângulo ou retângulo. Devolve null se não parecer nenhuma. Kotlin puro.
 */
object ShapeRecognizer {

    fun snap(stroke: List<Pt>): List<Pt>? {
        if (stroke.size < 8) return null
        val t0 = stroke.first().t
        val t1 = stroke.last().t
        val length = pathLength(stroke)
        if (length < 0.08f) return null

        val a = stroke.first()
        val b = stroke.last()
        val gap = hypot(a.x - b.x, a.y - b.y)

        // ---- linha
        if (gap > 0.6f * length) {
            val dev = stroke.maxOf { distToLine(it, a, b) }
            return if (dev < 0.08f * gap) line(a, b, t0, t1) else null
        }

        // ---- fechada
        if (gap > 0.25f * length) return null

        val minX = stroke.minOf { it.x }
        val maxX = stroke.maxOf { it.x }
        val minY = stroke.minOf { it.y }
        val maxY = stroke.maxOf { it.y }
        val w = maxX - minX
        val h = maxY - minY
        if (w < 0.03f || h < 0.03f) return null

        val corners = corners(stroke, epsilon = 0.045f * length)
        return when (corners) {
            3 -> triangle(stroke, t0, t1)
            4 -> rectangle(minX, minY, maxX, maxY, t0, t1)
            else -> {
                val cx = stroke.map { it.x }.average().toFloat()
                val cy = stroke.map { it.y }.average().toFloat()
                // distância normalizada ao elipse inscrito no retângulo envolvente
                val rx = w / 2f
                val ry = h / 2f
                val ecx = (minX + maxX) / 2f
                val ecy = (minY + maxY) / 2f
                var sum = 0f
                for (p in stroke) {
                    val nx = (p.x - ecx) / rx
                    val ny = (p.y - ecy) / ry
                    sum += abs(sqrt(nx * nx + ny * ny) - 1f)
                }
                if (sum / stroke.size < 0.14f && cx.isFinite() && cy.isFinite()) {
                    ellipse(ecx, ecy, rx, ry, t0, t1)
                } else null
            }
        }
    }

    // ---------------------------------------------------------------- geometria

    private fun pathLength(s: List<Pt>): Float {
        var l = 0f
        for (i in 1 until s.size) l += hypot(s[i].x - s[i - 1].x, s[i].y - s[i - 1].y)
        return l
    }

    private fun distToLine(p: Pt, a: Pt, b: Pt): Float {
        val dx = b.x - a.x
        val dy = b.y - a.y
        val len = hypot(dx, dy)
        if (len < 1e-6f) return hypot(p.x - a.x, p.y - a.y)
        return abs(dy * p.x - dx * p.y + b.x * a.y - b.y * a.x) / len
    }

    /** Número de cantos do polígono simplificado (Douglas-Peucker em curva fechada). */
    internal fun corners(s: List<Pt>, epsilon: Float): Int {
        // abre a curva no ponto mais distante do início
        var far = 0
        var best = -1f
        for (i in s.indices) {
            val d = hypot(s[i].x - s[0].x, s[i].y - s[0].y)
            if (d > best) {
                best = d
                far = i
            }
        }
        val first = dp(s.subList(0, far + 1), epsilon)
        val second = dp(s.subList(far, s.size) + listOf(s[0]), epsilon)
        // pontos únicos: first + second sem duplicar as junções
        return (first.size - 1) + (second.size - 1)
    }

    private fun dp(pts: List<Pt>, eps: Float): List<Pt> {
        if (pts.size < 3) return pts
        var idx = 0
        var dmax = 0f
        val a = pts.first()
        val b = pts.last()
        for (i in 1 until pts.size - 1) {
            val d = distToLine(pts[i], a, b)
            if (d > dmax) {
                dmax = d
                idx = i
            }
        }
        if (dmax <= eps) return listOf(a, b)
        val l = dp(pts.subList(0, idx + 1), eps)
        val r = dp(pts.subList(idx, pts.size), eps)
        return l.dropLast(1) + r
    }

    private fun interp(a: Pt, b: Pt, steps: Int, t0: Long, t1: Long, startIdx: Int, total: Int): List<Pt> =
        List(steps) { i ->
            val f = i / steps.toFloat()
            val k = startIdx + i
            Pt(a.x + (b.x - a.x) * f, a.y + (b.y - a.y) * f, t0 + (t1 - t0) * k / total)
        }

    private fun polygon(vs: List<Pt>, t0: Long, t1: Long): List<Pt> {
        val steps = 12
        val total = steps * vs.size
        val out = ArrayList<Pt>()
        for (i in vs.indices) {
            out += interp(vs[i], vs[(i + 1) % vs.size], steps, t0, t1, i * steps, total)
        }
        out += Pt(vs[0].x, vs[0].y, t1)
        return out
    }

    private fun line(a: Pt, b: Pt, t0: Long, t1: Long): List<Pt> =
        interp(a, b, 16, t0, t1, 0, 16) + Pt(b.x, b.y, t1)

    private fun rectangle(x0: Float, y0: Float, x1: Float, y1: Float, t0: Long, t1: Long): List<Pt> =
        polygon(listOf(Pt(x0, y0, t0), Pt(x1, y0, t0), Pt(x1, y1, t0), Pt(x0, y1, t0)), t0, t1)

    private fun triangle(s: List<Pt>, t0: Long, t1: Long): List<Pt> {
        // usa o simplificado para achar os 3 vértices
        var far = 0
        var best = -1f
        for (i in s.indices) {
            val d = hypot(s[i].x - s[0].x, s[i].y - s[0].y)
            if (d > best) { best = d; far = i }
        }
        val eps = 0.045f * pathLength(s)
        val first = dp(s.subList(0, far + 1), eps)
        val second = dp(s.subList(far, s.size) + listOf(s[0]), eps)
        val vs = (first.dropLast(1) + second.dropLast(1))
        return if (vs.size == 3) polygon(vs, t0, t1) else polygon(vs.take(3), t0, t1)
    }

    private fun ellipse(cx: Float, cy: Float, rx: Float, ry: Float, t0: Long, t1: Long): List<Pt> {
        val n = 48
        return List(n + 1) { i ->
            val ang = 2.0 * Math.PI * i / n
            Pt(cx + rx * cos(ang).toFloat(), cy + ry * sin(ang).toFloat(), t0 + (t1 - t0) * i / n)
        }
    }

    @Suppress("unused")
    private fun angle(a: Pt, b: Pt) = atan2(b.y - a.y, b.x - a.x)
}
