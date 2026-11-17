package com.airtouch

import android.app.Application
import android.graphics.Bitmap
import android.net.Uri
import android.os.SystemClock
import android.provider.OpenableColumns
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.airtouch.control.AirAction
import com.airtouch.control.ControlPipeline
import com.airtouch.control.DemoContent
import com.airtouch.detection.HandDetectionEngine
import com.airtouch.docs.PdfController
import com.airtouch.gesture.Gesture
import com.airtouch.model.HandFrame
import com.airtouch.mouse.MouseEvent
import com.airtouch.mouse.TouchInjector
import com.airtouch.mouse.VirtualMouse
import com.airtouch.settings.SettingsManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

data class GestureEvent(val gesture: Gesture, val timeMs: Long)

data class LogEntry(val timeMs: Long, val text: String)

data class AirTouchUiState(
    val tab: AppTab = AppTab.PANEL,
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

/** Estado do documento (PDF) aberto nas abas Documentos e Apresentações. */
data class DocState(
    val name: String? = null,
    val pageCount: Int = 0,
    val page: Int = 0,
    val zoom: Float = 1f,
    val presenting: Boolean = false,
    val loading: Boolean = false,
    val error: String? = null,
    /** Aumenta a cada documento aberto (para o renderizador saber que mudou). */
    val version: Int = 0,
)

/**
 * Liga: câmera -> detecção -> [ControlPipeline] -> (mouse virtual no app | ações) -> interface.
 * Este é o modo "dentro do app". O modo global (outros apps) fica em AirTouchService.
 */
class AirTouchViewModel(app: Application) : AndroidViewModel(app) {

    private val settings = SettingsManager(app)

    private val _state = MutableStateFlow(
        AirTouchUiState(sensitivity = settings.sensitivity, invertScroll = settings.invertScroll)
    )
    val state: StateFlow<AirTouchUiState> = _state.asStateFlow()

    private val _doc = MutableStateFlow(DocState())
    val doc: StateFlow<DocState> = _doc.asStateFlow()

    // Fluxos separados e rápidos (a cada quadro), lidos só pelos overlays.
    private val _frame = MutableStateFlow<HandFrame?>(null)
    val frame: StateFlow<HandFrame?> = _frame.asStateFlow()

    private val _cursor = MutableStateFlow<VirtualMouse.Cursor?>(null)
    val cursor: StateFlow<VirtualMouse.Cursor?> = _cursor.asStateFlow()

    val pdf = PdfController(app)

    private val lock = Any()
    private val pipeline = ControlPipeline(
        VirtualMouse.Config(settings.sensitivity, settings.invertScroll)
    )

    @Volatile private var detector: HandDetectionEngine? = null
    @Volatile private var injector: TouchInjector? = null

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

    fun setTab(tab: AppTab) {
        _state.update { it.copy(tab = tab) }
        if (tab != AppTab.PRESENT) _doc.update { it.copy(presenting = false) }
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
        pipeline.mouse.config = pipeline.mouse.config.copy(sensitivity = value)
        _state.update { it.copy(sensitivity = value) }
    }

    fun setInvertScroll(value: Boolean) {
        settings.invertScroll = value
        pipeline.mouse.config = pipeline.mouse.config.copy(invertScroll = value)
        _state.update { it.copy(invertScroll = value) }
    }

    fun changeSlide(delta: Int) {
        _state.update {
            it.copy(slideIndex = (it.slideIndex + delta).coerceIn(0, DemoContent.slides.lastIndex))
        }
    }

    // ---------------------------------------------------------------- documentos

    fun openPdf(uri: Uri) {
        viewModelScope.launch {
            _doc.update { it.copy(loading = true, error = null) }
            try {
                val count = pdf.open(uri)
                val name = queryName(uri)
                _doc.update {
                    DocState(name = name, pageCount = count, version = it.version + 1)
                }
                logEvent("PDF aberto: $name")
            } catch (e: SecurityException) {
                _doc.update {
                    DocState(error = "PDF protegido por senha não é suportado.", version = it.version + 1)
                }
            } catch (e: Exception) {
                _doc.update {
                    DocState(error = "Não foi possível abrir o PDF: ${e.message}", version = it.version + 1)
                }
            }
        }
    }

    private fun queryName(uri: Uri): String {
        return try {
            getApplication<Application>().contentResolver
                .query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                ?.use { c -> if (c.moveToFirst()) c.getString(0) else null }
                ?: "documento.pdf"
        } catch (e: Exception) {
            "documento.pdf"
        }
    }

    fun changePage(delta: Int) {
        _doc.update {
            if (it.pageCount == 0) it
            else it.copy(page = (it.page + delta).coerceIn(0, it.pageCount - 1))
        }
    }

    fun zoomBy(delta: Float) {
        _doc.update {
            val z = ((it.zoom + delta) * 4f).roundToInt() / 4f
            it.copy(zoom = z.coerceIn(1f, 3f))
        }
    }

    fun setPresenting(on: Boolean) {
        _doc.update { if (on && it.pageCount == 0) it else it.copy(presenting = on) }
    }

    // ---------------------------------------------------------------- pipeline

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
            injectAll(pipeline.reset())
            _frame.value = null
            _cursor.value = null
        }
    }

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
            val out = pipeline.process(hand, frame.imageWidth, frame.imageHeight, now)

            out.actions.forEach { applyAction(it) }
            injectAll(out.mouseEvents)
            _cursor.value = out.cursor

            val confidence = (out.confidence * 20).toInt() / 20f
            _state.update { cur ->
                if (!cur.controlEnabled) {
                    cur
                } else {
                    var recent = cur.recent
                    if (out.stableChanged && out.stable.isCommand) {
                        recent = (listOf(GestureEvent(out.stable, System.currentTimeMillis())) + recent).take(6)
                    }
                    cur.copy(
                        handCount = frame.hands.size,
                        rawGesture = out.raw,
                        stableGesture = out.stable,
                        confidence = confidence,
                        recent = recent,
                        fps = fps,
                        latencyMs = avgLatency,
                        locked = out.locked,
                        panelVisible = out.palmShown,
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
        val tab = _state.value.tab
        val inDocument = tab == AppTab.DOCS || tab == AppTab.PRESENT
        when (action) {
            AirAction.Confirm -> logEvent("Confirmar ✔")
            AirAction.Cancel -> {
                if (_doc.value.presenting) {
                    setPresenting(false)
                    logEvent("Apresentação encerrada")
                } else {
                    logEvent("Cancelar ✘")
                }
            }

            AirAction.NextSlide -> {
                if (inDocument) changePage(+1) else changeSlide(+1)
                logEvent(if (inDocument) "Página seguinte →" else "Slide seguinte →")
            }

            AirAction.PrevSlide -> {
                if (inDocument) changePage(-1) else changeSlide(-1)
                logEvent(if (inDocument) "← Página anterior" else "← Slide anterior")
            }

            AirAction.ScrollUp -> scrollStep(handMovedUp = true)
            AirAction.ScrollDown -> scrollStep(handMovedUp = false)
            is AirAction.Pause ->
                logEvent(if (action.paused) "Pausado (punho)" else "Retomado (mão aberta)")
        }
    }

    /** Rolagem de um passo, no ponto onde o cursor esteve por último. */
    private fun scrollStep(handMovedUp: Boolean) {
        val fingerUp = handMovedUp != pipeline.mouse.config.invertScroll
        val from = if (fingerUp) 0.65f else 0.35f
        val to = if (fingerUp) 0.35f else 0.65f
        injector?.swipeVertical(pipeline.mouse.lastX, from, to)
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
        // viewModelScope já foi cancelado aqui; usa um escopo próprio só para fechar o PDF.
        CoroutineScope(Dispatchers.IO).launch { pdf.close() }
    }
}
