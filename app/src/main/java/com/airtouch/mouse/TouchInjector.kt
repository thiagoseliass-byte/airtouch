package com.airtouch.mouse

import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.InputDevice
import android.view.MotionEvent
import android.view.View

/**
 * Injeta toques DENTRO do próprio app (na view raiz do conteúdo), sem permissões
 * especiais. Para controlar OUTROS apps será usado o AccessibilityService (Fase 3).
 *
 * Pode ser chamado de qualquer thread: tudo é enfileirado na thread principal.
 */
class TouchInjector(private val view: View) {

    private val handler = Handler(Looper.getMainLooper())

    // Estado usado apenas na thread principal.
    private var downTime = 0L
    private var scrolling = false
    private var scrollX = 0f
    private var scrollY = 0f

    private fun w() = view.width.toFloat()
    private fun h() = view.height.toFloat()

    fun handle(e: MouseEvent) {
        when (e) {
            is MouseEvent.Click -> tap(e.x, e.y)
            is MouseEvent.DragStart -> handler.post { send(MotionEvent.ACTION_DOWN, e.x * w(), e.y * h()) }
            is MouseEvent.DragMove -> handler.post { send(MotionEvent.ACTION_MOVE, e.x * w(), e.y * h()) }
            is MouseEvent.DragEnd -> handler.post { send(MotionEvent.ACTION_UP, e.x * w(), e.y * h()) }
            is MouseEvent.ScrollStart -> handler.post {
                scrollX = e.x * w()
                scrollY = e.y * h()
                scrolling = true
                send(MotionEvent.ACTION_DOWN, scrollX, scrollY)
            }

            is MouseEvent.ScrollMove -> handler.post {
                if (scrolling) scrollBy(e.dy * h() * SCROLL_GAIN)
            }

            MouseEvent.ScrollEnd -> handler.post {
                if (scrolling) {
                    send(MotionEvent.ACTION_UP, scrollX, scrollY)
                    scrolling = false
                }
            }
        }
    }

    /** Arrasto vertical curto (rolagem "de um passo"), com x/y normalizados. */
    fun swipeVertical(nx: Float, fromNy: Float, toNy: Float) {
        val steps = 8
        handler.post { send(MotionEvent.ACTION_DOWN, nx * w(), fromNy * h()) }
        for (i in 1..steps) {
            val ny = fromNy + (toNy - fromNy) * i / steps
            handler.postDelayed({ send(MotionEvent.ACTION_MOVE, nx * w(), ny * h()) }, i * STEP_MS)
        }
        handler.postDelayed(
            { send(MotionEvent.ACTION_UP, nx * w(), toNy * h()) },
            (steps + 1) * STEP_MS
        )
    }

    fun release() {
        handler.removeCallbacksAndMessages(null)
        if (scrolling) {
            send(MotionEvent.ACTION_CANCEL, scrollX, scrollY)
            scrolling = false
        }
    }

    private fun tap(nx: Float, ny: Float) {
        handler.post {
            val x = nx * w()
            val y = ny * h()
            send(MotionEvent.ACTION_DOWN, x, y)
            handler.postDelayed({ send(MotionEvent.ACTION_UP, x, y) }, TAP_MS)
        }
    }

    private fun scrollBy(dyPx: Float) {
        var ny = scrollY + dyPx
        val lo = h() * 0.08f
        val hi = h() * 0.92f
        if (ny < lo || ny > hi) {
            // Chegou na borda: cancela (sem inércia) e recomeça do centro.
            send(MotionEvent.ACTION_CANCEL, scrollX, scrollY)
            scrollY = h() / 2f
            send(MotionEvent.ACTION_DOWN, scrollX, scrollY)
            ny = scrollY + dyPx
        }
        scrollY = ny
        send(MotionEvent.ACTION_MOVE, scrollX, scrollY)
    }

    private fun send(action: Int, px: Float, py: Float) {
        if (view.width == 0 || view.height == 0) return
        val now = SystemClock.uptimeMillis()
        if (action == MotionEvent.ACTION_DOWN) downTime = now

        val props = arrayOf(
            MotionEvent.PointerProperties().apply {
                id = 0
                toolType = MotionEvent.TOOL_TYPE_FINGER
            }
        )
        val coords = arrayOf(
            MotionEvent.PointerCoords().apply {
                this.x = px
                this.y = py
                pressure = 1f
                size = 1f
            }
        )
        val ev = MotionEvent.obtain(
            downTime, now, action, 1, props, coords,
            0, 0, 1f, 1f, 0, 0, InputDevice.SOURCE_TOUCHSCREEN, 0
        )
        view.dispatchTouchEvent(ev)
        ev.recycle()
    }

    private companion object {
        const val TAP_MS = 45L
        const val STEP_MS = 16L
        const val SCROLL_GAIN = 1.6f
    }
}
