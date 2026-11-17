package com.airtouch.control

import com.airtouch.gesture.Gesture
import com.airtouch.gesture.GestureRecognitionEngine
import com.airtouch.gesture.SwipeDetector
import com.airtouch.model.Hand
import com.airtouch.model.LM
import com.airtouch.mouse.MouseEvent
import com.airtouch.mouse.VirtualMouse

/**
 * Junta reconhecimento de gestos + mouse virtual + varredura + despachante de ações.
 * É usado tanto pelo app (ViewModel) quanto pelo serviço de controle global.
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
    )

    private val gestures = GestureRecognitionEngine(framesToConfirm = 3, framesToRelease = 6)
    private val dispatcher = GestureActionDispatcher()
    private val swipe = SwipeDetector()
    val mouse = VirtualMouse(mouseConfig)

    private var last = Gesture.NONE
    private var wasLocked = false

    fun process(hand: Hand?, imageWidth: Int, imageHeight: Int, nowMs: Long): Output {
        val s = gestures.process(hand, imageWidth, imageHeight)
        val actions = ArrayList<AirAction>(2)
        val events = ArrayList<MouseEvent>(2)

        val changed = s.stable != last
        if (changed) {
            last = s.stable
            dispatcher.onStableGesture(s.stable)?.let { actions += it }
        }

        val locked = dispatcher.locked
        if (locked) {
            if (!wasLocked) events += mouse.reset()
            swipe.reset()
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
        }
        wasLocked = locked

        return Output(
            raw = s.raw,
            stable = s.stable,
            confidence = s.confidence,
            stableChanged = changed,
            mouseEvents = events,
            cursor = if (locked) null else mouse.cursor,
            actions = actions,
            locked = locked,
            palmShown = !locked && s.stable == Gesture.OPEN_PALM,
        )
    }

    /** Zera tudo e devolve eventos de encerramento (soltar arraste/rolagem em andamento). */
    fun reset(): List<MouseEvent> {
        val events = mouse.reset()
        gestures.reset()
        dispatcher.reset()
        swipe.reset()
        last = Gesture.NONE
        wasLocked = false
        return events
    }
}
