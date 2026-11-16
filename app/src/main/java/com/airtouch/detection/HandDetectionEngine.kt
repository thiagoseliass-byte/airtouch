package com.airtouch.detection

import android.content.Context
import android.graphics.Bitmap
import android.os.SystemClock
import com.airtouch.model.Hand
import com.airtouch.model.HandFrame
import com.airtouch.model.Landmark
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.framework.image.MPImage
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.core.Delegate
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarker
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarkerResult

/**
 * Envolve o MediaPipe Hand Landmarker em modo LIVE_STREAM (assíncrono).
 * Tudo roda no aparelho; o modelo vem de assets/hand_landmarker.task.
 */
class HandDetectionEngine(
    private val context: Context,
    private val maxHands: Int = 1,
    private val preferGpu: Boolean = false,
    private val onResult: (HandFrame) -> Unit,
    private val onError: (String) -> Unit,
) : AutoCloseable {

    private var landmarker: HandLandmarker? = null
    private var lastTimestamp = 0L

    /** Carrega o modelo. Pode demorar algumas centenas de ms: chame fora da thread principal. */
    @Synchronized
    fun start() {
        if (landmarker != null) return
        landmarker = if (preferGpu) {
            try {
                build(Delegate.GPU)
            } catch (e: Exception) {
                build(Delegate.CPU) // GPU indisponível neste aparelho: usa CPU
            }
        } else {
            build(Delegate.CPU)
        }
    }

    private fun build(delegate: Delegate): HandLandmarker {
        val base = BaseOptions.builder()
            .setModelAssetPath(MODEL_ASSET)
            .setDelegate(delegate)
            .build()

        val options = HandLandmarker.HandLandmarkerOptions.builder()
            .setBaseOptions(base)
            .setRunningMode(RunningMode.LIVE_STREAM)
            .setNumHands(maxHands)
            .setMinHandDetectionConfidence(0.5f)
            .setMinHandPresenceConfidence(0.5f)
            .setMinTrackingConfidence(0.5f)
            .setResultListener { result, input -> handleResult(result, input) }
            .setErrorListener { e -> onError(e.message ?: "Erro desconhecido no detector") }
            .build()

        return HandLandmarker.createFromOptions(context, options)
    }

    /** Envia um quadro (já rotacionado e espelhado quando necessário). */
    @Synchronized
    fun detect(bitmap: Bitmap, timestampMs: Long) {
        val lm = landmarker ?: return
        // O MediaPipe exige timestamps estritamente crescentes.
        val ts = if (timestampMs <= lastTimestamp) lastTimestamp + 1 else timestampMs
        lastTimestamp = ts
        lm.detectAsync(BitmapImageBuilder(bitmap).build(), ts)
    }

    private fun handleResult(result: HandLandmarkerResult, input: MPImage) {
        val hands = result.landmarks().mapIndexed { i, points ->
            val category = result.handedness().getOrNull(i)?.firstOrNull()
            Hand(
                landmarks = points.map { Landmark(it.x(), it.y(), it.z()) },
                label = category?.categoryName() ?: "?",
                score = category?.score() ?: 0f,
            )
        }
        val latency = SystemClock.uptimeMillis() - result.timestampMs()
        onResult(
            HandFrame(
                hands = hands,
                imageWidth = input.width,
                imageHeight = input.height,
                latencyMs = latency,
                timestampMs = result.timestampMs(),
            )
        )
    }

    @Synchronized
    override fun close() {
        landmarker?.close()
        landmarker = null
    }

    companion object {
        const val MODEL_ASSET = "hand_landmarker.task"
    }
}
