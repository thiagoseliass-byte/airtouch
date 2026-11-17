package com.airtouch.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.airtouch.AirTouchUiState
import com.airtouch.AirTouchViewModel
import com.airtouch.InkModel
import com.airtouch.WriteMode
import com.airtouch.ui.theme.Alert
import com.airtouch.ui.theme.BgDark
import com.airtouch.ui.theme.Cyan
import com.airtouch.ui.theme.Magenta
import com.airtouch.ui.theme.PanelDark
import com.airtouch.ui.theme.TextDim

/** Escrita no ar: pinça = caneta. Modos: Texto (reconhece), Livre (desenho) e Formas (endireita). */
@Composable
fun WritingScreen(
    viewModel: AirTouchViewModel,
    state: AirTouchUiState,
    rootSize: IntSize,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val shape = RoundedCornerShape(20.dp)

    Row(modifier, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        // ---- folha de escrita
        Box(
            Modifier
                .weight(1f)
                .fillMaxHeight()
                .clip(shape)
                .background(Color(0xFF0B1220))
                .border(1.dp, Cyan.copy(alpha = 0.3f), shape)
                .onGloballyPositioned { c ->
                    val r = c.boundsInRoot()
                    viewModel.setArea(
                        r.left, r.top, r.width, r.height,
                        rootSize.width.toFloat(), rootSize.height.toFloat(),
                    )
                }
        ) {
            InkOverlay(viewModel, Modifier.fillMaxSize())
            Text(
                if (!state.controlEnabled) "Ligue o controle (ATIVAR CONTROLE) para escrever"
                else "Junte polegar e indicador (pinça) e escreva no ar",
                color = TextDim.copy(alpha = 0.6f),
                fontSize = 13.sp,
                modifier = Modifier.align(Alignment.TopStart).padding(14.dp),
            )
        }

        // ---- controles
        Column(
            Modifier.width(300.dp).fillMaxHeight().verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text("MODO", color = TextDim, fontSize = 11.sp, letterSpacing = 2.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                WriteMode.values().forEach { m ->
                    Chip(m.label, state.writeMode == m) { viewModel.setWriteMode(m) }
                }
            }
            Text(
                when (state.writeMode) {
                    WriteMode.TEXT -> "Escreva uma palavra, pare 1,5 s e ela vira texto. 👍 reconhece na hora · varrer → espaço · varrer ← apaga letra."
                    WriteMode.FREE -> "Desenho livre. 👎 apaga tudo."
                    WriteMode.SHAPES -> "Desenhe uma linha, círculo, triângulo ou retângulo: ao soltar, ele é endireitado."
                },
                color = TextDim,
                fontSize = 12.sp,
            )

            if (state.writeMode == WriteMode.TEXT) InkModelBox(viewModel, state)

            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                AirButton("Desfazer", { viewModel.writeUndoStroke() })
                AirButton("Limpar", { viewModel.writeClearInk() })
            }

            if (state.writeMode == WriteMode.TEXT) {
                Text("TEXTO", color = TextDim, fontSize = 11.sp, letterSpacing = 2.sp)
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(110.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(PanelDark)
                        .padding(12.dp)
                ) {
                    Text(
                        if (state.writeText.isEmpty()) "(vazio)" else state.writeText,
                        color = if (state.writeText.isEmpty()) TextDim else Color.White,
                        fontSize = 18.sp,
                        modifier = Modifier.verticalScroll(rememberScrollState()),
                    )
                }
                if (state.inkBusy) Text("Reconhecendo…", color = Magenta, fontSize = 12.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    AirButton("Reconhecer", { viewModel.recognizeInkNow() }, primary = true)
                    AirButton("␣", { viewModel.writeSpace() })
                    AirButton("⌫", { viewModel.writeBackspace() })
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    AirButton("Copiar", {
                        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        cm.setPrimaryClip(ClipData.newPlainText("AIR TOUCH", state.writeText))
                        Toast.makeText(context, "Texto copiado", Toast.LENGTH_SHORT).show()
                    }, enabled = state.writeText.isNotEmpty())
                    AirButton("Apagar texto", { viewModel.writeClearText() }, enabled = state.writeText.isNotEmpty())
                }
            }
            Spacer(Modifier.height(170.dp)) // espaço para a câmera no canto
        }
    }
}

@Composable
private fun InkModelBox(viewModel: AirTouchViewModel, state: AirTouchUiState) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(PanelDark).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        when (state.inkModel) {
            InkModel.CHECKING -> Text("Verificando o modelo de escrita…", color = TextDim, fontSize = 12.sp)
            InkModel.READY -> Text("✔ Reconhecimento de escrita pronto (funciona offline)", color = Cyan, fontSize = 12.sp)
            InkModel.DOWNLOADING -> Text("Baixando o modelo de escrita (uma única vez)…", color = Magenta, fontSize = 12.sp)
            InkModel.MISSING, InkModel.ERROR -> {
                Text(
                    "Para transformar escrita em texto, é preciso baixar o modelo de escrita (cerca de 20 MB) UMA vez, " +
                        "usando internet. Depois disso tudo funciona sem internet.",
                    color = Color.White,
                    fontSize = 12.sp,
                )
                state.inkMessage?.let { Text(it, color = Alert, fontSize = 12.sp) }
                AirButton("Baixar modelo", { viewModel.downloadInkModel() }, primary = true)
            }
        }
    }
}

@Composable
internal fun Chip(text: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .clip(RoundedCornerShape(50))
            .background(if (selected) Cyan.copy(alpha = 0.18f) else PanelDark)
            .border(1.dp, if (selected) Cyan else Color.White.copy(alpha = 0.1f), RoundedCornerShape(50))
            .clickable(onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp)
    ) {
        Text(text, color = if (selected) Cyan else Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
    }
}

@Suppress("unused")
private val unusedBg = BgDark
