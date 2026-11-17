package com.airtouch.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.airtouch.AirTouchViewModel
import com.airtouch.games.GameInput
import com.airtouch.ui.theme.Alert
import com.airtouch.ui.theme.Cyan
import com.airtouch.ui.theme.Magenta
import com.airtouch.ui.theme.TextDim
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.random.Random

private enum class GameKind(val title: String) {
    BUBBLES("Bolhas"),
    PADDLE("Raquete"),
    RUNNER("Corredor"),
    DPAD("D-pad"),
}

private interface Sim {
    val hud: String
    fun update(dt: Float, input: GameInput, w: Float, h: Float)
    fun draw(s: DrawScope)
}

// ------------------------------------------------------------------ Bolhas
private class BubblesSim : Sim {
    private class B(val x: Float, val y: Float, val r: Float, var age: Float = 0f)

    private val list = ArrayList<B>()
    private var left = 45f
    private var score = 0
    private var spawn = 0f
    private var wasAction = false
    private var w = 1f
    private var h = 1f
    private var px = -1f
    private var py = -1f

    override val hud: String
        get() = if (left <= 0f) "Fim! $score bolhas · pinça para jogar de novo"
        else "Passe o dedo sobre as bolhas · $score pontos · ${left.toInt()} s"

    override fun update(dt: Float, input: GameInput, w: Float, h: Float) {
        this.w = w; this.h = h
        px = input.x?.let { it * w } ?: -1f
        py = input.y?.let { it * h } ?: -1f
        if (left <= 0f) {
            if (input.action && !wasAction) { left = 45f; score = 0; list.clear() }
            wasAction = input.action
            return
        }
        left -= dt
        spawn -= dt
        if (spawn <= 0f) {
            val r = minOf(w, h) * (0.06f + Random.nextFloat() * 0.05f)
            list += B(r + Random.nextFloat() * (w - 2 * r), r + Random.nextFloat() * (h - 2 * r), r)
            spawn = 0.6f
        }
        val it = list.iterator()
        while (it.hasNext()) {
            val b = it.next()
            b.age += dt
            if (b.age > 3.2f) { it.remove(); continue }
            if (px >= 0f && hypot(px - b.x, py - b.y) <= b.r) { it.remove(); score++ }
        }
        wasAction = input.action
    }

    override fun draw(s: DrawScope) {
        for (b in list) {
            val a = 1f - (b.age / 3.2f) * 0.6f
            s.drawCircle(Cyan.copy(alpha = 0.25f * a), b.r, Offset(b.x, b.y))
            s.drawCircle(Cyan.copy(alpha = a), b.r, Offset(b.x, b.y), style = Stroke(5f))
        }
    }
}

// ------------------------------------------------------------------ Raquete
private class PaddleSim : Sim {
    private var bx = 0f
    private var by = 0f
    private var vx = 0f
    private var vy = 0f
    private var score = 0
    private var over = false
    private var started = false
    private var padX = 0f
    private var w = 1f
    private var h = 1f
    private var wasAction = false

    override val hud: String
        get() = if (over) "Fim! $score rebatidas · pinça para jogar de novo" else "Mova a mão para os lados · $score rebatidas"

    private fun reset(w: Float, h: Float) {
        bx = w / 2; by = h * 0.35f
        val sp = h * 0.55f
        vx = sp * 0.5f; vy = sp
        score = 0; over = false; started = true
    }

    override fun update(dt: Float, input: GameInput, w: Float, h: Float) {
        this.w = w; this.h = h
        if (!started) reset(w, h)
        input.x?.let { padX = it * w }
        val pw = w * 0.22f
        val py = h * 0.92f
        if (over) {
            if (input.action && !wasAction) reset(w, h)
            wasAction = input.action
            return
        }
        bx += vx * dt; by += vy * dt
        val r = minOf(w, h) * 0.03f
        if (bx < r) { bx = r; vx = abs(vx) }
        if (bx > w - r) { bx = w - r; vx = -abs(vx) }
        if (by < r) { by = r; vy = abs(vy) }
        if (vy > 0 && by + r >= py && by < py + 30f && abs(bx - padX) <= pw / 2 + r) {
            vy = -abs(vy) * 1.04f
            vx += (bx - padX) / (pw / 2) * h * 0.2f
            vx = vx.coerceIn(-h * 1.1f, h * 1.1f)
            score++
        }
        if (by > h + r) over = true
        wasAction = input.action
    }

    override fun draw(s: DrawScope) {
        val pw = w * 0.22f
        s.drawRect(Cyan, Offset(padX - pw / 2, h * 0.92f), Size(pw, 14f))
        s.drawCircle(Color.White, minOf(w, h) * 0.03f, Offset(bx, by))
    }
}

// ------------------------------------------------------------------ Corredor
private class RunnerSim : Sim {
    private class O(val lane: Int, var y: Float)

    private val obs = ArrayList<O>()
    private var lane = 1
    private var score = 0f
    private var speed = 0.35f
    private var spawn = 0f
    private var over = false
    private var wasAction = false
    private var w = 1f
    private var h = 1f

    override val hud: String
        get() = if (over) "Fim! ${score.toInt()} pontos · pinça para jogar de novo"
        else "Mova a mão para a esquerda/direita para trocar de faixa · ${score.toInt()} pontos"

