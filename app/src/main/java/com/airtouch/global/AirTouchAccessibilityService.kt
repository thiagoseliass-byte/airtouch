package com.airtouch.global

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Context
import android.content.Intent
import android.graphics.Path
import android.graphics.Point
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.KeyEvent
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import com.airtouch.control.AirAction
import com.airtouch.control.ControlPipeline
import com.airtouch.mouse.MouseEvent
import com.airtouch.settings.SettingsManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Executa em OUTROS apps o que os gestos pedem: toques, arrastar, rolar, voltar, início.
 * O Android só permite isso se o usuário ligar este serviço em
 * Configurações > Acessibilidade. Ele recebe os resultados do [AirTouchService].
 */
class AirTouchAccessibilityService : AccessibilityService() {

    companion object {
        @Volatile var instance: AirTouchAccessibilityService? = null
            private set

        private val _connected = MutableStateFlow(false)
        val connected: StateFlow<Boolean> = _connected.asStateFlow()

        private const val BAR_VISIBLE_MS = 8000L
        private const val DRAG_SEGMENT_MS = 40L
        private const val FLICK_TRIGGER = 0.10f
    }

    private val main = Handler(Looper.getMainLooper())
    private var overlay: GlobalOverlay? = null
    private lateinit var settings: SettingsManager

    private val drag = DragSession()

    private var barUntil = 0L
    private var scrollAcc = 0f
    private var scrollX = 0.5f
    private var flickBusy = false

    // ------------------------------------------------------------ ciclo de vida

    override fun onServiceConnected() {
        super.onServiceConnected()
        settings = SettingsManager(this)
        instance = this
        _connected.value = true
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Não lemos o conteúdo das telas.
    }

    override fun onInterrupt() {
        drag.cancel()
    }

    override fun onUnbind(intent: Intent?): Boolean {
        shutdown()
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        shutdown()
        super.onDestroy()
    }

    private fun shutdown() {
        instance = null
        _connected.value = false
        deactivate()
        AirTouchService.stop(this)
    }

    /** Mostra o cursor e o selo de câmera ativa (chamado pelo serviço de câmera). */
    fun activate() {
        main.post {
            if (overlay == null) {
                overlay = GlobalOverlay(
                    this,
                    getSystemService(Context.WINDOW_SERVICE) as WindowManager,
                    ::onBarAction,
                )
            }
            overlay?.show()
        }
    }

    fun deactivate() {
        main.post {
            drag.cancel()
            overlay?.hide()
            overlay = null
            barUntil = 0L
            scrollAcc = 0f
        }
    }

    // ------------------------------------------------------------ entrada

    /** Recebe o resultado de cada quadro (qualquer thread). */
    fun handleOutput(out: ControlPipeline.Output) {
        main.post { actuate(out) }
    }

    private fun actuate(out: ControlPipeline.Output) {
        val ov = overlay ?: return
        val now = SystemClock.uptimeMillis()

        ov.setCursor(out.cursor)
        out.mouseEvents.forEach { handleMouse(it) }
        out.actions.forEach { handleAction(it) }

        // Barra de ações: abre com a mão aberta e fica um tempo para dar para clicar.
        if (out.palmShown) barUntil = now + BAR_VISIBLE_MS
        val cursorY = out.cursor?.y
        if (cursorY != null && cursorY > 0.78f && now < barUntil) barUntil = max(barUntil, now + 3000)
        ov.setBarVisible(now < barUntil)
    }

