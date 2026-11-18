package com.airtouch.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.airtouch.AirTouchUiState
import com.airtouch.AirTouchViewModel
import com.airtouch.control.ActionId
import com.airtouch.control.Triggers
import com.airtouch.ui.theme.Cyan
import com.airtouch.ui.theme.Magenta
import com.airtouch.ui.theme.PanelDark
import com.airtouch.ui.theme.TextDim

/** Perfis, associação de gestos a ações e gestos personalizados. */
@Composable
fun CustomizeScreen(
    viewModel: AirTouchViewModel,
    state: AirTouchUiState,
    modifier: Modifier = Modifier,
) {
    val profile = state.profiles.firstOrNull { it.name == state.activeProfile } ?: state.profiles.first()

    Column(
        modifier.verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Title("PERFIS")
        Row(
            Modifier.fillMaxWidth().horizontalScrollCompat(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            state.profiles.forEach { p ->
                Chip(p.name, p.name == state.activeProfile) { viewModel.setActiveProfile(p.name) }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AirButton("Novo perfil (cópia deste)", { viewModel.newProfile() })
            if (!profile.builtIn) AirButton("Excluir este perfil", { viewModel.deleteProfile(profile.name) })
        }
        Text(
            "Toque numa ação para trocá-la pela próxima da lista. O perfil escolhido vale também no Controle global.",
            color = TextDim,
            fontSize = 12.sp,
        )

        state.smartProfile?.let {
            Text("⚙ Perfil automático nesta aba: $it", color = Cyan, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        }
        Title("O QUE CADA GESTO FAZ NO PERFIL \"${profile.name.uppercase()}\"")
        Triggers.builtIn.forEach { (key, label) ->
            BindingRow(label, profile.bindings[key] ?: ActionId.NONE) { viewModel.cycleBinding(profile.name, key) }
        }
        state.customGestures.forEach { g ->
            val key = Triggers.custom(g.id)
            BindingRow("★ ${g.name}", profile.bindings[key] ?: ActionId.NONE) { viewModel.cycleBinding(profile.name, key) }
        }
        Text(
            "Fixos em todos os perfis: indicador move o cursor · pinça clica/arrasta · dois dedos rolam · " +
                "punho pausa · mão aberta retoma · duas mãos fazem zoom · círculo no ar abre o menu.",
            color = TextDim,
            fontSize = 12.sp,
        )

        Title("GESTOS PERSONALIZADOS")
        Text(
            "Grave uma pose da mão (por exemplo: \"hang loose\", \"V\" ou \"rock\"). Depois escolha a ação dela acima. " +
                "Dica: use uma pose diferente dos gestos padrão e mantenha a mão parada ao gravar.",
            color = TextDim,
            fontSize = 12.sp,
        )
        AirButton(
            if (state.recordMsg != null && state.customGestures.isNotEmpty() || state.recordMsg?.startsWith("✔") == true)
                "Gravar outro gesto" else "Gravar novo gesto",
            { viewModel.startRecording() },
            enabled = state.controlEnabled && (state.recordMsg == null || state.recordMsg.startsWith("✔") ||
                state.recordMsg.startsWith("Não")),
            primary = true,
        )
        if (!state.controlEnabled) {
            Text("Ligue o controle (ATIVAR CONTROLE) para gravar.", color = TextDim, fontSize = 12.sp)
        }
        state.recordMsg?.let { Text(it, color = Magenta, fontSize = 15.sp, fontWeight = FontWeight.Bold) }

        state.customGestures.forEach { g ->
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(PanelDark).padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("★ ${g.name}", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    AirButton("Excluir", { viewModel.deleteCustom(g.id) })
                }
                Text(
                    "Tolerância: ${(g.tolerance * 100).toInt()}%  (baixa = exige pose igual · alta = aceita parecida)",
                    color = TextDim,
                    fontSize = 12.sp,
                )
                Slider(
                    value = g.tolerance,
                    onValueChange = { viewModel.setCustomTolerance(g.id, it) },
                    valueRange = 0f..1f,
                )
            }
        }
        if (state.customGestures.isNotEmpty()) {
            Text(
                "Teste ao vivo (distância / limite): " + (state.customLive ?: "mostre a mão"),
                color = if (state.customLive?.endsWith("✔") == true) Cyan else TextDim,
                fontSize = 13.sp,
            )
        }
        Title("AJUSTES AVANÇADOS (PRECISÃO E BATERIA)")
        SwitchRow(
            "Perfis inteligentes",
            "Troca o perfil sozinho: Apresentação/Leitura/Jogos por aba; no Controle global, pelo app aberto (slides, PDF, vídeo, jogo, tela inicial).",
            state.autoProfile,
            viewModel::setAutoProfile,
        )
        SwitchRow(
            "Filtro de qualidade",
            "Ignora mãos mal detectadas ou cortadas pela borda da câmera (menos falsos positivos).",
            state.qualityFilter,
            viewModel::setQualityFilter,
        )
        SwitchRow(
            "Economia de bateria",
            "Analisa menos quadros por segundo. Sem nenhuma mão à vista por 3 s, o app já reduz a análise sozinho.",
            state.powerSaver,
            viewModel::setPowerSaver,
        )
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(PanelDark).padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text("Calibrar a pinça", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            Text(
                "Faça a pinça do SEU jeito e segure por alguns segundos. O app ajusta a sensibilidade da pinça " +
                    "para você (ajuste atual: ${"%.2f".format(state.pinchScale)}×).",
                color = TextDim,
                fontSize = 12.sp,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AirButton(
                    "Calibrar",
                    { viewModel.startPinchCalibration() },
                    enabled = state.controlEnabled,
                    primary = true,
                )
                AirButton("Voltar ao padrão", { viewModel.resetPinchCalibration() })
            }
            if (!state.controlEnabled) Text("Ligue o controle (ATIVAR CONTROLE) para calibrar.", color = TextDim, fontSize = 12.sp)
            state.calibMsg?.let { Text(it, color = Magenta, fontSize = 14.sp, fontWeight = FontWeight.Bold) }
        }
        Spacer(Modifier.height(170.dp)) // espaço para a câmera no canto
    }
}

@Composable
private fun SwitchRow(title: String, desc: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(PanelDark).padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            Text(desc, color = TextDim, fontSize = 12.sp)
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun Title(text: String) {
    Text(text, color = TextDim, fontSize = 11.sp, letterSpacing = 2.sp)
}

@Composable
private fun BindingRow(label: String, action: ActionId, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(PanelDark).padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, color = Color.White, fontSize = 15.sp, modifier = Modifier.weight(1f))
        AirButton(action.label, onClick, primary = action != ActionId.NONE)
    }
}

/** Rolagem horizontal simples para a lista de perfis. */
@Composable
private fun Modifier.horizontalScrollCompat(): Modifier =
    this.then(Modifier.horizontalScroll(rememberScrollState()))
