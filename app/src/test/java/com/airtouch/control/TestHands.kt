package com.airtouch.control

import com.airtouch.model.Hand
import com.airtouch.model.LM
import com.airtouch.model.Landmark

/** Mãos sintéticas para os testes (mão em pé, coordenadas normalizadas). */
object TestHands {
    fun palm(cx: Float = 0.5f, cy: Float = 0.7f, score: Float = 0.9f): Hand {
        val pts = Array(LM.COUNT) { Landmark(cx, cy) }
        // pulso e base do médio definem o "centro" usado pelo zoom
        pts[LM.WRIST] = Landmark(cx, cy)
        pts[LM.MIDDLE_MCP] = Landmark(cx, cy - 0.2f)
        for (i in 1 until LM.COUNT) {
            if (i != LM.MIDDLE_MCP) pts[i] = Landmark(cx + (i % 5) * 0.01f, cy - 0.05f - i * 0.01f)
        }
        return Hand(pts.toList(), score = score)
    }
}
