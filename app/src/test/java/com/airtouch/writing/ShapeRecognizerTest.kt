package com.airtouch.writing

import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

class ShapeRecognizerTest {
    private fun path(vs: List<Pair<Float, Float>>, closed: Boolean): List<Pt> {
        val pts = ArrayList<Pt>()
        val n = if (closed) vs.size else vs.size - 1
        var t = 0L
        for (i in 0 until n) {
            val a = vs[i]
            val b = vs[(i + 1) % vs.size]
            for (k in 0 until 10) {
                val f = k / 10f
                pts += Pt(a.first + (b.first - a.first) * f, a.second + (b.second - a.second) * f, t)
                t += 10
            }
        }
        pts += Pt(vs.last().first.takeIf { !closed } ?: vs[0].first, vs.last().second.takeIf { !closed } ?: vs[0].second, t)
        return pts
    }

    @Test fun straightLine() = assertNotNull(ShapeRecognizer.snap(path(listOf(0.1f to 0.1f, 0.6f to 0.2f), false)))

    @Test fun circle() {
        val pts = List(40) { i ->
            val a = 2 * PI * i / 39
            Pt(0.5f + 0.2f * cos(a).toFloat(), 0.5f + 0.2f * sin(a).toFloat(), i * 20L)
        }
        assertNotNull(ShapeRecognizer.snap(pts))
    }

    @Test fun rectangle() =
        assertNotNull(ShapeRecognizer.snap(path(listOf(0.2f to 0.2f, 0.7f to 0.2f, 0.7f to 0.5f, 0.2f to 0.5f), true)))

    @Test fun triangle() =
        assertNotNull(ShapeRecognizer.snap(path(listOf(0.2f to 0.5f, 0.45f to 0.1f, 0.7f to 0.5f), true)))

    @Test fun scribbleIsRejected() {
        val pts = List(30) { i -> Pt(0.3f + 0.1f * ((i * 7) % 5), 0.3f + 0.08f * ((i * 3) % 7), i * 15L) }
        assertNull(ShapeRecognizer.snap(pts))
    }
}