    private fun handleMouse(e: MouseEvent) {
        val (w, h) = screen()
        when (e) {
            is MouseEvent.Click -> tap(e.x * w, e.y * h)
            is MouseEvent.DragStart -> drag.start(e.x * w, e.y * h)
            is MouseEvent.DragMove -> drag.move(e.x * w, e.y * h)
            is MouseEvent.DragEnd -> drag.end(e.x * w, e.y * h)
            is MouseEvent.ScrollStart -> {
                scrollX = e.x
                scrollAcc = 0f
            }

            is MouseEvent.ScrollMove -> {
                scrollAcc += e.dy
                if (abs(scrollAcc) >= FLICK_TRIGGER && !flickBusy) {
                    val acc = scrollAcc
                    scrollAcc = 0f
                    // Mão desceu (dy > 0) => dedo desce => conteúdo desce.
                    val dist = min(0.45f, max(0.18f, abs(acc) * 1.8f))
                    flick(scrollX, dist, fingerDown = acc > 0f)
                }
            }

            MouseEvent.ScrollEnd -> scrollAcc = 0f
        }
    }

    private fun handleAction(a: AirAction) {
        val invert = settings.invertScroll
        when (a) {
            AirAction.Cancel -> performGlobalAction(GLOBAL_ACTION_BACK)
            AirAction.NextSlide -> swipeNormalized(0.8f, 0.5f, 0.2f, 0.5f, 220)
            AirAction.PrevSlide -> swipeNormalized(0.2f, 0.5f, 0.8f, 0.5f, 220)
            AirAction.ScrollUp -> flick(0.5f, 0.4f, fingerDown = invert)
            AirAction.ScrollDown -> flick(0.5f, 0.4f, fingerDown = !invert)
            AirAction.Confirm -> Unit
            is AirAction.Pause -> Unit
        }
    }

    private fun onBarAction(a: GlobalOverlay.BarAction) {
        val audio = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        when (a) {
            GlobalOverlay.BarAction.BACK -> performGlobalAction(GLOBAL_ACTION_BACK)
            GlobalOverlay.BarAction.HOME -> performGlobalAction(GLOBAL_ACTION_HOME)
            GlobalOverlay.BarAction.RECENTS -> performGlobalAction(GLOBAL_ACTION_RECENTS)
            GlobalOverlay.BarAction.VOL_DOWN ->
                audio.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_LOWER, AudioManager.FLAG_SHOW_UI)

            GlobalOverlay.BarAction.VOL_UP ->
                audio.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_RAISE, AudioManager.FLAG_SHOW_UI)

