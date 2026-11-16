package com.airtouch.camera

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import android.os.SystemClock
import android.util.Size
import android.view.Surface
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * Abre a câmera (preferindo a frontal), mostra o preview e entrega cada quadro
 * como Bitmap já ROTACIONADO (em pé) e ESPELHADO (se frontal), exatamente como
 * o usuário vê no preview. Assim as coordenadas dos pontos da mão batem com a tela.
 *
 * Nenhum quadro é gravado: cada Bitmap é processado e descartado.
 */
class CameraManager(
    context: Context,
    private val onFrame: (bitmap: Bitmap, timestampMs: Long) -> Unit,
    private val onError: (String) -> Unit = {},
) {
    private val appContext = context.applicationContext
    private val executor: ExecutorService = Executors.newSingleThreadExecutor()
    private var provider: ProcessCameraProvider? = null
    private var analysis: ImageAnalysis? = null

    @Volatile private var mirror = true
    @Volatile private var released = false

    fun start(lifecycleOwner: LifecycleOwner, previewView: PreviewView) {
        val future = ProcessCameraProvider.getInstance(appContext)
        future.addListener({
            if (released) return@addListener
            try {
                val cameraProvider = future.get()
                provider = cameraProvider

                val selector = when {
                    cameraProvider.hasCamera(CameraSelector.DEFAULT_FRONT_CAMERA) ->
                        CameraSelector.DEFAULT_FRONT_CAMERA
                    cameraProvider.hasCamera(CameraSelector.DEFAULT_BACK_CAMERA) ->
                        CameraSelector.DEFAULT_BACK_CAMERA
                    else -> {
                        onError("Nenhuma câmera encontrada neste aparelho.")
                        return@addListener
                    }
                }
                mirror = selector == CameraSelector.DEFAULT_FRONT_CAMERA

                val rotation = previewView.display?.rotation ?: Surface.ROTATION_0

                // Preview e análise em 4:3 para terem o mesmo campo de visão.
                val previewSelector = ResolutionSelector.Builder()
                    .setAspectRatioStrategy(AspectRatioStrategy.RATIO_4_3_FALLBACK_AUTO_STRATEGY)
                    .build()
                val analysisSelector = ResolutionSelector.Builder()
                    .setAspectRatioStrategy(AspectRatioStrategy.RATIO_4_3_FALLBACK_AUTO_STRATEGY)
                    .setResolutionStrategy(
                        ResolutionStrategy(
                            Size(640, 480),
                            ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER
                        )
                    )
                    .build()

                val preview = Preview.Builder()
                    .setResolutionSelector(previewSelector)
                    .setTargetRotation(rotation)
                    .build()
                    .also { it.setSurfaceProvider(previewView.surfaceProvider) }

                val imageAnalysis = ImageAnalysis.Builder()
                    .setResolutionSelector(analysisSelector)
                    .setTargetRotation(rotation)
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                    .build()
                    .also { it.setAnalyzer(executor) { proxy -> analyze(proxy) } }
                analysis = imageAnalysis

                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(lifecycleOwner, selector, preview, imageAnalysis)
            } catch (e: Exception) {
                onError("Falha ao abrir a câmera: ${e.message}")
            }
        }, ContextCompat.getMainExecutor(appContext))
    }

    private fun analyze(proxy: ImageProxy) {
        try {
            if (released) return
            val timestamp = SystemClock.uptimeMillis()
            val raw = proxy.toBitmap()

            val matrix = Matrix()
            val rotation = proxy.imageInfo.rotationDegrees
            if (rotation != 0) matrix.postRotate(rotation.toFloat())
            if (mirror) matrix.postScale(-1f, 1f)

            val upright = if (matrix.isIdentity) raw
            else Bitmap.createBitmap(raw, 0, 0, raw.width, raw.height, matrix, true)
            if (upright !== raw) raw.recycle()

            onFrame(upright, timestamp)
        } catch (e: Exception) {
            onError("Erro ao processar quadro: ${e.message}")
        } finally {
            proxy.close()
        }
    }

    /** Desliga a câmera imediatamente e libera os recursos. */
    fun release() {
        released = true
        analysis?.clearAnalyzer()
        provider?.unbindAll()
        executor.shutdown()
    }
}
