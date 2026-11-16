package com.airtouch

import android.app.Application
import android.graphics.Bitmap
import android.os.SystemClock
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.airtouch.control.AirAction
import com.airtouch.control.DemoContent
import com.airtouch.control.GestureActionDispatcher
import com.airtouch.detection.HandDetectionEngine
import com.airtouch.gesture.Gesture
import com.airtouch.gesture.GestureRecognitionEngine
import com.airtouch.gesture.SwipeDetector
import com.airtouch.model.HandFrame
import com.airtouch.model.LM
import com.airtouch.mouse.TouchInjector
import com.airtouch.mouse.MouseEvent
import com.airtouch.mouse.VirtualMouse
import com.airtouch.settings.SettingsManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class GestureEvent(val gesture: Gesture, val timeMs: Long)

data class LogEntry(val timeMs: Long, val text: String)

data class AirTouchUiState(
    val controlEnabled: Boolean = false,
    val modelReady: Boolean = false,
    val error: String? = null,
    val handCount: Int = 0,
    val rawGesture: Gesture = Gesture.NONE,
    val stableGesture: Gesture = Gesture.NONE,
    val confidence: Float = 0f,
    val recent: List<GestureEvent> = emptyList(),
    val fps: Int = 0,
    val latencyMs: Long = 0,
    val locked: Boolean = false,
    val panelVisible: Boolean = false,
    val slideIndex: Int = 0,
    val log: List<LogEntry> = emptyList(),
    val sensitivity: Float = 0.5f,
    val invertScroll: Boolean = false,
)

/**
 * Liga: câmera -> detecção -> reconhecimento -> (mouse virtual | despachante de ações) -> interface.
 */
class AirTouchViewModel(app: Application) : AndroidViewModel(app) {

    private val settings = SettingsManager(app)

    private val _state = MutableStateFlow(
        AirTouchUiState(sensitivity = settings.sensitivity, invertScroll = settings.invertScroll)
    )
    val state: StateFlow<AirTouchUiState> = _state.asStateFlow()

    // Fluxos separados e rápidos (a cada quadro), lidos só pelos overlays.
    private val _frame = MutableStateFlow<HandFrame?>(null)
    val frame: StateFlow<HandFrame?> = _frame.asStateFlow()

    private val _cursor = MutableStateFlow<VirtualMouse.Cursor?>(null)
    val cursor: StateFlow<VirtualMouse.Cursor?> = _cursor.asStateFlow()

    private val lock = Any()
    private val gestures = GestureRecognitionEngine(framesToConfirm = 3, framesToRelease = 6)
    private val dispatcher = GestureActionDispatcher()
    private val swipe = SwipeDetector()
    private val mouse = VirtualMouse(
        VirtualMouse.Config(settings.sensitivity, settings.invertScroll)
    )

    @Volatile private var detector: HandDetectionEngine? = null
    @Volatile private var injector: TouchInjector? = null

    private var lastStable = Gesture.NONE
    private var wasLocked = false
    private var fpsWindowStart = 0L
    private var fpsCount = 0
    private var fps = 0
    private var latencySum = 0L
    private var avgLatency = 0L

    // ---------------------------------------------------------------- controle

    fun attachInjector(value: TouchInjector) {
        injector = value
    }

    fun detachInjector() {
        injector?.release()
        injector = null
    }

    fun setControlEnabled(enabled: Boolean) {
        if (enabled == _state.value.controlEnabled) return
        if (enabled) {
            _state.update { freshState(it, true) }
            viewModelScope.launch(Dispatchers.Default) { startDetector() }
        } else {
            stopDetector()
            _state.update { freshState(it, false) }
        }
    }

    fun setSensitivity(value: Float) {
        settings.sensitivity = value
        mouse.config = mouse.config.copy(sensitivity = value)
        _state.update { it.copy(sensitivity = value) }
    }

    fun setInvertScroll(value: Boolean) {
        settings.invertScroll = value
        mouse.config = mouse.config.copy(invertScroll = value)
        _state.update { it.copy(invertScroll = value) }
    }

    fun changeSlide(delta: Int) {
        _state.update {
            it.copy(slideIndex = (it.slideIndex + delta).coerceIn(0, DemoContent.slides.lastIndex))
        }
    }

