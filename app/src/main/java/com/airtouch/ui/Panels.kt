package com.airtouch.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.airtouch.AirTouchUiState
import com.airtouch.gesture.Gesture
import com.airtouch.ui.theme.Alert
import com.airtouch.ui.theme.BgDark
import com.airtouch.ui.theme.Cyan
import com.airtouch.ui.theme.Magenta
import com.airtouch.ui.theme.PanelDark
import com.airtouch.ui.theme.TextDim
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun SensitivityControl(value: Float, onChange: (Float) -> Unit, compact: Boolean = false) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Sensibilidade", color = TextDim, fontSize = if (compact) 12.sp else 13.sp)
        Slider(
            value = value,
            onValueChange = onChange,
            modifier = Modifier.weight(1f).height(if (compact) 28.dp else 36.dp),
            colors = SliderDefaults.colors(thumbColor = Cyan, activeTrackColor = Cyan),
        )
        Text("${(value * 100).toInt()}%", color = Color.White, fontSize = 12.sp)
    }
}

@Composable
fun QuickPanel(state: AirTouchUiState, modifier: Modifier) {
    Column(
        modifier
            .clip(RoundedCornerShape(20.dp))
            .background(PanelDark.copy(alpha = 0.95f))
            .border(1.dp, Cyan, RoundedCornerShape(20.dp))
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text("PAINEL DE CONTROLE", color = Cyan, fontWeight = FontWeight.Black, letterSpacing = 2.sp)
        Text("Sensibilidade ${(state.sensitivity * 100).toInt()}%  ·  ${state.fps} FPS", color = Color.White, fontSize = 14.sp)
        Text("Punho = pausar  ·  Indicador = cursor  ·  Pinça = clique", color = TextDim, fontSize = 12.sp)
        Text("Varrer a mão aberta = slides / páginas / rolagem", color = TextDim, fontSize = 12.sp)
    }
}

@Composable
fun ControlPanel(
    state: AirTouchUiState,
    onSensitivity: (Float) -> Unit,
    onInvertScroll: (Boolean) -> Unit,
    modifier: Modifier,
) {
    val shape = RoundedCornerShape(24.dp)
    Column(
        modifier = modifier
            .clip(shape)
            .background(PanelDark)
            .border(1.dp, Cyan.copy(alpha = 0.2f), shape)
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text("Fase 4 · Escrita no ar, duas mãos, gestos próprios e jogos", color = TextDim, fontSize = 13.sp)

        // Status
        val (statusText, statusColor) = when {
            !state.controlEnabled -> "Controle desligado" to TextDim
            state.error != null -> state.error to Alert
            !state.modelReady -> "Carregando modelo…" to Magenta
            state.locked -> "Pausado (punho fechado)" to Alert
            state.handCount == 0 -> "Procurando mão…" to Magenta
            else -> "Mão detectada" to Cyan
        }
        Text(statusText ?: "", color = statusColor, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)

        // Gesto atual
        val gesture = state.stableGesture
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(BgDark)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text("GESTO ATUAL", color = TextDim, fontSize = 11.sp, letterSpacing = 2.sp)
            Text(
                text = if (gesture == Gesture.NONE) "—" else gesture.label,
                color = if (gesture.isCommand) Cyan else Color.White,
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = if (gesture.isCommand) "Ação: ${gesture.action}" else " ",
                color = TextDim,
                fontSize = 13.sp,
            )
            LinearProgressIndicator(
                progress = { state.confidence.coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                color = Cyan,
                trackColor = Color.White.copy(alpha = 0.08f),
            )
            Text(
                "Confiança ${(state.confidence * 100).toInt()}% · instantâneo: ${state.rawGesture.label}",
                color = TextDim,
                fontSize = 11.sp,
            )
        }

        // Métricas
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Metric("FPS", state.fps.toString(), Modifier.weight(1f))
            Metric("Latência", "${state.latencyMs} ms", Modifier.weight(1f))
            Metric("Mãos", state.handCount.toString(), Modifier.weight(1f))
        }

        // Diagnóstico da pinça (ajuda a calibrar: me envie estes números se a pinça falhar)
        Text(
            "Pinça: distância ${"%.2f".format(state.pinchDist)} (pinça < 0,30) · indicador aberto ${"%.2f".format(state.indexOpen)} (pinça > 0,45)" +
                if (state.zoomActive) " · ZOOM com 2 mãos" else "",
            color = TextDim,
            fontSize = 11.sp,
        )

        // Ajustes
        SensitivityControl(state.sensitivity, onSensitivity)
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Inverter sentido da rolagem", color = TextDim, fontSize = 13.sp)
            Switch(checked = state.invertScroll, onCheckedChange = onInvertScroll)
        }

        // Ações e gestos recentes
        Text("AÇÕES RECENTES", color = TextDim, fontSize = 11.sp, letterSpacing = 2.sp)
        val fmt = remember { SimpleDateFormat("HH:mm:ss", Locale.getDefault()) }
        if (state.log.isEmpty()) {
            Text("Nenhuma ação ainda", color = TextDim, fontSize = 13.sp)
        } else {
            state.log.forEach { e ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(e.text, color = Color.White, fontSize = 14.sp)
                    Text(fmt.format(Date(e.timeMs)), color = TextDim, fontSize = 13.sp)
                }
            }
        }

        Text("GESTOS RECENTES", color = TextDim, fontSize = 11.sp, letterSpacing = 2.sp)
        if (state.recent.isEmpty()) {
            Text("Nenhum gesto ainda", color = TextDim, fontSize = 13.sp)
        } else {
            state.recent.forEach { e ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(e.gesture.label, color = Color.White, fontSize = 14.sp)
                    Text(fmt.format(Date(e.timeMs)), color = TextDim, fontSize = 13.sp)
                }
            }
        }

        Text("GUIA RÁPIDO", color = TextDim, fontSize = 11.sp, letterSpacing = 2.sp)
        Text(
            "☝ Indicador: move o cursor\n" +
                "🤏 Pinça rápida: clique (duas = duplo clique)\n" +
                "🤏 Pinça segurada: arrastar\n" +
                "✌ Dois dedos: rolar (mova a mão para cima/baixo)\n" +
                "🖐 Varrer mão aberta ← →: slide/página anterior/seguinte\n" +
                "🖐 Varrer mão aberta ↑ ↓: rolar\n" +
                "✊ Punho: pausa · 🖐 Mão aberta: retoma\n" +
                "👐 Duas mãos afastando/aproximando: zoom\n" +
                "☝ Desenhar um círculo no ar com o indicador: menu de ações\n" +
                "👍 Confirmar · 👎 Cancelar / sair da apresentação\n\n" +
                "Abas: Mouse virtual (teste), Documentos (PDF), Apresentações (tela cheia com laser), " +
                "Escrita no ar, Jogos, Personalizar (perfis e gestos próprios) e Controle global.",
            color = TextDim,
            fontSize = 12.sp,
            lineHeight = 18.sp,
        )

        Text(
            "Processamento 100% local. Nenhum vídeo é gravado ou enviado.",
            color = TextDim,
            fontSize = 11.sp,
        )
    }
}

@Composable
private fun Metric(label: String, value: String, modifier: Modifier) {
    Column(
        modifier
            .clip(RoundedCornerShape(12.dp))
            .background(BgDark)
            .padding(10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(value, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        Text(label, color = TextDim, fontSize = 11.sp)
    }
}
