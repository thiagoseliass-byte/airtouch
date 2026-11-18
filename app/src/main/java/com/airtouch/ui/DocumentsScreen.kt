package com.airtouch.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.airtouch.AirTouchViewModel
import com.airtouch.DocState
import com.airtouch.ui.theme.Alert
import com.airtouch.ui.theme.Cyan
import com.airtouch.ui.theme.PanelDark
import com.airtouch.ui.theme.TextDim
import kotlin.math.roundToInt

/** Aba "Documentos": abrir PDF, navegar, rolar e dar zoom (por gestos ou pelos botões). */
@Composable
fun DocumentsScreen(
    viewModel: AirTouchViewModel,
    doc: DocState,
    onOpenPdf: () -> Unit,
    onOpenExternal: () -> Unit,
    onPresent: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AirButton("Abrir PDF", onOpenPdf, primary = true)
            AirButton("◀", { viewModel.changePage(-1) }, enabled = doc.pageCount > 0)
            Text(
                if (doc.pageCount > 0) "Pág. ${doc.page + 1}/${doc.pageCount}" else "—",
                color = Color.White,
                fontWeight = FontWeight.Bold,
            )
            AirButton("▶", { viewModel.changePage(+1) }, enabled = doc.pageCount > 0)
            AirButton("Zoom −", { viewModel.zoomBy(-0.25f) }, enabled = doc.pageCount > 0)
            Text("${(doc.zoom * 100).roundToInt()}%", color = Color.White)
            AirButton("Zoom +", { viewModel.zoomBy(+0.25f) }, enabled = doc.pageCount > 0)
            AirButton("Apresentar", onPresent, enabled = doc.pageCount > 0)
            AirButton("Abrir em outro app", onOpenExternal)
        }

        val shape = RoundedCornerShape(16.dp)
        Box(
            Modifier
                .fillMaxWidth()
                .weight(1f)
                .clip(shape)
                .background(Color(0xFF1B2335))
                .border(1.dp, Color.White.copy(alpha = 0.12f), shape)
        ) {
            when {
                doc.pageCount > 0 -> PdfPageView(viewModel, doc, fit = false, modifier = Modifier.fillMaxSize())
                doc.loading -> Hint("Abrindo…")
                doc.error != null -> Hint(doc.error, Alert)
                else -> Hint(
                    "Toque em ABRIR PDF para escolher um arquivo.\n\n" +
                        "Gestos: varrer a mão aberta ← → muda de página · " +
                        "dois dedos rolam a página · pinça clica nos botões."
                )
            }
            if (doc.name != null && doc.pageCount > 0) {
                Text(
                    doc.name,
                    color = Color.White,
                    fontSize = 12.sp,
                    maxLines = 1,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(8.dp)
                        .clip(RoundedCornerShape(50))
                        .background(Color.Black.copy(alpha = 0.55f))
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                )
            }
        }
    }
}

@Composable
private fun androidx.compose.foundation.layout.BoxScope.Hint(text: String, color: Color = TextDim) {
    Text(
        text,
        color = color,
        textAlign = TextAlign.Center,
        fontSize = 14.sp,
        modifier = Modifier.align(Alignment.Center).padding(24.dp),
    )
}

/** Aba "Apresentações": PDF como slides em tela cheia, com ponteiro virtual. */
@Composable
fun PresentationScreen(
    viewModel: AirTouchViewModel,
    doc: DocState,
    onOpenPdf: () -> Unit,
    onOpenExternal: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (doc.presenting) {
        // Palco em tela cheia: fundo preto, slide inteiro, ponteiro (laser) por cima.
        Box(modifier.background(Color.Black)) {
            PdfPageView(viewModel, doc, fit = true, modifier = Modifier.fillMaxSize())
            Text(
                "${doc.page + 1} / ${doc.pageCount}",
                color = Color.White,
                fontSize = 13.sp,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(16.dp)
                    .clip(RoundedCornerShape(50))
                    .background(Color.Black.copy(alpha = 0.55f))
                    .padding(horizontal = 12.dp, vertical = 6.dp),
            )
            Text(
                "Varrer ← →: slides · Indicador: ponteiro · Polegar ↓: sair",
                color = TextDim,
                fontSize = 11.sp,
                modifier = Modifier.align(Alignment.TopCenter).padding(10.dp),
            )
        }
        return
    }

    Column(modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AirButton("Abrir PDF", onOpenPdf, primary = true)
            AirButton(
                "▶ Iniciar apresentação",
                { viewModel.setPresenting(true) },
                enabled = doc.pageCount > 0,
                primary = doc.pageCount > 0,
            )
            AirButton("Abrir PowerPoint/Slides em outro app", onOpenExternal)
        }

        val shape = RoundedCornerShape(16.dp)
        Box(
            Modifier
                .fillMaxWidth()
                .weight(1f)
                .clip(shape)
                .background(Color.Black)
                .border(1.dp, Cyan.copy(alpha = 0.25f), shape)
        ) {
            if (doc.pageCount > 0) {
                PdfPageView(viewModel, doc, fit = true, modifier = Modifier.fillMaxSize())
                Text(
                    "${doc.name ?: "PDF"} · slide ${doc.page + 1}/${doc.pageCount}",
                    color = Color.White,
                    fontSize = 12.sp,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(8.dp)
                        .clip(RoundedCornerShape(50))
                        .background(Color.Black.copy(alpha = 0.55f))
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                )
            } else {
                Hint(
                    doc.error
                        ?: ("Abra um PDF (exporte seus slides do PowerPoint como PDF) e inicie a apresentação.\n\n" +
                            "Para PowerPoint/Google Slides diretamente, use \"Abrir em outro app\": o Controle global " +
                            "liga sozinho e varrer a mão aberta ← → passa os slides lá."),
                    if (doc.error != null) Alert else TextDim,
                )
            }
        }

        // Aviso fixo (fora da caixa para não ser coberto pela câmera)
        Text(
            "Dica: na apresentação, o indicador vira um ponteiro laser. Destacar/anotar em slides chega na Fase 4 (escrita no ar).",
            color = TextDim,
            fontSize = 12.sp,
            modifier = Modifier.fillMaxWidth().background(PanelDark, RoundedCornerShape(12.dp)).padding(10.dp),
        )
    }
}
