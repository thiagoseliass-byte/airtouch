package com.airtouch.ui

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Surface
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
import androidx.lifecycle.viewmodel.compose.viewModel
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

@Composable
fun AirTouchScreen(viewModel: AirTouchViewModel = viewModel()) {
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

    val showCamera = state.controlEnabled && hasPermission

    Surface(color = BgDark, modifier = Modifier.fillMaxSize()) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .padding(16.dp)
        ) {
            val wide = maxWidth > maxHeight
            if (wide) {
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    CameraCard(
                        state, showCamera, viewModel::onCameraFrame,
                        Modifier.weight(1.6f).fillMaxHeight()
                    )
                    ControlPanel(state, onToggle, Modifier.weight(1f).fillMaxHeight())
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    CameraCard(
                        state, showCamera, viewModel::onCameraFrame,
                        Modifier.fillMaxWidth().weight(1f)
                    )
                    ControlPanel(state, onToggle, Modifier.fillMaxWidth())
                }
            }
        }
    }
}

@Composable
private fun CameraCard(
    state: AirTouchUiState,
    showCamera: Boolean,
    onFrame: (Bitmap, Long) -> Unit,
    modifier: Modifier,
) {
    val shape = RoundedCornerShape(24.dp)
    Box(
        modifier = modifier
            .clip(shape)
            .background(Color.Black)
            .border(1.dp, Cyan.copy(alpha = 0.35f), shape)
    ) {
        if (showCamera) {
            CameraPreview(onFrame = onFrame, modifier = Modifier.fillMaxSize())
            HandOverlay(frame = state.frame, modifier = Modifier.fillMaxSize())
            CameraActiveBadge(Modifier.align(Alignment.TopStart).padding(16.dp))
        } else {
            Text(
                text = "Câmera desligada\nToque em ATIVAR CONTROLE para começar",
                color = TextDim,
                textAlign = TextAlign.Center,
                modifier = Modifier.align(Alignment.Center).padding(24.dp),
            )
        }
    }
}

/** Indicador permanente de câmera ativa (requisito de privacidade). */
@Composable
private fun CameraActiveBadge(modifier: Modifier) {
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
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(10.dp).alpha(alpha).background(Alert, CircleShape))
        Spacer(Modifier.size(8.dp))
        Text("CÂMERA ATIVA", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun ControlPanel(state: AirTouchUiState, onToggle: () -> Unit, modifier: Modifier) {
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
        Text("AIR TOUCH", color = Cyan, fontSize = 26.sp, fontWeight = FontWeight.Black, letterSpacing = 4.sp)
        Text("Fase 1 · Prova de conceito", color = TextDim, fontSize = 13.sp)

        // Status
        val handCount = state.frame?.hands?.size ?: 0
        val (statusText, statusColor) = when {
            !state.controlEnabled -> "Controle desligado" to TextDim
            state.error != null -> state.error to Alert
            !state.modelReady -> "Carregando modelo…" to Magenta
            handCount == 0 -> "Procurando mão…" to Magenta
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
            Metric("Mãos", handCount.toString(), Modifier.weight(1f))
        }

        // Recentes
        Text("GESTOS RECENTES", color = TextDim, fontSize = 11.sp, letterSpacing = 2.sp)
        if (state.recent.isEmpty()) {
            Text("Nenhum gesto ainda", color = TextDim, fontSize = 13.sp)
        } else {
            val fmt = remember { SimpleDateFormat("HH:mm:ss", Locale.getDefault()) }
            state.recent.forEach { e ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(e.gesture.label, color = Color.White, fontSize = 14.sp)
                    Text(fmt.format(Date(e.timeMs)), color = TextDim, fontSize = 13.sp)
                }
            }
        }

        Spacer(Modifier.height(4.dp))

        Button(
            onClick = onToggle,
            modifier = Modifier.fillMaxWidth().height(52.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = if (state.controlEnabled) Alert else Cyan,
                contentColor = if (state.controlEnabled) Color.White else BgDark,
            ),
        ) {
            Text(
                if (state.controlEnabled) "DESLIGAR CÂMERA" else "ATIVAR CONTROLE",
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp,
            )
        }

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
