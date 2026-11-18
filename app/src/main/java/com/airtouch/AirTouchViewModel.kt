package com.airtouch

import android.app.Application
import android.graphics.Bitmap
import android.net.Uri
import android.os.SystemClock
import android.provider.OpenableColumns
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.airtouch.control.ActionId
import com.airtouch.control.AdaptiveRate
import com.airtouch.control.SmartProfiles
import com.airtouch.gesture.GestureClassifier
import com.airtouch.gesture.PinchCalibration
import com.airtouch.control.AirAction
import com.airtouch.control.ControlPipeline
import com.airtouch.control.CustomGesture
import com.airtouch.control.CustomGestureEngine
import com.airtouch.control.DemoContent
import com.airtouch.control.Profile
import com.airtouch.detection.HandDetectionEngine
import com.airtouch.docs.PdfController
import com.airtouch.games.GameController
import com.airtouch.games.GameInput
import com.airtouch.gesture.Gesture
import com.airtouch.model.HandFrame
import com.airtouch.mouse.MouseEvent
import com.airtouch.mouse.TouchInjector
import com.airtouch.mouse.VirtualMouse
import com.airtouch.settings.ProfileStore
import com.airtouch.settings.SettingsManager
import com.airtouch.system.SystemActions
import com.airtouch.writing.AirWriting
import com.airtouch.writing.InkRecognizer
import com.airtouch.writing.ShapeRecognizer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.math.ceil
import kotlin.math.roundToInt

data class GestureEvent(val gesture: Gesture, val timeMs: Long)

data class LogEntry(val timeMs: Long, val text: String)

enum class WriteMode(val label: String) {
    TEXT("Texto"),
    FREE("Livre"),
    SHAPES("Formas"),
}

enum class InkModel { CHECKING, MISSING, DOWNLOADING, READY, ERROR }

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
    // ---- Fase 4
    val zoomActive: Boolean = false,
    /** Diagnóstico da pinça: distância polegar-indicador e abertura do indicador (em palmas). */
    val pinchDist: Float = 0f,
    val indexOpen: Float = 0f,
    val menuVisible: Boolean = false,
    val profiles: List<Profile> = Profile.defaults(),
    val activeProfile: String = Profile.GENERAL,
    val customGestures: List<CustomGesture> = emptyList(),
    val customLive: String? = null,
    val recordMsg: String? = null,
    val writeMode: WriteMode = WriteMode.TEXT,
    val writeText: String = "",
    val inkModel: InkModel = InkModel.CHECKING,
    val inkBusy: Boolean = false,
    val inkMessage: String? = null,
    // ---- Fase 5
    val autoProfile: Boolean = true,
    val powerSaver: Boolean = false,
    val qualityFilter: Boolean = true,
    val pinchScale: Float = 1f,
    /** Perfil escolhido automaticamente (null = usando o perfil do usuário). */
    val smartProfile: String? = null,
    val calibMsg: String? = null,
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

/** Retângulo (em pixels da janela) da área de escrita ou de jogo, e tamanho da janela. */
class Area(val l: Float, val t: Float, val w: Float, val h: Float, val rootW: Float, val rootH: Float)

/**
 * Liga: câmera -> detecção -> [ControlPipeline] -> (mouse virtual no app | ações) -> interface.
 * Este é o modo "dentro do app". O modo global (outros apps) fica em AirTouchService.
 */
class AirTouchViewModel(app: Application) : AndroidViewModel(app) {

    private val settings = SettingsManager(app)
    private val store = ProfileStore(app)
    private val system = SystemActions(app)
    private val recognizer = InkRecognizer(app)

    private val _state = MutableStateFlow(
        AirTouchUiState(
            sensitivity = settings.sensitivity,
            invertScroll = settings.invertScroll,
            autoProfile = settings.autoProfile,
            powerSaver = settings.powerSaver,
            qualityFilter = settings.qualityFilter,
            pinchScale = settings.pinchScale,
        )
    )
    val state: StateFlow<AirTouchUiState> = _state.asStateFlow()

    private val _doc = MutableStateFlow(DocState())
    val doc: StateFlow<DocState> = _doc.asStateFlow()

    // Fluxos separados e rápidos (a cada quadro), lidos só pelos overlays.
    private val _frame = MutableStateFlow<HandFrame?>(null)
    val frame: StateFlow<HandFrame?> = _frame.asStateFlow()

