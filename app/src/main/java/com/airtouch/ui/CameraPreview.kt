package com.airtouch.ui

import android.graphics.Bitmap
import androidx.camera.view.PreviewView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.airtouch.camera.CameraManager

/**
 * Mostra o preview da câmera. Ao sair da composição (controle desligado),
 * a câmera é liberada imediatamente.
 */
@Composable
fun CameraPreview(
    onFrame: (Bitmap, Long) -> Unit,
    modifier: Modifier = Modifier,
    frameGate: (Long) -> Boolean = { true },
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val cameraManager = remember { CameraManager(context, onFrame, frameGate = frameGate) }

    DisposableEffect(cameraManager) {
        onDispose { cameraManager.release() }
    }

    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            PreviewView(ctx).apply {
                implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                // FIT_CENTER: mostra o quadro inteiro, o que torna simples alinhar o overlay.
                scaleType = PreviewView.ScaleType.FIT_CENTER
                // Espera a view estar anexada para conhecer a rotação do display.
                post { cameraManager.start(lifecycleOwner, this) }
            }
        },
    )
}
