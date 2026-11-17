package com.airtouch.ui

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import com.airtouch.AirTouchViewModel
import com.airtouch.DocState
import kotlin.math.max

/**
 * Mostra a página atual do PDF.
 *  - [fit] = true: página inteira na área (modo apresentação).
 *  - [fit] = false: largura da área x zoom, com rolagem vertical e horizontal (modo leitura).
 */
@Composable
fun PdfPageView(
    viewModel: AirTouchViewModel,
    doc: DocState,
    fit: Boolean,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(modifier) {
        val density = LocalDensity.current
        val wPx = with(density) { maxWidth.toPx() }.toInt()
        val hPx = with(density) { maxHeight.toPx() }.toInt()
        val renderWidth =
            if (fit) max(wPx, (hPx * 1.78f).toInt()) else (wPx * doc.zoom).toInt()

        var bitmap by remember { mutableStateOf<Bitmap?>(null) }
        LaunchedEffect(doc.version, doc.page, renderWidth) {
            if (renderWidth > 0) bitmap = viewModel.pdf.render(doc.page, renderWidth)
        }

        val vScroll = rememberScrollState()
        val hScroll = rememberScrollState()
        LaunchedEffect(doc.page, doc.version) {
            vScroll.scrollTo(0)
            hScroll.scrollTo(0)
        }

        val bmp = bitmap
        if (bmp != null) {
            if (fit) {
                Image(
                    bitmap = bmp.asImageBitmap(),
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Image(
                    bitmap = bmp.asImageBitmap(),
                    contentDescription = null,
                    contentScale = ContentScale.FillWidth,
                    modifier = Modifier
                        .verticalScroll(vScroll)
                        .horizontalScroll(hScroll)
                        .width(maxWidth * doc.zoom),
                )
            }
        }
    }
}
