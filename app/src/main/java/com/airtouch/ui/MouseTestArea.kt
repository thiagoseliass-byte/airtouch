package com.airtouch.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.airtouch.AirTouchUiState
import com.airtouch.control.DemoContent
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
import kotlin.math.roundToInt

/**
 * Área de teste do mouse virtual: botões para clicar, objeto para arrastar,
 * lista para rolar e "slides" para avançar/voltar.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MouseTestArea(
    state: AirTouchUiState,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onSensitivity: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(modifier = modifier) {
        val wide = maxWidth > maxHeight

        val left: @Composable (Modifier) -> Unit = { m ->
            Column(m, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                StatusStrip(state, onSensitivity)
                SlideCard(state.slideIndex, onPrev, onNext)
                ClickTargets()
                DragArea(Modifier.fillMaxWidth().weight(1f))
            }
        }
        val list: @Composable (Modifier) -> Unit = { m -> ScrollList(m) }

        if (wide) {
            Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                left(Modifier.weight(1.2f).fillMaxSize())
                list(Modifier.weight(1f).fillMaxSize())
            }
        } else {
            Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                left(Modifier.fillMaxWidth().weight(1.3f))
                list(Modifier.fillMaxWidth().weight(1f))
            }
        }
    }
}

@Composable
private fun StatusStrip(state: AirTouchUiState, onSensitivity: (Float) -> Unit) {
    val fmt = remember { SimpleDateFormat("HH:mm:ss", Locale.getDefault()) }
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(PanelDark)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(
                text = when {
                    !state.controlEnabled -> "Controle desligado"
                    state.locked -> "PAUSADO — mão aberta para retomar"
                    state.stableGesture == Gesture.NONE -> "Procurando mão…"
                    else -> state.stableGesture.label
                },
                color = if (state.locked) Alert else Cyan,
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
            )
            Text("${state.fps} FPS · ${state.latencyMs} ms", color = TextDim, fontSize = 12.sp)
        }
        SensitivityControl(state.sensitivity, onSensitivity, compact = true)
        val last = state.log.firstOrNull()
        Text(
            text = last?.let { "${fmt.format(Date(it.timeMs))}  ${it.text}" } ?: "Nenhuma ação ainda",
            color = TextDim,
            fontSize = 12.sp,
        )
    }
}

@Composable
private fun SlideCard(index: Int, onPrev: () -> Unit, onNext: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(PanelDark)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Button(
            onClick = onPrev,
            colors = ButtonDefaults.buttonColors(containerColor = Cyan, contentColor = BgDark),
        ) { Text("◀") }
        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                DemoContent.slides[index.coerceIn(0, DemoContent.slides.lastIndex)],
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            )
            Text(
                "Slide ${index + 1} de ${DemoContent.slides.size}  ·  varra a mão aberta ← →",
                color = TextDim,
                fontSize = 12.sp,
            )
        }
        Button(
            onClick = onNext,
            colors = ButtonDefaults.buttonColors(containerColor = Cyan, contentColor = BgDark),
        ) { Text("▶") }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ClickTargets() {
    var clicks by remember { mutableIntStateOf(0) }
    var doubles by remember { mutableIntStateOf(0) }
    var toggled by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(16.dp)

    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(
            Modifier
                .weight(1f)
                .height(72.dp)
                .clip(shape)
                .background(PanelDark)
                .border(1.dp, Cyan.copy(alpha = 0.5f), shape)
                .clickable { clicks++ },
            contentAlignment = Alignment.Center,
        ) { Text("Clique: $clicks", color = Color.White, fontWeight = FontWeight.Bold) }

        Box(
            Modifier
                .weight(1f)
                .height(72.dp)
                .clip(shape)
                .background(PanelDark)
                .border(1.dp, Magenta.copy(alpha = 0.5f), shape)
                .combinedClickable(onClick = {}, onDoubleClick = { doubles++ }),
            contentAlignment = Alignment.Center,
        ) { Text("Duplo clique: $doubles", color = Color.White, fontWeight = FontWeight.Bold) }

        Box(
            Modifier
                .weight(1f)
                .height(72.dp)
                .clip(shape)
                .background(if (toggled) Cyan else PanelDark)
                .border(1.dp, Cyan.copy(alpha = 0.5f), shape)
                .clickable { toggled = !toggled },
            contentAlignment = Alignment.Center,
        ) {
            Text(
                if (toggled) "LIGADO" else "DESLIGADO",
                color = if (toggled) BgDark else Color.White,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

@Composable
private fun DragArea(modifier: Modifier) {
    var offset by remember { mutableStateOf(IntOffset(40, 20)) }
    val shape = RoundedCornerShape(16.dp)
    Box(
        modifier
            .clip(shape)
            .background(PanelDark)
            .border(1.dp, Color.White.copy(alpha = 0.12f), shape)
            .clipToBounds()
    ) {
        Text(
            "Segure a pinça sobre a bola e arraste",
            color = TextDim,
            fontSize = 12.sp,
            modifier = Modifier.align(Alignment.BottomCenter).padding(8.dp),
        )
        Box(
            Modifier
                .offset { offset }
                .size(64.dp)
                .background(Magenta, CircleShape)
                .pointerInput(Unit) {
                    detectDragGestures { change, drag ->
                        change.consume()
                        offset = IntOffset(
                            (offset.x + drag.x).roundToInt().coerceIn(0, 2000),
                            (offset.y + drag.y).roundToInt().coerceIn(0, 1000),
                        )
                    }
                }
        )
    }
}

@Composable
private fun ScrollList(modifier: Modifier) {
    val shape = RoundedCornerShape(16.dp)
    LazyColumn(
        modifier = modifier
            .clip(shape)
            .background(PanelDark)
            .border(1.dp, Color.White.copy(alpha = 0.12f), shape),
        // Espaço no fim para a miniatura da câmera não cobrir os últimos itens.
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            start = 14.dp, end = 14.dp, top = 8.dp, bottom = 180.dp
        ),
    ) {
        items((1..40).toList()) { n ->
            Column(Modifier.fillMaxWidth().padding(vertical = 10.dp)) {
                Text("Item $n", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                Text("Role com dois dedos (indicador + médio)", color = TextDim, fontSize = 12.sp)
            }
            Spacer(Modifier.height(1.dp).fillMaxWidth().background(Color.White.copy(alpha = 0.06f)))
        }
    }
}
