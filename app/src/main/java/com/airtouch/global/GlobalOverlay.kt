package com.airtouch.global

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import com.airtouch.mouse.VirtualMouse

/**
 * Elementos desenhados SOBRE outros apps (janelas de acessibilidade, que não exigem
 * a permissão "sobrepor a outros apps"):
 *  - cursor virtual (não recebe toques);
 *  - selo "CÂMERA ATIVA" (privacidade);
 *  - barra de ações (aparece com a mão aberta): Voltar, Início, Recentes, volume, mídia, parar.
 *
 * Use sempre na thread principal.
 */
class GlobalOverlay(
    private val ctx: Context,
    private val wm: WindowManager,
    private val onBarAction: (BarAction) -> Unit,
) {
    enum class BarAction { BACK, HOME, RECENTS, VOL_DOWN, VOL_UP, PLAY_PAUSE, STOP }

    private val density = ctx.resources.displayMetrics.density
    private fun dp(v: Int): Int = (v * density).toInt()

    private val cyan = Color.rgb(0, 229, 255)
    private val magenta = Color.rgb(255, 45, 149)

    private inner class CursorView(context: Context) : View(context) {
        var cursor: VirtualMouse.Cursor? = null

        private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
        private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = dp(2).toFloat()
            strokeCap = Paint.Cap.ROUND
        }

        override fun onDraw(canvas: Canvas) {
            val c = cursor ?: return
            val cx = c.x * width
            val cy = c.y * height
            when (c.mode) {
                VirtualMouse.Mode.MOVE -> {
                    fill.color = Color.argb(64, 0, 229, 255)
                    canvas.drawCircle(cx, cy, dp(14).toFloat(), fill)
                    line.color = cyan
                    canvas.drawCircle(cx, cy, dp(14).toFloat(), line)
                    fill.color = Color.WHITE
                    canvas.drawCircle(cx, cy, dp(3).toFloat(), fill)
                }

                VirtualMouse.Mode.PRESS -> {
                    fill.color = Color.argb(128, 255, 45, 149)
                    canvas.drawCircle(cx, cy, dp(9).toFloat(), fill)
                    line.color = magenta
                    canvas.drawCircle(cx, cy, dp(9).toFloat(), line)
                }

                VirtualMouse.Mode.DRAG -> {
                    fill.color = Color.argb(90, 255, 45, 149)
                    canvas.drawCircle(cx, cy, dp(18).toFloat(), fill)
                    line.color = magenta
                    canvas.drawCircle(cx, cy, dp(18).toFloat(), line)
                    fill.color = Color.WHITE
                    canvas.drawCircle(cx, cy, dp(4).toFloat(), fill)
                }

                VirtualMouse.Mode.SCROLL -> {
                    line.color = cyan
                    val a = dp(18).toFloat()
                    val h = dp(7).toFloat()
                    canvas.drawLine(cx, cy - a, cx, cy + a, line)
                    canvas.drawLine(cx, cy - a, cx - h, cy - a + h, line)
                    canvas.drawLine(cx, cy - a, cx + h, cy - a + h, line)
                    canvas.drawLine(cx, cy + a, cx - h, cy + a - h, line)
                    canvas.drawLine(cx, cy + a, cx + h, cy + a - h, line)
                }
            }
        }
    }

    private val cursorView = CursorView(ctx)

    private val badge = TextView(ctx).apply {
        text = "●  CÂMERA ATIVA"
        setTextColor(Color.WHITE)
        textSize = 12f
        setPadding(dp(12), dp(6), dp(12), dp(6))
        background = GradientDrawable().apply {
            cornerRadius = dp(20).toFloat()
            setColor(Color.argb(190, 20, 20, 20))
        }
    }

    private val bar = LinearLayout(ctx).apply {
        orientation = LinearLayout.HORIZONTAL
        setPadding(dp(10), dp(8), dp(10), dp(8))
        background = GradientDrawable().apply {
            cornerRadius = dp(22).toFloat()
            setColor(Color.argb(235, 10, 16, 30))
            setStroke(dp(1), cyan)
        }
        addButton(this, "◀ Voltar", BarAction.BACK)
        addButton(this, "⌂ Início", BarAction.HOME)
        addButton(this, "▢ Recentes", BarAction.RECENTS)
        addButton(this, "🔉", BarAction.VOL_DOWN)
        addButton(this, "🔊", BarAction.VOL_UP)
        addButton(this, "⏯", BarAction.PLAY_PAUSE)
        addButton(this, "■ Parar", BarAction.STOP)
    }

    private fun addButton(parent: LinearLayout, label: String, action: BarAction) {
        val b = TextView(ctx).apply {
            text = label
            setTextColor(Color.WHITE)
            textSize = 16f
            gravity = Gravity.CENTER
            setPadding(dp(14), dp(10), dp(14), dp(10))
            background = GradientDrawable().apply {
                cornerRadius = dp(14).toFloat()
                setColor(if (action == BarAction.STOP) Color.rgb(160, 30, 30) else Color.rgb(27, 42, 68))
            }
            setOnClickListener { onBarAction(action) }
        }
        val lp = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { marginStart = dp(4); marginEnd = dp(4) }
        parent.addView(b, lp)
    }

    private var shown = false
    private var barShown = false

    fun show() {
        if (shown) return
        shown = true
        try {
            wm.addView(cursorView, fullScreenParams())
            wm.addView(badge, badgeParams())
        } catch (e: Exception) {
            shown = false
        }
    }

    fun hide() {
        setBarVisible(false)
        if (!shown) return
        shown = false
        try { wm.removeViewImmediate(cursorView) } catch (_: Exception) {}
        try { wm.removeViewImmediate(badge) } catch (_: Exception) {}
    }

    fun setCursor(cursor: VirtualMouse.Cursor?) {
        if (cursor == cursorView.cursor) return
        cursorView.cursor = cursor
        cursorView.invalidate()
    }

    fun setBarVisible(visible: Boolean) {
        if (visible == barShown || (visible && !shown)) return
        barShown = visible
        try {
            if (visible) wm.addView(bar, barParams()) else wm.removeViewImmediate(bar)
        } catch (e: Exception) {
            barShown = false
        }
    }

    private fun baseFlags(touchable: Boolean): Int {
        var f = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
        if (!touchable) f = f or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
        return f
    }

    private fun fullScreenParams() = WindowManager.LayoutParams(
        WindowManager.LayoutParams.MATCH_PARENT,
        WindowManager.LayoutParams.MATCH_PARENT,
        WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
        baseFlags(touchable = false),
        PixelFormat.TRANSLUCENT,
    ).apply { gravity = Gravity.TOP or Gravity.START }

    private fun badgeParams() = WindowManager.LayoutParams(
        WindowManager.LayoutParams.WRAP_CONTENT,
        WindowManager.LayoutParams.WRAP_CONTENT,
        WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
        baseFlags(touchable = false),
        PixelFormat.TRANSLUCENT,
    ).apply {
        gravity = Gravity.TOP or Gravity.END
        x = dp(16)
        y = dp(16)
    }

    private fun barParams() = WindowManager.LayoutParams(
        WindowManager.LayoutParams.WRAP_CONTENT,
        WindowManager.LayoutParams.WRAP_CONTENT,
        WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
        baseFlags(touchable = true),
        PixelFormat.TRANSLUCENT,
    ).apply {
        gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
        y = dp(24)
    }
}
