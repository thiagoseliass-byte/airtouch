package com.airtouch.ui

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
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
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import com.airtouch.AirTouchViewModel
import com.airtouch.AppTab
import com.airtouch.global.AirTouchAccessibilityService
import com.airtouch.global.AirTouchService
import com.airtouch.ui.theme.Alert
import com.airtouch.ui.theme.BgDark
import com.airtouch.ui.theme.Cyan
import com.airtouch.ui.theme.PanelDark
import com.airtouch.ui.theme.TextDim

@Composable
fun AirTouchScreen(viewModel: AirTouchViewModel, onFullscreen: (Boolean) -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val doc by viewModel.doc.collectAsStateWithLifecycle()
    val global by AirTouchService.status.collectAsStateWithLifecycle()
    val a11y by AirTouchAccessibilityService.connected.collectAsStateWithLifecycle()
    val context = LocalContext.current

    fun toast(msg: String) {
        Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
    }

    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    var pendingGlobal by remember { mutableStateOf(false) }
    var askedNotif by remember { mutableStateOf(false) }

    // ---- controle global (outros apps) ----------------------------------------------

    fun startGlobalNow() {
        viewModel.setControlEnabled(false) // a câmera é de um só: o global assume
        AirTouchService.start(context)
    }

    val notifLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { _ -> startGlobalNow() } // com ou sem notificação visível, o serviço funciona

    val cameraLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasPermission = granted
        if (!granted) pendingGlobal = false
        else if (!pendingGlobal) viewModel.setControlEnabled(true)
    }

    fun startGlobalFlow() {
        val needsNotification = Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED &&
            !askedNotif
        when {
            !a11y -> {
                viewModel.setTab(AppTab.GLOBAL)
                toast("Primeiro ligue o serviço de acessibilidade do AIR TOUCH.")
            }

            !hasPermission -> {
                pendingGlobal = true
                cameraLauncher.launch(Manifest.permission.CAMERA)
            }

            needsNotification -> {
                askedNotif = true
                notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }

            else -> startGlobalNow()
        }
    }

    // Depois de conceder a câmera num fluxo "iniciar global", continua o fluxo.
    LaunchedEffect(hasPermission, pendingGlobal) {
        if (pendingGlobal && hasPermission) {
            pendingGlobal = false
            startGlobalFlow()
        }
    }

    // Se a permissão da câmera for revogada, desliga o controle.
    LaunchedEffect(hasPermission) {
        if (!hasPermission && state.controlEnabled) viewModel.setControlEnabled(false)
    }

    /** Abre outro app/documento. Se possível, liga o controle global antes. */
    fun openExternal(intent: Intent) {
        if (a11y && hasPermission && !global.running) {
            startGlobalNow()
        } else if (!a11y) {
            toast("Dica: ligue o Controle global (aba \"Controle global\") para controlar o outro app com gestos.")
        }
        try {
            context.startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            toast("Nenhum aplicativo encontrado para abrir isto.")
        }
    }

    // ---- seletores de arquivo (sem permissão de armazenamento) ----------------------

    val pdfPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? -> if (uri != null) viewModel.openPdf(uri) }

    val externalPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            val type = context.contentResolver.getType(uri) ?: "*/*"
            val view = Intent(Intent.ACTION_VIEW)
                .setDataAndType(uri, type)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            openExternal(Intent.createChooser(view, "Abrir com…"))
        }
    }

    val onToggle: () -> Unit = {
        when {
            global.running -> AirTouchService.stop(context)
            state.controlEnabled -> viewModel.setControlEnabled(false)
            hasPermission -> viewModel.setControlEnabled(true)
            else -> cameraLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    val showCamera = state.controlEnabled && hasPermission
    val fullscreen = state.tab == AppTab.PRESENT && doc.presenting
    LaunchedEffect(fullscreen) { onFullscreen(fullscreen) }

    Surface(color = BgDark, modifier = Modifier.fillMaxSize()) {
        // Esta Box ocupa a janela inteira: o cursor é desenhado aqui, na mesma
        // área em que os toques do mouse virtual são injetados.
        Box(Modifier.fillMaxSize()) {
            Column(
                Modifier
                    .fillMaxSize()
                    .safeDrawingPadding()
                    .padding(if (fullscreen) 0.dp else 16.dp)
            ) {
                if (!fullscreen) {
                    TopBar(
                        tab = state.tab,
                        onTab = viewModel::setTab,
                        active = global.running || state.controlEnabled,
                        label = when {
                            global.running -> "PARAR CONTROLE GLOBAL"
                            state.controlEnabled -> "DESLIGAR CÂMERA"
                            else -> "ATIVAR CONTROLE"
                        },
                        onToggle = onToggle,
                    )
                    Spacer(Modifier.height(12.dp))
                }

                BoxWithConstraints(Modifier.fillMaxWidth().weight(1f)) {
                    val wide = maxWidth > maxHeight

                    // 1) Conteúdo da aba
                    when (state.tab) {
                        AppTab.PANEL -> {
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

                        AppTab.MOUSE -> MouseTestArea(
                            state = state,
                            onPrev = { viewModel.changeSlide(-1) },
                            onNext = { viewModel.changeSlide(+1) },
                            onSensitivity = viewModel::setSensitivity,
                            modifier = Modifier.fillMaxSize(),
                        )

                        AppTab.DOCS -> DocumentsScreen(
                            viewModel = viewModel,
                            doc = doc,
                            onOpenPdf = { pdfPicker.launch(arrayOf("application/pdf")) },
                            onOpenExternal = { externalPicker.launch(arrayOf("*/*")) },
                            onPresent = {
                                viewModel.setTab(AppTab.PRESENT)
                                viewModel.setPresenting(true)
                            },
                            modifier = Modifier.fillMaxSize(),
                        )

                        AppTab.PRESENT -> PresentationScreen(
                            viewModel = viewModel,
                            doc = doc,
                            onOpenPdf = { pdfPicker.launch(arrayOf("application/pdf")) },
                            onOpenExternal = { externalPicker.launch(arrayOf("*/*")) },
                            modifier = Modifier.fillMaxSize(),
                        )

                        AppTab.GLOBAL -> GlobalScreen(
                            accessibilityOn = a11y,
                            status = global,
                            onOpenAccessibilitySettings = {
                                context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                            },
                            onOpenAppInfo = {
                                context.startActivity(
                                    Intent(
                                        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                        Uri.parse("package:${context.packageName}")
                                    )
                                )
                            },
                            onStart = { startGlobalFlow() },
                            onStop = { AirTouchService.stop(context) },
                            onLaunchApp = { pkg ->
                                context.packageManager.getLaunchIntentForPackage(pkg)?.let { openExternal(it) }
                            },
                            onOpenDocument = { externalPicker.launch(arrayOf("*/*")) },
                            modifier = Modifier.fillMaxSize(),
                        )
                    }

                    // 2) Câmera: SEMPRE no mesmo ponto da árvore, só muda tamanho/posição
                    //    (assim ela não reinicia ao trocar de aba). Na aba Controle global
                    //    o app não usa a câmera, então ela some.
                    if (state.tab != AppTab.GLOBAL) {
                        val big = state.tab == AppTab.PANEL
                        val cameraMod = when {
                            big && wide ->
                                Modifier.align(Alignment.TopStart).fillMaxHeight().width(maxWidth * 0.6f - 8.dp)

                            big ->
                                Modifier.align(Alignment.TopStart).fillMaxWidth().height(maxHeight * 0.5f - 8.dp)

                            else -> Modifier
                                .align(Alignment.BottomEnd)
                                .size(if (fullscreen) 160.dp else 200.dp, if (fullscreen) 120.dp else 150.dp)
                        }
                        CameraCard(
                            viewModel = viewModel,
                            showCamera = showCamera,
                            small = !big,
                            globalRunning = global.running,
                            modifier = cameraMod,
                        )
                    }
                }
            }

            // Cursor por cima de tudo (laser vermelho na apresentação)
            LiveCursorOverlay(viewModel, Modifier.fillMaxSize(), laser = fullscreen)

            // Painel rápido (mão aberta)
            if (state.panelVisible && !fullscreen) {
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
private fun TopBar(
    tab: AppTab,
    onTab: (AppTab) -> Unit,
    active: Boolean,
    label: String,
    onToggle: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text("AIR TOUCH", color = Cyan, fontSize = 22.sp, fontWeight = FontWeight.Black, letterSpacing = 3.sp)
        Row(
            Modifier.weight(1f).horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AppTab.values().forEach { t ->
                val selected = t == tab
                Box(
                    Modifier
                        .clip(RoundedCornerShape(50))
                        .background(if (selected) Cyan.copy(alpha = 0.18f) else PanelDark)
                        .border(1.dp, if (selected) Cyan else Color.White.copy(alpha = 0.1f), RoundedCornerShape(50))
                        .clickable { onTab(t) }
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Text(
                        t.title,
                        color = if (selected) Cyan else Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                    )
                }
            }
        }
        Button(
            onClick = onToggle,
            colors = ButtonDefaults.buttonColors(
                containerColor = if (active) Alert else Cyan,
                contentColor = if (active) Color.White else BgDark,
            ),
        ) {
            Text(label, fontWeight = FontWeight.Bold, maxLines = 1)
        }
    }
}

@Composable
private fun CameraCard(
    viewModel: AirTouchViewModel,
    showCamera: Boolean,
    small: Boolean,
    globalRunning: Boolean,
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
                text = when {
                    globalRunning -> "Controle global ativo\n(câmera em segundo plano)"
                    small -> "Câmera desligada"
                    else -> "Câmera desligada\nToque em ATIVAR CONTROLE para começar"
                },
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
