package com.airtouch.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.airtouch.AirTouchUiState
import com.airtouch.AirTouchViewModel
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

private enum class Tab(val title: String) {
    PANEL("Painel"),
    MOUSE("Mouse virtual"),
}

@Composable
fun AirTouchScreen(viewModel: AirTouchViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasPermission = granted
        if (granted) viewModel.setControlEnabled(true)
    }

    // Se a permissão for revogada, desliga o controle.
    LaunchedEffect(hasPermission) {
        if (!hasPermission && state.controlEnabled) viewModel.setControlEnabled(false)
    }

    val onToggle: () -> Unit = {
        when {
            state.controlEnabled -> viewModel.setControlEnabled(false)
            hasPermission -> viewModel.setControlEnabled(true)
            else -> permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    var tab by remember { mutableStateOf(Tab.PANEL) }
    val showCamera = state.controlEnabled && hasPermission

    Surface(color = BgDark, modifier = Modifier.fillMaxSize()) {
        // Esta Box ocupa a janela inteira: o cursor é desenhado aqui, na mesma
        // área em que os toques do mouse virtual são injetados.
        Box(Modifier.fillMaxSize()) {
            Column(
                Modifier
                    .fillMaxSize()
                    .safeDrawingPadding()
                    .padding(16.dp)
            ) {
                TopBar(
                    tab = tab,
                    onTab = { tab = it },
                    enabled = state.controlEnabled,
                    onToggle = onToggle,
                )
                Spacer(Modifier.height(12.dp))

                BoxWithConstraints(Modifier.fillMaxWidth().weight(1f)) {
                    val wide = maxWidth > maxHeight

                    // 1) Conteúdo da aba
                    when (tab) {
                        Tab.PANEL -> {
                            val panelMod = if (wide) {
                                Modifier.align(Alignment.TopEnd).fillMaxHeight().width(maxWidth * 0.4f - 8.dp)
                            } else {
                                Modifier.align(Alignment.BottomStart).fillMaxWidth().height(maxHeight * 0.5f - 8.dp)
                            }
                            ControlPanel(
                                state = state,
                                onSensitivity = viewModel::setSensitivity,
                                onInvertScroll = viewModel::setInvertScroll,
                                modifier = panelMod,
                            )
                        }

                        Tab.MOUSE -> MouseTestArea(
                            state = state,
                            onPrev = { viewModel.changeSlide(-1) },
                            onNext = { viewModel.changeSlide(+1) },
                            onSensitivity = viewModel::setSensitivity,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }

                    // 2) Câmera: SEMPRE no mesmo ponto da árvore, só muda o tamanho/posição
                    //    (assim ela não reinicia ao trocar de aba).
                    val cameraMod = when {
                        tab == Tab.PANEL && wide ->
                            Modifier.align(Alignment.TopStart).fillMaxHeight().width(maxWidth * 0.6f - 8.dp)

                        tab == Tab.PANEL ->
                            Modifier.align(Alignment.TopStart).fillMaxWidth().height(maxHeight * 0.5f - 8.dp)

                        else -> Modifier.align(Alignment.BottomEnd).size(200.dp, 150.dp)
                    }
                    CameraCard(
                        viewModel = viewModel,
                        showCamera = showCamera,
                        small = tab == Tab.MOUSE,
                        modifier = cameraMod,
                    )
                }
            }

            // Cursor por cima de tudo
            LiveCursorOverlay(viewModel, Modifier.fillMaxSize())

            // Painel rápido (mão aberta)
            if (state.panelVisible) {
                QuickPanel(
                    state,
                    Modifier.align(Alignment.TopCenter).safeDrawingPadding().padding(top = 80.dp)
                )
            }
            if (state.locked) {
                Text(
                    "⏸  PAUSADO — mostre a mão aberta para retomar",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .safeDrawingPadding()
                        .padding(bottom = 24.dp)
                        .clip(RoundedCornerShape(50))
                        .background(Alert)
                        .padding(horizontal = 20.dp, vertical = 10.dp),
                )
            }
        }
    }
}

@Composable
private fun TopBar(tab: Tab, onTab: (Tab) -> Unit, enabled: Boolean, onToggle: () -> Unit) {
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text("AIR TOUCH", color = Cyan, fontSize = 22.sp, fontWeight = FontWeight.Black, letterSpacing = 3.sp)
        Spacer(Modifier.width(8.dp))
        Tab.values().forEach { t ->
            val selected = t == tab
            Box(
                Modifier
                    .clip(RoundedCornerShape(50))
                    .background(if (selected) Cyan.copy(alpha = 0.18f) else PanelDark)
                    .border(1.dp, if (selected) Cyan else Color.White.copy(alpha = 0.1f), RoundedCornerShape(50))
                    .clickable { onTab(t) }
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Text(t.title, color = if (selected) Cyan else Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            }
        }
        Spacer(Modifier.weight(1f))
        Button(
            onClick = onToggle,
            colors = ButtonDefaults.buttonColors(
                containerColor = if (enabled) Alert else Cyan,
                contentColor = if (enabled) Color.White else BgDark,
            ),
        ) {
            Text(if (enabled) "DESLIGAR CÂMERA" else "ATIVAR CONTROLE", fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun CameraCard(
    viewModel: AirTouchViewModel,
    showCamera: Boolean,
    small: Boolean,
    modifier: Modifier,
) {
    val shape = RoundedCornerShape(if (small) 14.dp else 24.dp)
    Box(
        modifier = modifier
            .clip(shape)
            .background(Color.Black)
            .border(1.dp, Cyan.copy(alpha = 0.35f), shape)
    ) {
        if (showCamera) {
            CameraPreview(onFrame = viewModel::onCameraFrame, modifier = Modifier.fillMaxSize())
            LiveHandOverlay(viewModel, Modifier.fillMaxSize())
            CameraActiveBadge(small, Modifier.align(Alignment.TopStart).padding(if (small) 8.dp else 16.dp))
        } else {
            Text(
                text = if (small) "Câmera desligada" else "Câmera desligada\nToque em ATIVAR CONTROLE para começar",
                color = TextDim,
                fontSize = if (small) 12.sp else 14.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.align(Alignment.Center).padding(16.dp),
            )
        }
    }
}

@Composable
private fun LiveHandOverlay(viewModel: AirTouchViewModel, modifier: Modifier) {
    val frame by viewModel.frame.collectAsStateWithLifecycle()
    HandOverlay(frame = frame, modifier = modifier)
}

/** Indicador permanente de câmera ativa (requisito de privacidade). */
@Composable
private fun CameraActiveBadge(small: Boolean, modifier: Modifier) {
    val pulse = rememberInfiniteTransition(label = "pulse")
    val alpha by pulse.animateFloat(
        initialValue = 0.3f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(700), RepeatMode.Reverse),
        label = "dot",
    )
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(Color.Black.copy(alpha = 0.6f))
            .padding(horizontal = if (small) 8.dp else 12.dp, vertical = if (small) 3.dp else 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(if (small) 7.dp else 10.dp).alpha(alpha).background(Alert, CircleShape))
        Spacer(Modifier.size(6.dp))
        Text(
            "CÂMERA ATIVA",
            color = Color.White,
            fontSize = if (small) 9.sp else 12.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}

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
private fun QuickPanel(state: AirTouchUiState, modifier: Modifier) {
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
        Text("Varrer a mão aberta = slides / rolagem", color = TextDim, fontSize = 12.sp)
    }
}

@Composable
private fun ControlPanel(
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
        Text("Fase 2 · Controle básico", color = TextDim, fontSize = 13.sp)

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

        // Ajustes
        SensitivityControl(state.sensitivity, onSensitivity)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth()) {
            Text("Inverter sentido da rolagem", color = TextDim, fontSize = 13.sp)
            Switch(checked = state.invertScroll, onCheckedChange = onInvertScroll)
        }

        // Ações recentes + gestos recentes
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
                "🖐 Varrer mão aberta ← →: slide anterior/seguinte\n" +
                "🖐 Varrer mão aberta ↑ ↓: rolar\n" +
                "✊ Punho: pausa · 🖐 Mão aberta: retoma\n" +
                "👍 Confirmar · 👎 Cancelar\n\n" +
                "Abra a aba \"Mouse virtual\" para testar.",
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