            GlobalOverlay.BarAction.PLAY_PAUSE -> {
                audio.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE))
                audio.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE))
            }

            GlobalOverlay.BarAction.STOP -> AirTouchService.stop(this)
        }
        barUntil = SystemClock.uptimeMillis() + 3000
    }

    // ------------------------------------------------------------ gestos de toque

    @Suppress("DEPRECATION")
    private fun screen(): Pair<Float, Float> {
        val wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        return if (Build.VERSION.SDK_INT >= 30) {
            val b = wm.currentWindowMetrics.bounds
            Pair(b.width().toFloat(), b.height().toFloat())
        } else {
            val p = Point()
            wm.defaultDisplay.getRealSize(p)
            Pair(p.x.toFloat(), p.y.toFloat())
        }
    }

    private fun clampX(x: Float): Float = x.coerceIn(1f, screen().first - 1f)
    private fun clampY(y: Float): Float = y.coerceIn(1f, screen().second - 1f)

    private fun tap(x: Float, y: Float) {
        val path = Path().apply { moveTo(clampX(x), clampY(y)) }
        val g = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, 40))
            .build()
        dispatchGesture(g, null, null)
    }

    private fun swipeNormalized(x0: Float, y0: Float, x1: Float, y1: Float, durationMs: Long) {
        val (w, h) = screen()
        val path = Path().apply {
            moveTo(clampX(x0 * w), clampY(y0 * h))
            lineTo(clampX(x1 * w), clampY(y1 * h))
        }
        val g = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, durationMs))
            .build()
        dispatchGesture(g, null, null)
    }

    /** Passada vertical do dedo, centrada na tela. [dist] em alturas de tela. */
    private fun flick(nx: Float, dist: Float, fingerDown: Boolean) {
        if (flickBusy) return
        val (w, h) = screen()
        val d = dist.coerceIn(0.1f, 0.7f)
        val y0 = if (fingerDown) 0.5f - d / 2f else 0.5f + d / 2f
        val y1 = if (fingerDown) 0.5f + d / 2f else 0.5f - d / 2f
        val path = Path().apply {
            moveTo(clampX(nx * w), clampY(y0 * h))
            lineTo(clampX(nx * w), clampY(y1 * h))
        }
        val g = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, 180))
            .build()
        flickBusy = true
        val ok = dispatchGesture(g, object : AccessibilityService.GestureResultCallback() {
            override fun onCompleted(gestureDescription: GestureDescription?) {
                flickBusy = false
            }

            override fun onCancelled(gestureDescription: GestureDescription?) {
                flickBusy = false
            }
        }, main)
        if (!ok) flickBusy = false
    }

    /**
     * Arrastar contínuo: o Android exige o caminho pronto, então o arraste é enviado em
     * pequenos trechos encadeados (continueStroke), sempre seguindo o último ponto da mão.
     */
    private inner class DragSession {
        private var active = false
        private var inFlight = false
        private var endRequested = false
        private var stroke: GestureDescription.StrokeDescription? = null
        private var lastX = 0f
        private var lastY = 0f
        private var targetX = 0f
        private var targetY = 0f

        fun start(x: Float, y: Float) {
            if (active) cancel()
            active = true
            endRequested = false
            lastX = clampX(x); lastY = clampY(y)
            targetX = lastX; targetY = lastY
            val path = Path().apply { moveTo(lastX, lastY) }
            val s = GestureDescription.StrokeDescription(path, 0, DRAG_SEGMENT_MS, true)
            stroke = s
            send(s)
        }

        fun move(x: Float, y: Float) {
            if (!active) return
            targetX = clampX(x); targetY = clampY(y)
            pump()
        }

        fun end(x: Float, y: Float) {
            if (!active) return
            targetX = clampX(x); targetY = clampY(y)
            endRequested = true
            pump()
        }

        fun cancel() {
            if (active && !inFlight) {
                // Solta o dedo no último ponto para não deixar um toque "preso".
                val prev = stroke
                if (prev != null) {
                    val path = Path().apply { moveTo(lastX, lastY) }
                    try {
                        val s = prev.continueStroke(path, 0, 1, false)
                        dispatchGesture(GestureDescription.Builder().addStroke(s).build(), null, null)
                    } catch (_: Exception) {
                    }
                }
            }
            active = false
            inFlight = false
            endRequested = false
            stroke = null
        }

        private fun pump() {
            if (inFlight || !active) return
            val prev = stroke ?: return
            val moved = abs(targetX - lastX) + abs(targetY - lastY) > 1f
            if (!endRequested && !moved) return

            val path = Path().apply {
                moveTo(lastX, lastY)
                if (moved) lineTo(targetX, targetY)
            }
            val s = try {
                prev.continueStroke(path, 0, DRAG_SEGMENT_MS, !endRequested)
            } catch (e: Exception) {
                active = false
                stroke = null
                return
            }
            lastX = targetX
            lastY = targetY
            stroke = s
            if (endRequested) active = false
            send(s)
        }

        private fun send(s: GestureDescription.StrokeDescription) {
            inFlight = true
            val ok = dispatchGesture(
                GestureDescription.Builder().addStroke(s).build(),
                object : AccessibilityService.GestureResultCallback() {
                    override fun onCompleted(gestureDescription: GestureDescription?) {
                        inFlight = false
                        pump()
                    }

                    override fun onCancelled(gestureDescription: GestureDescription?) {
                        inFlight = false
                        active = false
                        stroke = null
                    }
                },
                main,
            )
            if (!ok) {
                inFlight = false
                active = false
                stroke = null
            }
        }
    }
}
