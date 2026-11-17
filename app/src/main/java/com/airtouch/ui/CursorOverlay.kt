package com.airtouch.ui

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.airtouch.AirTouchViewModel
import com.airtouch.mouse.VirtualMouse
import com.airtouch.ui.theme.Cyan
import com.airtouch.ui.theme.Magenta

private val Laser = Color(0xFFFF1744)

/**
 * Cursor virtual desenhado sobre TODA a janela do app. Precisa ocupar exatamente a
 * mesma área onde os toques são injetados (a view de conteúdo da Activity).
 *
 * Com [laser] = true (apresentação), o cursor de movimento vira um ponteiro laser vermelho.
 */
@Composable
fun LiveCursorOverlay(
    viewModel: AirTouchViewModel,
    modifier: Modifier = Modifier,
    laser: Boolean = false,
) {
    val cursor by viewModel.cursor.collectAsStateWithLifecycle()
    Canvas(modifier = modifier) {
        val c = cursor ?: return@Canvas
        val center = Offset(c.x * size.width, c.y * size.height)
        when (c.mode) {
            VirtualMouse.Mode.MOVE -> {
                if (laser) {
                    drawCircle(Laser.copy(alpha = 0.20f), radius = 34f, center = center)
                    drawCircle(Laser.copy(alpha = 0.45f), radius = 20f, center = center)
                    drawCircle(Laser, radius = 11f, center = center)
                    drawCircle(Color.White, radius = 3.5f, center = center)
                } else {
                    drawCircle(Cyan.copy(alpha = 0.25f), radius = 30f, center = center)
                    drawCircle(Cyan, radius = 30f, center = center, style = Stroke(width = 4f))
                    drawCircle(Color.White, radius = 6f, center = center)
                }
            }

            VirtualMouse.Mode.PRESS -> {
                drawCircle(Magenta.copy(alpha = 0.5f), radius = 18f, center = center)
                drawCircle(Magenta, radius = 18f, center = center, style = Stroke(width = 5f))
            }

            VirtualMouse.Mode.DRAG -> {
                drawCircle(Magenta.copy(alpha = 0.35f), radius = 38f, center = center)
                drawCircle(Magenta, radius = 38f, center = center, style = Stroke(width = 5f))
                drawCircle(Color.White, radius = 7f, center = center)
            }

            VirtualMouse.Mode.SCROLL -> {
                // Duas setas verticais.
                val a = 34f
                drawLine(Cyan, center.copy(y = center.y - a), center.copy(y = center.y + a), strokeWidth = 6f, cap = StrokeCap.Round)
                drawLine(Cyan, center.copy(y = center.y - a), Offset(center.x - 14f, center.y - a + 14f), strokeWidth = 6f, cap = StrokeCap.Round)
                drawLine(Cyan, center.copy(y = center.y - a), Offset(center.x + 14f, center.y - a + 14f), strokeWidth = 6f, cap = StrokeCap.Round)
                drawLine(Cyan, center.copy(y = center.y + a), Offset(center.x - 14f, center.y + a - 14f), strokeWidth = 6f, cap = StrokeCap.Round)
                drawLine(Cyan, center.copy(y = center.y + a), Offset(center.x + 14f, center.y + a - 14f), strokeWidth = 6f, cap = StrokeCap.Round)
            }
        }
    }
}
