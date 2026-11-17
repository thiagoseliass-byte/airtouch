package com.airtouch.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val BgDark = Color(0xFF070B14)
val PanelDark = Color(0xFF0E1525)
val Cyan = Color(0xFF00E5FF)
val Magenta = Color(0xFFFF2D95)
val Alert = Color(0xFFFF3B3B)
val TextDim = Color(0xFF8FA3BF)

@Composable
fun AirTouchTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Cyan,
            secondary = Magenta,
            background = BgDark,
            surface = PanelDark,
            onSurface = Color.White,
            onBackground = Color.White,
        ),
        content = content,
    )
}