    private val _cursor = MutableStateFlow<VirtualMouse.Cursor?>(null)
    val cursor: StateFlow<VirtualMouse.Cursor?> = _cursor.asStateFlow()

    private val _inkVersion = MutableStateFlow(0)
    val inkVersion: StateFlow<Int> = _inkVersion.asStateFlow()

    private val _game = MutableStateFlow(GameInput(null, null, 0, 0, false))
    val gameInput: StateFlow<GameInput> = _game.asStateFlow()

    val pdf = PdfController(app)
    val writing = AirWriting()

    private val lock = Any()
    private val pipeline = ControlPipeline(
        VirtualMouse.Config(settings.sensitivity, settings.invertScroll)
    )

    @Volatile private var detector: HandDetectionEngine? = null
    @Volatile private var injector: TouchInjector? = null
    @Volatile private var area: Area? = null

    private var fpsWindowStart = 0L
    private var fpsCount = 0
    private var fps = 0
    private var latencySum = 0L
    private var avgLatency = 0L

    private val rate = AdaptiveRate()
    private var calibration: PinchCalibration? = null
    private var calibStart = 0L

    private var prevPenDown = false
    private var menuUntil = 0L
    private var inkBusy = false

    // gravação de gesto personalizado
    private var recording = false
    private var recStart = 0L
    private val recSamples = ArrayList<FloatArray>()

    init {
        val profiles = store.loadProfiles()
        val active = profiles.firstOrNull { it.name == store.activeName } ?: profiles.first()
        val gestures = store.loadGestures()
        pipeline.dispatcher.profile = active
        pipeline.custom.gestures = gestures
        GestureClassifier.pinchScale = settings.pinchScale
        rate.powerSaver = settings.powerSaver
        pipeline.minScore = if (settings.qualityFilter) MIN_QUALITY else 0f
        _state.update { it.copy(profiles = profiles, activeProfile = active.name, customGestures = gestures) }
        refreshProfile()
    }

    // ---------------------------------------------------------------- Fase 5: ajustes

    /** Chamado pelo analisador da câmera: decide se o quadro será processado (bateria). */
    fun shouldAnalyze(nowMs: Long): Boolean = rate.shouldProcess(nowMs)

    /** Perfil em uso = automático (por aba) quando ligado; senão o escolhido pelo usuário. */
    private fun refreshProfile() {
        val st = _state.value
        val smart = if (st.autoProfile) SmartProfiles.forTab(st.tab.name) else null
        val name = smart ?: st.activeProfile
        val p = st.profiles.firstOrNull { it.name == name } ?: st.profiles.first()
        pipeline.dispatcher.profile = p
        if (st.smartProfile != smart) _state.update { it.copy(smartProfile = smart) }
    }

    fun setAutoProfile(on: Boolean) {
        settings.autoProfile = on
        _state.update { it.copy(autoProfile = on) }
        refreshProfile()
    }

    fun setPowerSaver(on: Boolean) {
        settings.powerSaver = on
        rate.powerSaver = on
        _state.update { it.copy(powerSaver = on) }
    }

    fun setQualityFilter(on: Boolean) {
        settings.qualityFilter = on
        pipeline.minScore = if (on) MIN_QUALITY else 0f
        _state.update { it.copy(qualityFilter = on) }
    }

    /** Calibra a pinça: o usuário faz a pinça do jeito dele e segura por alguns segundos. */
    fun startPinchCalibration() {
        synchronized(lock) {
            if (!_state.value.controlEnabled || calibration != null || recording) return
            calibration = PinchCalibration()
            calibStart = SystemClock.uptimeMillis()
        }
        _state.update { it.copy(calibMsg = "Prepare a pinça… 3") }
    }

    fun resetPinchCalibration() {
        settings.pinchScale = 1f
        GestureClassifier.pinchScale = 1f
        _state.update { it.copy(pinchScale = 1f, calibMsg = "Pinça voltou ao padrão.") }
    }

