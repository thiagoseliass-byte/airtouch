package com.airtouch

import android.app.Application
import android.graphics.Bitmap
import android.os.SystemClock
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.airtouch.detection.HandDetectionEngine
import com.airtouch.gesture.Gesture
import com.airtouch.gesture.GestureRecognitionEngine
import com.airtouch.model.HandFrame
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class GestureEvent(val gesture: Gesture, val timeMs: Long)

data class AirTouchUiState(
    val controlEnabled: Boolean = false,
    val modelReady: Boolean = false,
    val error: String? = null,
    val frame: HandFrame? = null,
    val rawGesture: Gesture = Gesture.NONE,
    val stableGesture: Gesture = Gesture.NONE,
    val confidence: Float = 0f,
    val recent: List<GestureEvent> = emptyList(),
    val fps: Int = 0,
    val latencyMs: Long = 0,
)

/**
 * Liga câmera -> detecção -> reconhecimento -> interface.
 * Na Fase 2 o resultado estável também será enviado ao GestureActionDispatcher.
 */
class AirTouchViewModel(app: Application) : AndroidViewModel(app) {

    private val _state = MutableStateFlow(AirTouchUiState())
    val state: StateFlow<AirTouchUiState> = _state.asStateFlow()

    private val gestures = GestureRecognitionEngine()

    @Volatile private var detector: HandDetectionEngine? = null

    private var fpsWindowStart = 0L
    private var fpsCount = 0
    private var fps = 0

    fun setControlEnabled(enabled: Boolean) {
        if (enabled == _state.value.controlEnabled) return
        if (enabled) {
            _state.update { AirTouchUiState(controlEnabled = true, recent = it.recent) }
            viewModelScope.launch(Dispatchers.Default) { startDetector() }
        } else {
            stopDetector()
            _state.update { AirTouchUiState(controlEnabled = false, recent = it.recent) }
        }
    }

    private fun startDetector() {
        try {
            val engine = HandDetectionEngine(
                context = getApplication(),
                maxHands = 1,
                onResult = ::onHandFrame,
                onError = ::onError,
            )
            engine.start()
            if (!_state.value.controlEnabled) { // foi desligado enquanto carregava
                engine.close()
                return
            }
            gestures.reset()
            detector = engine
            _state.update { it.copy(modelReady = true, error = null) }
        } catch (e: Exception) {
            _state.update {
                it.copy(error = "Falha ao carregar o modelo (hand_landmarker.task em assets?): ${e.message}")
            }
        }
    }

    private fun stopDetector() {
        detector?.close()
        detector = null
        gestures.reset()
    }

    /** Chamado pela thread de análise da câmera para cada quadro. */
    fun onCameraFrame(bitmap: Bitmap, timestampMs: Long) {
        detector?.detect(bitmap, timestampMs)
    }

    /** Chamado pela thread do MediaPipe para cada resultado. */
    @Synchronized
    private fun onHandFrame(frame: HandFrame) {
        val now = SystemClock.uptimeMillis()
        if (now - fpsWindowStart >= 1000) {
            fps = fpsCount
            fpsCount = 0
            fpsWindowStart = now
        }
        fpsCount++

        val hand = frame.hands.maxByOrNull { it.score }
        val s = gestures.process(hand, frame.imageWidth, frame.imageHeight)

        _state.update { cur ->
            if (!cur.controlEnabled) return@update cur
            var recent = cur.recent
            if (s.stable != cur.stableGesture && s.stable.isCommand) {
                recent = (listOf(GestureEvent(s.stable, System.currentTimeMillis())) + recent).take(6)
            }
            cur.copy(
                frame = frame,
                rawGesture = s.raw,
                stableGesture = s.stable,
                confidence = s.confidence,
                recent = recent,
                fps = fps,
                latencyMs = frame.latencyMs,
            )
        }
    }

    private fun onError(message: String) {
        _state.update { it.copy(error = message) }
    }

    override fun onCleared() {
        stopDetector()
    }
}
