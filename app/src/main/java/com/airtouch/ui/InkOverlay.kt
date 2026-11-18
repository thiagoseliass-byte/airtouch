package com.airtouch.ui

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.airtouch.AirTouchViewModel
import com.airtouch.ui.theme.Cyan

/**
 * Desenha os traços da escrita no ar. Os pontos estão em "unidades de largura":
 * x em 0..1 e y na mesma escala (y = pixels / largura da área).
 */
@Composable
fun InkOverlay(viewModel: AirTouchViewModel, modifier: Modifier = Modifier, color: Color = Cyan) {
    val version by viewModel.inkVersion.collectAsStateWithLifecycle()
    Canvas(modifier = modifier) {
        @Suppress("UNUSED_EXPRESSION") version // relê quando o desenho muda
        val w = size.width
        for (stroke in viewModel.writing.snapshot()) {
            if (stroke.size < 2) continue
            val path = Path()
            stroke.forEachIndexed { i, p ->
                val x = p.x * w
                val y = p.y * w
                if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            drawPath(
                path,
                color,
                style = Stroke(width = 7f, cap = StrokeCap.Round, join = StrokeJoin.Round),
            )
        }
        val last = viewModel.writing.snapshot().lastOrNull()?.lastOrNull()
        if (viewModel.writing.penDown && last != null) {
            drawCircle(Color.White, radius = 6f, center = Offset(last.x * w, last.y * w))
        }
    }
}
