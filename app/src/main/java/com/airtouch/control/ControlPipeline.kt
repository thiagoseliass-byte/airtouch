package com.airtouch.control

import com.airtouch.gesture.CircleDetector
import com.airtouch.gesture.Gesture
import com.airtouch.gesture.GestureRecognitionEngine
import com.airtouch.gesture.SwipeDetector
import com.airtouch.model.Hand
import com.airtouch.model.LM
import com.airtouch.mouse.MouseEvent
import com.airtouch.mouse.VirtualMouse

/**
 * Junta reconhecimento de gestos + mouse virtual + varredura + zoom de duas mãos +
 * círculo (menu) + gestos personalizados + despachante de ações.
 * Usado tanto pelo app (ViewModel) quanto pelo serviço de controle global.
 *
 * Kotlin puro (testável na JVM). Chame [process] uma vez por quadro, sempre da mesma thread.
 */
class ControlPipeline(mouseConfig: VirtualMouse.Config = VirtualMouse.Config()) {

    data class Output(
        val raw: Gesture,
        val stable: Gesture,
        val confidence: Float,
        val stableChanged: Boolean,
        val mouseEvents: List<MouseEvent>,
        val cursor: VirtualMouse.Cursor?,
        val actions: List<AirAction>,
        val locked: Boolean,
        /** Mão aberta mostrada e comandos liberados: abre o painel de controle. */
        val palmShown: Boolean,
        /** Fator de zoom deste quadro (1 = nenhum). */
        val zoomFactor: Float = 1f,
        val zoomActive: Boolean = false,
        /** Ponta do indicador mapeada para a tela (0..1), se houver mão. */
        val pointer: Pair<Float, Float>? = null,
        val pinchDist: Float = 0f,
        val indexOpen: Float = 0f,
        val customLive: CustomGestureEngine.Match? = null,
        val customFired: String? = null,
    )

    private val gestures = GestureRecognitionEngine(framesToConfirm = 3, framesToRelease = 6)
    val dispatcher = GestureActionDispatcher()
    val custom = CustomGestureEngine()
    val zoom = TwoHandZoom()
    val circle = CircleDetector()
    private val swipe = SwipeDetector()
    val mouse = VirtualMouse(mouseConfig)

    private var last = Gesture.NONE
    private var wasLocked = false
    private var wasZooming = false

    fun process(hand: Hand?, imageWidth: Int, imageHeight: Int, nowMs: Long): Output =
        process(if (hand == null) emptyList() else listOf(hand), imageWidth, imageHeight, nowMs)

    fun process(hands: List<Hand>, imageWidth: Int, imageHeight: Int, nowMs: Long): Output {
        val hand = hands.maxByOrNull { it.score }
        val s = gestures.process(hand, imageWidth, imageHeight)
        val actions = ArrayList<AirAction>(2)
        val events = ArrayList<MouseEvent>(2)

        val factor = zoom.update(hands, imageWidth, imageHeight)
        val zooming = zoom.active

        val changed = s.stable != last
        if (changed) {
            last = s.stable
            dispatcher.onStableGesture(s.stable)?.let { actions += it }
        }

        val locked = dispatcher.locked
        var fired: String? = null

        if (locked || zooming) {
            if (!wasLocked || (zooming && !wasZooming)) events += mouse.reset()
            swipe.reset()
            circle.reset()
            custom.update(null, imageWidth, imageHeight)
        } else {
            events += mouse.update(s.stable, hand, nowMs)

            if (hand != null && s.stable == Gesture.OPEN_PALM) {
                val wrist = hand.landmarks[LM.WRIST]
                val mid = hand.landmarks[LM.MIDDLE_MCP]
                val cx = (wrist.x + mid.x) / 2f
                val cy = (wrist.y + mid.y) / 2f
                swipe.update(cx, cy, nowMs)?.let { dir ->
                    dispatcher.onSwipe(dir)?.let { actions += it }
                }
            } else {
                swipe.reset()
            }

            if (hand != null && s.stable == Gesture.POINTING) {
                if (circle.update(mouse.lastX, mouse.lastY, nowMs)) actions += AirAction.OpenMenu
            } else {
                circle.reset()
            }

            fired = custom.update(hand, imageWidth, imageHeight)
            fired?.let { id -> dispatcher.onCustom(id)?.let { actions += it } }
        }
        wasLocked = locked
        wasZooming = zooming

        val pointer = hand?.let {
            val tip = it.landmarks[LM.INDEX_TIP]
            mouse.mapToScreen(tip.x, tip.y)
        }

        return Output(
            raw = s.raw,
            stable = s.stable,
            confidence = s.confidence,
            stableChanged = changed,
            mouseEvents = events,
            cursor = if (locked || zooming) null else mouse.cursor,
            actions = actions,
            locked = locked,
            palmShown = !locked && !zooming && s.stable == Gesture.OPEN_PALM,
            zoomFactor = if (locked) 1f else factor,
            zoomActive = zooming && !locked,
            pointer = pointer,
            pinchDist = s.pinchDist,
            indexOpen = s.indexOpen,
            customLive = custom.live,
            customFired = fired,
        )
    }

    /** Zera tudo e devolve eventos de encerramento (soltar arraste/rolagem em andamento). */
    fun reset(): List<MouseEvent> {
        val events = mouse.reset()
        gestures.reset()
        dispatcher.reset()
        custom.reset()
        zoom.reset()
        circle.reset()
        swipe.reset()
        last = Gesture.NONE
        wasLocked = false
        wasZooming = false
        return events
    }
}
