package com.airtouch.ui

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import com.airtouch.global.AirTouchService
import com.airtouch.ui.theme.Alert
import com.airtouch.ui.theme.Cyan
import com.airtouch.ui.theme.Magenta
import com.airtouch.ui.theme.PanelDark
import com.airtouch.ui.theme.TextDim
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private data class AppEntry(val label: String, val pkg: String, val icon: ImageBitmap?)

private fun loadApps(ctx: Context): List<AppEntry> {
    val pm = ctx.packageManager
    val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
    return pm.queryIntentActivities(intent, 0)
        .filter { it.activityInfo.packageName != ctx.packageName }
        .map { ri ->
            AppEntry(
                label = ri.loadLabel(pm).toString(),
                pkg = ri.activityInfo.packageName,
                icon = try {
                    ri.loadIcon(pm).toBitmap(96, 96).asImageBitmap()
                } catch (e: Exception) {
                    null
                },
            )
        }
        .distinctBy { it.pkg }
        .sortedBy { it.label.lowercase() }
}

/** Aba "Controle global": liga o controle de outros apps e abre aplicativos. */
@Composable
fun GlobalScreen(
    accessibilityOn: Boolean,
    status: AirTouchService.Status,
    onOpenAccessibilitySettings: () -> Unit,
    onOpenAppInfo: () -> Unit,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onLaunchApp: (String) -> Unit,
    onOpenDocument: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var apps by remember { mutableStateOf<List<AppEntry>>(emptyList()) }
    LaunchedEffect(Unit) { apps = withContext(Dispatchers.IO) { loadApps(context) } }

    BoxWithConstraints(modifier) {
        val wide = maxWidth > maxHeight
        val controlsWidth = maxWidth * 0.42f

        val controls: @Composable (Modifier) -> Unit = { m ->
            Column(
                m
                    .clip(RoundedCornerShape(20.dp))
                    .background(PanelDark)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text("CONTROLE GLOBAL", color = TextDim, fontSize = 11.sp, letterSpacing = 2.sp)
                Text(
                    "Usa os gestos para controlar QUALQUER app: cursor, clique, arrastar, rolar, " +
                        "voltar e início. A câmera continua ligada em segundo plano (notificação fixa).",
                    color = Color.White,
                    fontSize = 13.sp,
                )

                StepLine("1. Serviço de acessibilidade", if (accessibilityOn) "ATIVO" else "DESLIGADO", accessibilityOn)
                if (!accessibilityOn) {
                    AirButton("Abrir configurações de acessibilidade", onOpenAccessibilitySettings, primary = true)
                    Text(
                        "Em Aplicativos instalados / Serviços instalados, escolha AIR TOUCH e ligue. " +
                            "Se aparecer \"Configuração restrita\" (apps instalados por APK), toque no botão " +
                            "abaixo, abra o menu ⋮ no canto e escolha \"Permitir configurações restritas\"; " +
                            "depois volte e ligue o serviço.",
                        color = TextDim,
                        fontSize = 12.sp,
                    )
                    AirButton("Abrir informações do app (⋮)", onOpenAppInfo)
                }

                StepLine(
                    "2. Controle global",
                    when {
                        status.running && status.locked -> "ATIVO · pausado (punho)"
                        status.running -> "ATIVO · ${status.fps} FPS · ${status.gesture.label}"
                        else -> "PARADO"
                    },
                    status.running,
                )
                if (status.error != null) {
                    Text(status.error, color = Alert, fontSize = 12.sp)
                }
                if (status.running) {
                    AirButton("PARAR CONTROLE GLOBAL", onStop, primary = false)
                } else {
                    AirButton("INICIAR CONTROLE GLOBAL", onStart, enabled = accessibilityOn, primary = true)
                }

                AirButton("Abrir documento em outro app…", onOpenDocument)

                Text("COMO USAR NOS OUTROS APPS", color = TextDim, fontSize = 11.sp, letterSpacing = 2.sp)
                Text(
                    "☝ Indicador: cursor · 🤏 Pinça: clique · 🤏 segurada: arrastar\n" +
                        "✌ Dois dedos: rolar · 🖐 Varrer ← →: slide/página, ↑ ↓: rolar\n" +
                        "🖐 Mão aberta: mostra a barra (Voltar, Início, Recentes, volume, ⏯, Parar)\n" +
                        "👎 Polegar para baixo: Voltar · ✊ Punho: pausa",
                    color = TextDim,
                    fontSize = 12.sp,
                    lineHeight = 18.sp,
                )
                Text(
                    "Limites do Android: apps bancários e telas protegidas bloqueiam toques simulados; " +
                        "alguns jogos também ignoram.",
                    color = Magenta.copy(alpha = 0.9f),
                    fontSize = 11.sp,
                )
            }
        }

        val grid: @Composable (Modifier) -> Unit = { m ->
            Column(m, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "ABRIR APLICATIVO (liga o controle global sozinho)",
                    color = TextDim, fontSize = 11.sp, letterSpacing = 2.sp,
                )
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(96.dp),
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(20.dp))
                        .background(PanelDark)
                        .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(20.dp)),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(apps) { app ->
                        Column(
                            Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .clickable { onLaunchApp(app.pkg) }
                                .padding(6.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            if (app.icon != null) {
                                Image(app.icon, contentDescription = app.label, modifier = Modifier.size(48.dp))
                            }
                            Text(
                                app.label,
                                color = Color.White,
                                fontSize = 11.sp,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                textAlign = TextAlign.Center,
                            )
                        }
                    }
                }
            }
        }

        if (wide) {
            Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                controls(Modifier.width(controlsWidth).fillMaxHeight())
                grid(Modifier.weight(1f).fillMaxHeight())
            }
        } else {
            Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                controls(Modifier.fillMaxWidth().weight(1f))
                grid(Modifier.fillMaxWidth().weight(1f))
            }
        }
    }
}

@Composable
private fun StepLine(title: String, value: String, ok: Boolean) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(title, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
        Text(value, color = if (ok) Cyan else Alert, fontWeight = FontWeight.Bold, fontSize = 13.sp)
    }
}