    private fun handleCalibration(out: ControlPipeline.Output, hasHand: Boolean, now: Long) {
        val c = calibration ?: return
        val elapsed = now - calibStart
        if (elapsed < RECORD_COUNTDOWN_MS) {
            val sec = ceil((RECORD_COUNTDOWN_MS - elapsed) / 1000.0).toInt()
            setCalibMsg("Faça a pinça e SEGURE… $sec")
            return
        }
        if (elapsed > RECORD_TIMEOUT_MS) {
            calibration = null
            setCalibMsg("Não consegui medir a pinça. Tente de novo, com a mão bem à vista.")
            return
        }
        if (hasHand) c.add(out.pinchDist)
        setCalibMsg("Medindo a pinça… ${c.count}/25")
        val scale = c.result()
        if (scale != null) {
            calibration = null
            settings.pinchScale = scale
            GestureClassifier.pinchScale = scale
            _state.update { it.copy(pinchScale = scale, calibMsg = "✔ Pinça calibrada (ajuste ${"%.2f".format(scale)}×)") }
        }
    }

    private fun setCalibMsg(msg: String) {
        _state.update { if (it.calibMsg == msg) it else it.copy(calibMsg = msg) }
    }

    // ---------------------------------------------------------------- controle

    fun attachInjector(value: TouchInjector) {
        injector = value
    }

    fun detachInjector() {
        injector?.release()
        injector = null
    }

    /** A tela informa onde está a área de escrita/jogo (pixels da janela). */
    fun setArea(l: Float, t: Float, w: Float, h: Float, rootW: Float, rootH: Float) {
        area = if (w > 1f && h > 1f && rootW > 1f && rootH > 1f) Area(l, t, w, h, rootW, rootH) else null
    }

