package com.airtouch.ui

import androidx.compose.foundation.Canvas
import androidx.compose.ui.Modifier
import androidx.compose.runtime.Composable
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import com.airtouch.model.HandFrame
import com.airtouch.ui.theme.Cyan
import com.airtouch.ui.theme.Magenta
import kotlin.math.min

private val CONNECTIONS = listOf(
    0 to 1, 1 to 2, 2 to 3, 3 to 4,
    0 to 5, 5 to 6, 6 to 7, 7 to 8,
    5 to 9, 9 to 10, 10 to 11, 11 to 12,
    9 to 13, 13 to 14, 14 to 15, 15 to 16,
    13 to 17, 17 to 18, 18 to 19, 19 to 20,
    0 to 17,
)
private val FINGERTIPS = setOf(4, 8, 12, 16, 20)

/**
 * Desenha o esqueleto da mão sobre o preview (que usa FIT_CENTER, com o mesmo
 * quadro 4:3 usado na análise).
 */
@Composable
fun HandOverlay(frame: HandFrame?, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val f = frame ?: return@Canvas
        if (f.imageWidth <= 0 || f.imageHeight <= 0) return@Canvas

        val scale = min(size.width / f.imageWidth, size.height / f.imageHeight)
        val offsetX = (size.width - f.imageWidth * scale) / 2f
        val offsetY = (size.height - f.imageHeight * scale) / 2f

        f.hands.forEach { hand ->
            val pts = hand.landmarks.map {
                Offset(offsetX + it.x * f.imageWidth * scale, offsetY + it.y * f.imageHeight * scale)
            }
            CONNECTIONS.forEach { (a, b) ->
                drawLine(
                    color = Cyan.copy(alpha = 0.85f),
                    start = pts[a],
                    end = pts[b],
                    strokeWidth = 4f,
                    cap = StrokeCap.Round,
                )
            }
            pts.forEachIndexed { i, p ->
                val tip = i in FINGERTIPS
                drawCircle(
                    color = if (tip) Magenta else Color.White,
                    radius = if (tip) 10f else 6f,
                    center = p,
                )
            }
        }
    }
}