    override fun update(dt: Float, input: GameInput, w: Float, h: Float) {
        this.w = w; this.h = h
        if (over) {
            if (input.action && !wasAction) { obs.clear(); score = 0f; speed = 0.35f; over = false }
            wasAction = input.action
            return
        }
        if (input.x != null) lane = 1 + input.dirX
        score += dt * 10f
        speed = 0.35f + score / 400f
        spawn -= dt
        if (spawn <= 0f) { obs += O(Random.nextInt(3), -0.1f); spawn = (0.9f - speed * 0.4f).coerceAtLeast(0.35f) }
        val it = obs.iterator()
        while (it.hasNext()) {
            val o = it.next()
            o.y += speed * dt
            if (o.y > 1.1f) { it.remove(); continue }
            if (o.lane == lane && o.y in 0.80f..0.92f) over = true
        }
        wasAction = input.action
    }

    override fun draw(s: DrawScope) {
        val lw = w / 3f
        for (i in 1..2) s.drawLine(Color.White.copy(alpha = 0.15f), Offset(lw * i, 0f), Offset(lw * i, h), 3f)
        for (o in obs) s.drawRect(Alert, Offset(lw * o.lane + lw * 0.2f, o.y * h), Size(lw * 0.6f, h * 0.07f))
        s.drawRect(Cyan, Offset(lw * lane + lw * 0.25f, h * 0.84f), Size(lw * 0.5f, h * 0.08f))
    }
}

// ------------------------------------------------------------------ D-pad
private class DpadSim : Sim {
    private var inp = GameInput(null, null, 0, 0, false)
    private var w = 1f
    private var h = 1f
    override val hud: String
        get() = "Direção: ${dirText(inp)} · botão (pinça): ${if (inp.action) "APERTADO" else "solto"}"

    private fun dirText(i: GameInput): String {
        if (i.x == null) return "sem mão"
        val v = when (i.dirY) { -1 -> "cima"; 1 -> "baixo"; else -> "" }
        val hz = when (i.dirX) { -1 -> "esquerda"; 1 -> "direita"; else -> "" }
        return listOf(v, hz).filter { it.isNotEmpty() }.joinToString(" + ").ifEmpty { "centro" }
    }

    override fun update(dt: Float, input: GameInput, w: Float, h: Float) {
        inp = input; this.w = w; this.h = h
    }

    override fun draw(s: DrawScope) {
        val c = Offset(w / 2, h / 2)
        val u = minOf(w, h) * 0.16f
        fun arrow(dx: Int, dy: Int) {
            val on = inp.dirX == dx && inp.dirY == dy
            s.drawCircle(if (on) Cyan else Color.White.copy(alpha = 0.12f), u * 0.7f, Offset(c.x + dx * u * 2, c.y + dy * u * 2))
        }
        arrow(0, -1); arrow(0, 1); arrow(-1, 0); arrow(1, 0)
        s.drawCircle(Color.White.copy(alpha = 0.12f), u * 0.5f, c)
        s.drawCircle(if (inp.action) Magenta else Color.White.copy(alpha = 0.12f), u * 0.8f, Offset(c.x + u * 5f, c.y))
    }
}

@Composable
fun GamesScreen(
    viewModel: AirTouchViewModel,
    rootSize: IntSize,
    modifier: Modifier = Modifier,
) {
    var kind by remember { mutableStateOf(GameKind.BUBBLES) }
    val sim = remember(kind) {
        when (kind) {
            GameKind.BUBBLES -> BubblesSim()
            GameKind.PADDLE -> PaddleSim()
            GameKind.RUNNER -> RunnerSim()
            GameKind.DPAD -> DpadSim()
        }
    }
    var boxSize by remember { mutableStateOf(IntSize.Zero) }
    var tick by remember { mutableStateOf(0) }
    val input by viewModel.gameInput.collectAsStateWithLifecycle()

    LaunchedEffect(sim) {
        var last = 0L
        while (true) {
            androidx.compose.runtime.withFrameNanos { t ->
                val dt = if (last == 0L) 0f else ((t - last) / 1e9f).coerceAtMost(0.05f)
                last = t
                if (boxSize.width > 0) sim.update(dt, viewModel.gameInput.value, boxSize.width.toFloat(), boxSize.height.toFloat())
                tick++
            }
        }
    }

    val hud = run { tick; sim.hud }
    val shape = RoundedCornerShape(20.dp)

    Column(modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            GameKind.values().forEach { k -> Chip(k.title, kind == k) { kind = k } }
        }
        Text(hud, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
        Box(
            Modifier
                .fillMaxWidth()
                .weight(1f)
                .clip(shape)
                .background(Color(0xFF0B1220))
                .border(1.dp, Cyan.copy(alpha = 0.3f), shape)
                .onGloballyPositioned { c ->
                    boxSize = c.size
                    val r = c.boundsInRoot()
                    viewModel.setArea(
                        r.left, r.top, r.width, r.height,
                        rootSize.width.toFloat(), rootSize.height.toFloat(),
                    )
                }
        ) {
            Canvas(Modifier.fillMaxSize()) {
                @Suppress("UNUSED_EXPRESSION") tick
                sim.draw(this)
                val ix = input.x
                val iy = input.y
                if (ix != null && iy != null) {
                    val c = Offset(ix * size.width, iy * size.height)
                    val col = if (input.action) Magenta else Color.White
                    drawCircle(col.copy(alpha = 0.3f), 22f, c)
                    drawCircle(col, 22f, c, style = Stroke(4f))
                }
            }
            if (input.x == null) {
                Text(
                    "Mostre a mão para a câmera (e ligue o controle)",
                    color = TextDim,
                    fontSize = 13.sp,
                    modifier = Modifier.align(Alignment.TopStart).padding(14.dp),
                )
            }
        }
    }
}