    fun setTab(tab: AppTab) {
        synchronized(lock) {
            // Encerra qualquer toque/arraste em andamento antes de mudar o comportamento.
            injectAll(pipeline.reset())
            if (writing.penDown) writing.update(false, 0f, 0f, SystemClock.uptimeMillis())
            prevPenDown = false
            area = null
        }
        _state.update { it.copy(tab = tab, menuVisible = false) }
        refreshProfile()
        if (tab != AppTab.PRESENT) _doc.update { it.copy(presenting = false) }
        if (tab == AppTab.WRITE) checkInkModel()
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

    /** Passo de zoom (botões e gestos de polegar). */
    fun zoomBy(delta: Float) {
        _doc.update {
            val z = ((it.zoom + delta) * 4f).roundToInt() / 4f
            it.copy(zoom = z.coerceIn(1f, 4f))
        }
    }

    /** Zoom contínuo (duas mãos). */
    private fun zoomScale(factor: Float) {
        _doc.update { it.copy(zoom = (it.zoom * factor).coerceIn(1f, 4f)) }
    }

    fun setPresenting(on: Boolean) {
        _doc.update { if (on && it.pageCount == 0) it else it.copy(presenting = on) }
    }

    // ---------------------------------------------------------------- escrita no ar

    fun setWriteMode(mode: WriteMode) {
        _state.update { it.copy(writeMode = mode) }
    }

    private fun bumpInk() {
        _inkVersion.update { it + 1 }
    }

    fun checkInkModel() {
        viewModelScope.launch(Dispatchers.Default) {
            val cur = _state.value.inkModel
            if (cur == InkModel.READY || cur == InkModel.DOWNLOADING) return@launch
            _state.update { it.copy(inkModel = InkModel.CHECKING) }
            val ready = try { recognizer.isReady() } catch (e: Exception) { false }
            _state.update { it.copy(inkModel = if (ready) InkModel.READY else InkModel.MISSING) }
        }
    }

    /** Baixa o modelo de escrita (uma única vez; precisa de internet). */
    fun downloadInkModel() {
        if (_state.value.inkModel == InkModel.DOWNLOADING) return
        _state.update { it.copy(inkModel = InkModel.DOWNLOADING, inkMessage = null) }
        viewModelScope.launch(Dispatchers.Default) {
            try {
                recognizer.download()
                _state.update { it.copy(inkModel = InkModel.READY, inkMessage = null) }
            } catch (e: Exception) {
                _state.update {
                    it.copy(
                        inkModel = InkModel.ERROR,
                        inkMessage = "Não foi possível baixar o modelo. Conecte-se à internet e tente de novo.",
                    )
                }
            }
        }
    }

    fun recognizeInkNow() {
        if (_state.value.inkModel != InkModel.READY || inkBusy) return
        val strokes = writing.snapshot()
        if (strokes.isEmpty()) return
        inkBusy = true
        _state.update { it.copy(inkBusy = true) }
        viewModelScope.launch(Dispatchers.Default) {
            try {
                val text = recognizer.recognize(strokes)
                writing.dropFirst(strokes.size)
                if (!text.isNullOrBlank()) appendWord(text.trim())
                bumpInk()
            } catch (e: Exception) {
                _state.update { it.copy(inkMessage = "Falha ao reconhecer a escrita.") }
            } finally {
                inkBusy = false
                _state.update { it.copy(inkBusy = false) }
            }
        }
    }

    private fun appendWord(word: String) {
        _state.update {
            val t = it.writeText
            it.copy(writeText = if (t.isEmpty() || t.endsWith(" ")) t + word else "$t $word")
        }
    }

    fun writeSpace() = _state.update { it.copy(writeText = it.writeText + " ") }

    fun writeBackspace() = _state.update { it.copy(writeText = it.writeText.dropLast(1)) }

    fun writeClearText() = _state.update { it.copy(writeText = "") }

    fun writeUndoStroke() {
        writing.undo()
        bumpInk()
    }

    fun writeClearInk() {
        writing.clear()
        bumpInk()
    }

    private fun handleWriting(out: ControlPipeline.Output, now: Long) {
        val b = area
        val c = out.cursor
        var down = false
        var u = 0f
        var v = 0f
        if (b != null && c != null && !out.locked && !out.zoomActive) {
            val px = c.x * b.rootW
            val py = c.y * b.rootH
            u = (px - b.l) / b.w
            v = (py - b.t) / b.w // mesma escala nos dois eixos (unidades de largura)
            val inside = u in 0f..1f && v in 0f..(b.h / b.w)
            down = inside && out.stable == Gesture.PINCH
        }
        val changed = writing.update(down, u, v, now)
        val wasDown = prevPenDown
        prevPenDown = down

        if (wasDown && !down && _state.value.writeMode == WriteMode.SHAPES) {
            writing.snapshot().lastOrNull()?.let { last ->
                ShapeRecognizer.snap(last)?.let { writing.replaceLast(it) }
            }
        }
        if (changed) bumpInk()

        // Texto: depois de uma pausa sem escrever, reconhece sozinho.
        val s = _state.value
        if (s.writeMode == WriteMode.TEXT && s.inkModel == InkModel.READY && !down && !writing.isEmpty() &&
            writing.lastPenUpMs > 0 && now - writing.lastPenUpMs >= AUTO_RECOGNIZE_MS
        ) {
            recognizeInkNow()
        }
    }

    // ---------------------------------------------------------------- personalização

    fun setActiveProfile(name: String) {
        val p = _state.value.profiles.firstOrNull { it.name == name } ?: return
        store.activeName = name
        _state.update { it.copy(activeProfile = name) }
        refreshProfile()
        logEvent("Perfil: $name")
    }

    /** Troca a ação de um gatilho pela próxima da lista. */
    fun cycleBinding(profileName: String, key: String) {
        val all = _state.value.profiles
        val p = all.firstOrNull { it.name == profileName } ?: return
        val cur = p.bindings[key] ?: ActionId.NONE
        val values = ActionId.values()
        val next = values[(cur.ordinal + 1) % values.size]
        replaceProfile(p.with(key, next))
    }

    private fun replaceProfile(updated: Profile) {
        val all = _state.value.profiles.map { if (it.name == updated.name) updated else it }
        store.saveProfiles(all)
        _state.update { it.copy(profiles = all) }
        refreshProfile()
    }

    fun newProfile() {
        val st = _state.value
        val base = st.profiles.first { it.name == st.activeProfile }
        var n = 1
        while (st.profiles.any { it.name == "Meu perfil $n" }) n++
        val p = Profile("Meu perfil $n", base.bindings, builtIn = false)
        val all = st.profiles + p
        store.saveProfiles(all)
        _state.update { it.copy(profiles = all) }
        setActiveProfile(p.name)
    }

    fun deleteProfile(name: String) {
        val st = _state.value
        val p = st.profiles.firstOrNull { it.name == name } ?: return
        if (p.builtIn) return
        val all = st.profiles - p
        store.saveProfiles(all)
        _state.update { it.copy(profiles = all) }
        if (st.activeProfile == name) setActiveProfile(Profile.GENERAL)
    }

    fun startRecording() {
        synchronized(lock) {
            if (!_state.value.controlEnabled || recording) return
            recording = true
            recStart = SystemClock.uptimeMillis()
            recSamples.clear()
        }
        _state.update { it.copy(recordMsg = "Prepare a mão… 3") }
    }

    private fun handleRecording(out: ControlPipeline.Output, hand: com.airtouch.model.Hand?, w: Int, h: Int, now: Long) {
        val elapsed = now - recStart
        if (elapsed < RECORD_COUNTDOWN_MS) {
            val sec = ceil((RECORD_COUNTDOWN_MS - elapsed) / 1000.0).toInt()
            setRecordMsg("Faça o gesto e segure… $sec")
            return
        }
        if (elapsed > RECORD_TIMEOUT_MS) {
            recording = false
            setRecordMsg("Não consegui ver sua mão. Tente de novo.")
            return
        }
        if (hand != null && !out.locked) {
            CustomGestureEngine.features(hand, w, h)?.let { recSamples += it }
        }
        setRecordMsg("Gravando… ${recSamples.size}/$RECORD_SAMPLES")
        if (recSamples.size >= RECORD_SAMPLES) {
            recording = false
            val st = _state.value
            val g = CustomGesture(
                id = java.util.UUID.randomUUID().toString().take(8),
                name = "Gesto ${st.customGestures.size + 1}",
                template = CustomGestureEngine.average(recSamples),
            )
            val list = st.customGestures + g
            store.saveGestures(list)
            pipeline.custom.gestures = list
            _state.update { it.copy(customGestures = list, recordMsg = "✔ ${g.name} gravado! Escolha a ação dele abaixo.") }
        }
    }

    private fun setRecordMsg(msg: String) {
        _state.update { if (it.recordMsg == msg) it else it.copy(recordMsg = msg) }
    }

    fun clearRecordMsg() = _state.update { it.copy(recordMsg = null) }

    fun deleteCustom(id: String) {
        val st = _state.value
        val list = st.customGestures.filterNot { it.id == id }
        store.saveGestures(list)
        pipeline.custom.gestures = list
        val key = com.airtouch.control.Triggers.custom(id)
        val profiles = st.profiles.map { it.without(key) }
        store.saveProfiles(profiles)
        _state.update { it.copy(customGestures = list, profiles = profiles) }
        refreshProfile()
    }

    fun setCustomTolerance(id: String, tolerance: Float) {
        val list = _state.value.customGestures.map { if (it.id == id) it.copy(tolerance = tolerance) else it }
        store.saveGestures(list)
        pipeline.custom.gestures = list
        _state.update { it.copy(customGestures = list) }
    }

    // ---------------------------------------------------------------- menu de ações

    fun openMenu() {
        menuUntil = SystemClock.uptimeMillis() + MENU_VISIBLE_MS
        _state.update { it.copy(menuVisible = true) }
    }

    fun closeMenu() {
        _state.update { it.copy(menuVisible = false) }
    }

    fun systemVolume(up: Boolean) = system.volume(up)

    fun systemPlayPause() = system.playPause()

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
        zoomActive = false,
        menuVisible = false,
        customLive = null,
        recordMsg = null,
        calibMsg = null,
    )