    private fun freshState(cur: AirTouchUiState, enabled: Boolean) = cur.copy(
        controlEnabled = enabled,
        modelReady = false,
        error = null,
        handCount = 0,
        rawGesture = Gesture.NONE,
        stableGesture = Gesture.NONE,
        confidence = 0f,
        fps = 0,
        latencyMs = 0,
        locked = false,
        panelVisible = false,
    )

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
            resetPipeline()
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
        resetPipeline()
    }

    private fun resetPipeline() {
        synchronized(lock) {
            injectAll(mouse.reset())
            gestures.reset()
            dispatcher.reset()
            swipe.reset()
            lastStable = Gesture.NONE
            wasLocked = false
            _frame.value = null
            _cursor.value = null
        }
    }

    // ---------------------------------------------------------------- pipeline

    /** Chamado pela thread de análise da câmera para cada quadro. */
    fun onCameraFrame(bitmap: Bitmap, timestampMs: Long) {
        detector?.detect(bitmap, timestampMs)
    }

    /** Chamado pela thread do MediaPipe para cada resultado. */
    private fun onHandFrame(frame: HandFrame) {
        synchronized(lock) {
            if (!_state.value.controlEnabled) return
            _frame.value = frame

            val now = SystemClock.uptimeMillis()
            if (now - fpsWindowStart >= 1000) {
                fps = fpsCount
                avgLatency = if (fpsCount > 0) latencySum / fpsCount else 0
                fpsCount = 0
                latencySum = 0
                fpsWindowStart = now
            }
            fpsCount++
            latencySum += frame.latencyMs

            val hand = frame.hands.maxByOrNull { it.score }
            val s = gestures.process(hand, frame.imageWidth, frame.imageHeight)

            val previousStable = lastStable
            if (s.stable != previousStable) {
                lastStable = s.stable
                dispatcher.onStableGesture(s.stable)?.let { applyAction(it) }
            }

            val locked = dispatcher.locked
            if (locked) {
                if (!wasLocked) injectAll(mouse.reset())
                swipe.reset()
                _cursor.value = null
            } else {
                injectAll(mouse.update(s.stable, hand, now))
                _cursor.value = mouse.cursor

                if (hand != null && s.stable == Gesture.OPEN_PALM) {
                    val wrist = hand.landmarks[LM.WRIST]
                    val mid = hand.landmarks[LM.MIDDLE_MCP]
                    val cx = (wrist.x + mid.x) / 2f
                    val cy = (wrist.y + mid.y) / 2f
                    swipe.update(cx, cy, now)?.let { dir ->
                        dispatcher.onSwipe(dir)?.let { applyAction(it) }
                    }
                } else {
                    swipe.reset()
                }
            }
            wasLocked = locked

            val confidence = (s.confidence * 20).toInt() / 20f
            _state.update { cur ->
                if (!cur.controlEnabled) {
                    cur
                } else {
                    var recent = cur.recent
                    if (s.stable != previousStable && s.stable.isCommand) {
                        recent = (listOf(GestureEvent(s.stable, System.currentTimeMillis())) + recent).take(6)
                    }
                    cur.copy(
                        handCount = frame.hands.size,
                        rawGesture = s.raw,
                        stableGesture = s.stable,
                        confidence = confidence,
                        recent = recent,
                        fps = fps,
                        latencyMs = avgLatency,
                        locked = dispatcher.locked,
                        panelVisible = s.stable == Gesture.OPEN_PALM && !dispatcher.locked,
                    )
                }
            }
        }
    }

    private fun injectAll(events: List<MouseEvent>) {
        val inj = injector ?: return
        events.forEach { inj.handle(it) }
    }

    private fun applyAction(action: AirAction) {
        when (action) {
            AirAction.Confirm -> logEvent("Confirmar ✔")
            AirAction.Cancel -> logEvent("Cancelar ✘")
            AirAction.NextSlide -> {
                changeSlide(+1)
                logEvent("Slide seguinte →")
            }

            AirAction.PrevSlide -> {
                changeSlide(-1)
                logEvent("← Slide anterior")
            }

            AirAction.ScrollUp -> scrollStep(handMovedUp = true)
            AirAction.ScrollDown -> scrollStep(handMovedUp = false)
            is AirAction.Pause ->
                logEvent(if (action.paused) "Pausado (punho)" else "Retomado (mão aberta)")
        }
    }

    /** Rolagem de um passo, no ponto onde o cursor esteve por último. */
    private fun scrollStep(handMovedUp: Boolean) {
        val fingerUp = handMovedUp != mouse.config.invertScroll
        val from = if (fingerUp) 0.65f else 0.35f
        val to = if (fingerUp) 0.35f else 0.65f
        injector?.swipeVertical(mouse.lastX, from, to)
        logEvent(if (handMovedUp) "Rolar ↑" else "Rolar ↓")
    }

    private fun logEvent(text: String) {
        val entry = LogEntry(System.currentTimeMillis(), text)
        _state.update { it.copy(log = (listOf(entry) + it.log).take(6)) }
    }

    private fun onError(message: String) {
        _state.update { it.copy(error = message) }
    }

    override fun onCleared() {
        stopDetector()
        injector = null
    }
}
