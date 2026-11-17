package com.airtouch.ui

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.airtouch.ui.theme.BgDark
import com.airtouch.ui.theme.Cyan
import com.airtouch.ui.theme.PanelDark

/** Botão padrão do AIR TOUCH. [primary] = destaque em ciano. */
@Composable
fun AirButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    primary: Boolean = false,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier,
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (primary) Cyan else PanelDark,
            contentColor = if (primary) BgDark else Color.White,
            disabledContainerColor = PanelDark.copy(alpha = 0.5f),
            disabledContentColor = Color.White.copy(alpha = 0.35f),
        ),
    ) {
        Text(text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
    }
}

/** Contorno ciano discreto para botões secundários. */
fun Modifier.airOutline(): Modifier =
    this.then(Modifier.border(1.dp, Cyan.copy(alpha = 0.35f), androidx.compose.foundation.shape.RoundedCornerShape(50)))