    private fun startDetector() {
        try {
            val engine = HandDetectionEngine(
                context = getApplication(),
                maxHands = 2,
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
        recording = false
        calibration = null
        rate.reset()
        resetPipeline()
    }

    private fun resetPipeline() {
        synchronized(lock) {
            injectAll(pipeline.reset())
            _frame.value = null
            _cursor.value = null
            _game.value = GameInput(null, null, 0, 0, false)
            if (writing.penDown) writing.update(false, 0f, 0f, SystemClock.uptimeMillis())
            prevPenDown = false
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
            val out = pipeline.process(frame.hands, frame.imageWidth, frame.imageHeight, now)
            rate.onResult(frame.hands.isNotEmpty(), now)
            val tab = _state.value.tab

            // Nas abas de escrita/jogo (e ao gravar um gesto) a mão não gera toques no app.
            val suppress = tab == AppTab.WRITE || tab == AppTab.GAMES || recording || calibration != null
            if (!suppress) injectAll(out.mouseEvents)
            _cursor.value = out.cursor

            out.actions.forEach { applyAction(it) }

            if (out.zoomFactor != 1f && tab == AppTab.DOCS) zoomScale(out.zoomFactor)

            if (tab == AppTab.WRITE) handleWriting(out, now)
            if (tab == AppTab.GAMES) updateGame(out)
            if (recording) handleRecording(out, hand, frame.imageWidth, frame.imageHeight, now)
            if (calibration != null) handleCalibration(out, hand != null, now)

            if (_state.value.menuVisible) {
                if (out.mouseEvents.isNotEmpty()) menuUntil = now + MENU_VISIBLE_MS
                if (now > menuUntil) closeMenu()
            }

            val confidence = (out.confidence * 20).toInt() / 20f
            val pinch = (out.pinchDist * 50).toInt() / 50f
            val open = (out.indexOpen * 50).toInt() / 50f
            val live = out.customLive?.let {
                "${it.name}: ${"%.2f".format(it.distance)} / ${"%.2f".format(it.limit)}" +
                    if (it.matched) "  ✔" else ""
            }
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
                        zoomActive = out.zoomActive,
                        pinchDist = pinch,
                        indexOpen = open,
                        customLive = live,
                    )
                }
            }
        }
    }

    private fun updateGame(out: ControlPipeline.Output) {
        val b = area
        val p = out.pointer
        if (b == null || p == null || out.locked) {
            _game.value = GameInput(null, null, 0, 0, false)
            return
        }
        val u = (p.first * b.rootW - b.l) / b.w
        val v = (p.second * b.rootH - b.t) / b.h
        _game.value = GameController.map(Pair(u, v), out.stable)
    }

    private fun injectAll(events: List<MouseEvent>) {
        val inj = injector ?: return
        events.forEach { inj.handle(it) }
    }

    private fun applyAction(action: AirAction) {
        val tab = _state.value.tab
        val inDocument = tab == AppTab.DOCS || tab == AppTab.PRESENT
        when (action) {
            AirAction.Confirm -> {
                if (tab == AppTab.WRITE) recognizeInkNow()
                logEvent("Confirmar ✔")
            }

            AirAction.Cancel -> {
                if (tab == AppTab.WRITE) {
                    writeClearInk()
                    logEvent("Escrita apagada")
                } else if (_doc.value.presenting) {
                    setPresenting(false)
                    logEvent("Apresentação encerrada")
                } else {
                    logEvent("Cancelar ✘")
                }
            }

            AirAction.NextSlide -> {
                if (tab == AppTab.WRITE) {
                    writeSpace()
                    logEvent("Espaço ␣")
                } else {
                    if (inDocument) changePage(+1) else changeSlide(+1)
                    logEvent(if (inDocument) "Página seguinte →" else "Slide seguinte →")
                }
            }

            AirAction.PrevSlide -> {
                if (tab == AppTab.WRITE) {
                    writeBackspace()
                    logEvent("Apagar letra ⌫")
                } else {
                    if (inDocument) changePage(-1) else changeSlide(-1)
                    logEvent(if (inDocument) "← Página anterior" else "← Slide anterior")
                }
            }

            AirAction.ScrollUp -> scrollStep(handMovedUp = true)
            AirAction.ScrollDown -> scrollStep(handMovedUp = false)
            is AirAction.Pause ->
                logEvent(if (action.paused) "Pausado (punho)" else "Retomado (mão aberta)")

            AirAction.Back -> {
                if (_doc.value.presenting) setPresenting(false) else setTab(AppTab.PANEL)
                logEvent("Voltar")
            }

            AirAction.Home -> {
                setTab(AppTab.PANEL)
                logEvent("Início (Painel)")
            }

            AirAction.Recents -> logEvent("Apps recentes: só no Controle global")
            AirAction.VolumeUp -> {
                system.volume(true)
                logEvent("Volume +")
            }

            AirAction.VolumeDown -> {
                system.volume(false)
                logEvent("Volume −")
            }

            AirAction.PlayPause -> {
                system.playPause()
                logEvent("Play / Pause")
            }

            is AirAction.ZoomStep -> {
                if (inDocument) zoomBy(if (action.zoomIn) 0.25f else -0.25f)
                logEvent(if (action.zoomIn) "Zoom +" else "Zoom −")
            }

            AirAction.OpenMenu -> {
                openMenu()
                logEvent("Menu de ações (círculo)")
            }
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
        recognizer.close()
        // viewModelScope já foi cancelado aqui; usa um escopo próprio só para fechar o PDF.
        CoroutineScope(Dispatchers.IO).launch { pdf.close() }
    }

    private companion object {
        const val AUTO_RECOGNIZE_MS = 1500L
        const val MENU_VISIBLE_MS = 8000L
        const val RECORD_COUNTDOWN_MS = 3000L
        const val RECORD_TIMEOUT_MS = 15000L
        const val RECORD_SAMPLES = 20
        const val MIN_QUALITY = 0.6f
    }
}
