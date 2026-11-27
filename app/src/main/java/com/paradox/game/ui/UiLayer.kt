package com.paradox.game.ui

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.view.MotionEvent
import com.paradox.game.app.GameApp
import com.paradox.game.app.Mode
import com.paradox.game.app.ChallengeModeDefs
import com.paradox.game.engine.Vec3i
import com.paradox.game.game.CampaignLevels
import com.paradox.game.game.LevelCodec
import com.paradox.game.game.LevelGenerator
import com.paradox.game.game.LevelSpec
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

enum class Screen { SPLASH, MENU, WORLDS, LEVELS, PLAY, PAUSE, COMPLETE, SETTINGS, ACHIEVEMENTS, CREATIVE_LIST, CREATIVE_EDIT, CHALLENGE, HELP }

class UiLayer(private val app: GameApp) {

    var w = 1f
    var h = 1f
    var screen = Screen.SPLASH
    var selectedWorld = 0
    var scrollY = 0f
    var screenTime = 0f
    var transition = 0f
    var pendingScreen: Screen? = null
    var completing = false
    var helpReturn = Screen.MENU
    var phaseUsed = false
    var flashUntil = 0L

    fun onWinFlash() { flashUntil = System.currentTimeMillis() + 520 }
    fun onPhaseShifted() { phaseUsed = true }

    private lateinit var canvas: Canvas
    private val hits = ArrayList<Hit>()
    private var downX = 0f
    private var downY = 0f
    private var downHit: Hit? = null
    private var downTime = 0L
    private var dragging = false
    private var draggingCam = false
    private var lastX = 0f
    private var lastY = 0f
    private var pinchDist = 0f

    class Hit(val rect: RectF, val action: () -> Unit, val draggable: Boolean = false)

    private val pFill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val pStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val pText = Paint(Paint.ANTI_ALIAS_FLAG)
    private val pTitle = Paint(Paint.ANTI_ALIAS_FLAG)
    private val pSub = Paint(Paint.ANTI_ALIAS_FLAG)
    private val pSmall = Paint(Paint.ANTI_ALIAS_FLAG)
    private val pGlow = Paint(Paint.ANTI_ALIAS_FLAG)

    val cyan = 0xFF35E8FF.toInt()
    val violet = 0xFFB26BFF.toInt()
    val white = 0xFFEEF6FF.toInt()
    val bg = 0xFF06080F.toInt()
    val panel = 0xCC0A1020.toInt()

    init {
        pTitle.typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
        pText.typeface = Typeface.MONOSPACE
        pSub.typeface = Typeface.MONOSPACE
        pSmall.typeface = Typeface.MONOSPACE
    }

    fun layout(w: Int, h: Int) {
        this.w = w.toFloat(); this.h = h.toFloat()
    }

    fun tap(action: () -> Unit) {
        audioClick()
        action()
    }

    private fun audioClick() {
        app.audio.play(com.paradox.game.audio.AudioEngine.CLICK)
    }

    fun goto(s: Screen) {
        if (s != screen) {
            pendingScreen = s
            transition = 0f
            screenTime = 0f
            scrollY = 0f
        }
    }

    fun update(dt: Float) {
        screenTime += dt
        transition += dt * 4f
        if (pendingScreen != null && transition >= 0.5f) {
            screen = pendingScreen!!
            pendingScreen = null
            transition = 0.5f
        } else {
            transition = min(1f, transition)
        }
        if (screen == Screen.SPLASH && screenTime > 2.4f) goto(Screen.MENU)
        app.elapsed += dt
    }

    private fun tr(): Float {
        val p = pendingScreen
        if (p != null) return (1f - transition * 2f).coerceIn(0f, 1f)
        return ((transition - 0.5f) * 2f).coerceIn(0f, 1f)
    }

    fun onLevelComplete() {
        completing = true
        goto(Screen.COMPLETE)
    }

    fun refreshAfterDeath() {}

    fun draw(c: Canvas, time: Float) {
        canvas = c
        hits.clear()
        val t = tr()
        val overGl = screen == Screen.PLAY || screen == Screen.PAUSE || screen == Screen.COMPLETE
        if (!overGl) {
            c.drawColor(bg)
            drawBackdrop(time)
        }
        c.save()
        val slide = (1f - t) * w * 0.18f
        c.translate(slide, 0f)
        when (screen) {
            Screen.SPLASH -> drawSplash()
            Screen.MENU -> drawMenu(time)
            Screen.WORLDS -> drawWorlds()
            Screen.LEVELS -> drawLevels()
            Screen.PLAY -> drawPlay(time)
            Screen.PAUSE -> drawPause()
            Screen.COMPLETE -> drawComplete()
            Screen.SETTINGS -> drawSettings()
            Screen.ACHIEVEMENTS -> drawAchievements()
            Screen.CREATIVE_LIST -> drawCreativeList()
            Screen.CREATIVE_EDIT -> drawCreativeEdit()
            Screen.CHALLENGE -> drawChallenge()
            Screen.HELP -> drawHelp()
        }
        c.restore()
        if (System.currentTimeMillis() < flashUntil) {
            val a = ((flashUntil - System.currentTimeMillis()) / 520f).coerceIn(0f, 1f)
            pFill.color = withAlpha(0xFFFFFFFF.toInt(), a * 0.8f)
            canvas.drawRect(0f, 0f, w, h, pFill)
        }
        val toast = app.toast
        if (toast != null) {
            if (System.currentTimeMillis() > app.toastUntil) app.toast = null
            else {
                val a = ((app.toastUntil - System.currentTimeMillis()) / 400f).coerceIn(0f, 1f)
                pText.color = 0x00000000.toInt()
                val tw = pText.measureText(toast)
                val rw = tw + 64f * scale()
                val rect = RectF(w / 2 - rw / 2, h - 60f * scale(), w / 2 + rw / 2, h - 16f * scale())
                pFill.color = 0xDD101828.toInt()
                pFill.alpha = (a * 255).toInt()
                round(rect, 10f, pFill)
                pText.color = cyan; pText.alpha = (a * 255).toInt()
                pText.textSize = 15f * scale()
                c.drawText(toast, w / 2, h - 32f * scale(), pText)
                pText.alpha = 255
            }
        }
    }

    private fun scale() = min(w, h * 1.7f) / 1000f

    private fun drawBackdrop(time: Float) {
        val s = scale()
        val cx = w * 0.5f
        val cy = h * 0.5f
        pGlow.shader = RadialGradient(cx, cy, min(w, h) * 0.8f, 0x14222A55.toInt(), 0x00000000.toInt(), Shader.TileMode.CLAMP)
        pGlow.style = Paint.Style.FILL
        canvas.drawRect(0f, 0f, w, h, pGlow)
        pGlow.shader = null
        pStroke.color = 0x0A35E8FF.toInt()
        pStroke.strokeWidth = 1.2f * s
        val grid = 64f * s
        val off = (time * 6f) % grid
        var x = -off
        while (x < w) { canvas.drawLine(x, 0f, x, h, pStroke); x += grid }
        var y = -off
        while (y < h) { canvas.drawLine(0f, y, w, y, pStroke); y += grid }
        if (screen != Screen.PLAY && screen != Screen.CREATIVE_EDIT) drawParadoxGlyph(cx, h * 0.20f, 44f * s, time)
    }

    private fun drawParadoxGlyph(cx: Float, cy: Float, r: Float, time: Float) {
        val rot = time * 0.5f
        val cols = intArrayOf(cyan, violet, 0xAAFFFFFF.toInt())
        for (i in 0..2) {
            val a0 = rot + i * 2.0944f
            val a1 = rot + i * 2.0944f + 1.9f
            val path = Path()
            val p0 = pt(cx, cy, r, a0)
            val p1 = pt(cx, cy, r * 1.05f, a1)
            val pm = pt(cx, cy, r * 0.45f, a0 + 0.95f)
            path.moveTo(p0[0], p0[1])
            path.lineTo(pm[0], pm[1])
            path.lineTo(p1[0], p1[1])
            pStroke.color = cols[i]
            pStroke.strokeWidth = r * 0.16f
            pStroke.strokeCap = Paint.Cap.ROUND
            canvas.drawPath(path, pStroke)
        }
        pStroke.strokeCap = Paint.Cap.BUTT
    }

    private fun pt(cx: Float, cy: Float, r: Float, a: Float): FloatArray =
        floatArrayOf(cx + r * cos(a), cy + r * sin(a))

    private fun round(r: RectF, rad: Float, paint: Paint) = canvas.drawRoundRect(r, rad, rad, paint)

    private fun panel(rect: RectF) {
        pFill.color = panel
        round(rect, 14f * scale(), pFill)
        pStroke.color = 0x6635E8FF.toInt()
        pStroke.strokeWidth = 1.4f * scale()
        round(rect, 14f * scale(), pStroke)
    }

    private fun button(label: String, x: Float, y: Float, bw: Float, bh: Float, accent: Int = cyan, action: () -> Unit, sub: String? = null, dim: Boolean = false): RectF {
        val s = scale()
        val r = RectF(x, y, x + bw, y + bh)
        val pressed = downHit?.let { it.rect == r } == true
        pFill.color = if (dim) 0x330A1020.toInt() else if (pressed) 0xFF16324A.toInt() else 0xCC0E1A2E.toInt()
        round(r, 12f * s, pFill)
        pStroke.color = accent
        pStroke.strokeWidth = if (pressed) 2.4f * s else 1.6f * s
        pStroke.alpha = if (dim) 70 else 200
        round(r, 12f * s, pStroke)
        pStroke.alpha = 255
        pText.textSize = 18f * s
        pText.typeface = pTitle.typeface
        pText.color = if (dim) 0x88FFFFFF.toInt() else white
        val baseY = if (sub != null) y + bh * 0.42f else y + bh / 2 + 6f * s
        canvas.drawText(label, x + bw / 2, baseY, pText.also { it.textAlign = Paint.Align.CENTER })
        if (sub != null) {
            pSmall.textSize = 11.5f * s
            pSmall.color = accent
            pSmall.textAlign = Paint.Align.CENTER
            canvas.drawText(sub, x + bw / 2, y + bh * 0.74f, pSmall)
            pSmall.textAlign = Paint.Align.LEFT
        }
        pText.textAlign = Paint.Align.LEFT
        pText.typeface = pText.typeface
        hits.add(Hit(r, { tap(action) }))
        return r
    }

    private fun header(title: String, back: Screen? = Screen.MENU) {
        val s = scale()
        pTitle.textSize = 24f * s
        pTitle.color = cyan
        canvas.drawText(title, 24f * s, 44f * s, pTitle)
        if (back != null) {
            val r = RectF(12f * s, 12f * s, 12f * s + 84f * s, 56f * s)
            button("BACK", r.left, r.top, r.width(), r.height(), violet, { goto(back) })
            pTitle.textSize = 24f * s
            pTitle.color = cyan
            canvas.drawText(title, w / 2, 44f * s, pTitle.also { it.textAlign = Paint.Align.CENTER })
            pTitle.textAlign = Paint.Align.LEFT
        }
    }

    private fun drawSplash() {
        val s = scale()
        val a = (screenTime / 0.8f).coerceIn(0f, 1f)
        pTitle.textSize = 54f * s
        pTitle.color = withAlpha(white, a)
        pTitle.textAlign = Paint.Align.CENTER
        canvas.drawText("PARADOX", w / 2, h * 0.52f, pTitle)
        pSub.textSize = 15f * s
        pSub.color = withAlpha(cyan, a)
        canvas.drawText("IMPOSSIBLE REALITY", w / 2, h * 0.60f, pSub)
        pTitle.textAlign = Paint.Align.LEFT
        if (screenTime > 0.5f) hits.add(Hit(RectF(0f, 0f, w, h), { goto(Screen.MENU) }))
    }

    private fun withAlpha(c: Int, a: Float): Int {
        val aa = (a.coerceIn(0f, 1f) * 255).toInt()
        return (aa shl 24) or (c and 0x00FFFFFF.toInt())
    }

    private fun drawMenu(time: Float) {
        val s = scale()
        val bw = min(420f * s, w * 0.5f)
        val x = w * 0.5f - bw / 2
        var y = h * 0.34f
        pTitle.textSize = 52f * s
        pTitle.color = white
        pTitle.textAlign = Paint.Align.CENTER
        val wob = sin(time * 1.3f) * 3f * s
        canvas.drawText("PARADOX", w / 2, h * 0.17f + wob, pTitle)
        pSub.textSize = 14f * s
        pSub.color = cyan
        canvas.drawText("I M P O S S I B L E   R E A L I T Y", w / 2, h * 0.225f, pSub)
        pTitle.textAlign = Paint.Align.LEFT

        val d = app.save.data
        val cont = d.lastLevel
        if (cont != null) {
            val spec = CampaignLevels.byId(cont)
            if (spec != null) {
                val done = 100 * CampaignLevels.all().count { (d.progress[it.id] ?: 0) > 0 } / CampaignLevels.all().size
                button("CONTINUE", x, y, bw, 58f * s, cyan, {
                    app.startLevel(spec, Mode.CAMPAIGN); goto(Screen.PLAY)
                }, sub = spec.name + " - " + done + "%")
                y += 66f * s
            }
        }
        button("CAMPAIGN", x, y, bw, 54f * s, cyan, { goto(Screen.WORLDS) }, sub = "" + d.completedCount() + "/36")
        y += 62f * s
        button("DAILY CHALLENGE", x, y, bw, 54f * s, violet, {
            app.startDaily(); goto(Screen.PLAY)
        }, sub = if (d.dailyCompletedToday) "COMPLETED TODAY" else "NEW EVERY DAY")
        y += 62f * s
        button("ENDLESS PARADOX", x, y, bw, 54f * s, violet, {
            app.endlessStreak = 0; app.nextEndless(); goto(Screen.PLAY)
        }, sub = "BEST " + d.stats.endlessBest)
        y += 62f * s
        val half = bw / 2 - 5f * s
        button("CHALLENGE", x, y, half, 54f * s, cyan, { goto(Screen.CHALLENGE) })
        button("CREATIVE", x + half + 10f * s, y, half, 54f * s, violet, { goto(Screen.CREATIVE_LIST) })
        y += 62f * s
        val third = bw / 3 - 6f * s
        button("AWARDS", x, y, third, 48f * s, cyan, { goto(Screen.ACHIEVEMENTS) })
        button("SETTINGS", x + third + 8f * s, y, third, 48f * s, cyan, { goto(Screen.SETTINGS) })
        button("HELP", x + 2 * (third + 8f * s), y, third, 48f * s, cyan, { helpReturn = Screen.MENU; goto(Screen.HELP) })
    }

    private fun drawWorlds() {
        val s = scale()
        header("SELECT WORLD")
        val cw = (w - 60f * s) / 2
        val ch = 96f * s
        for (i in 0..5) {
            val levels = CampaignLevels.levelsOfWorld(i)
            val done = levels.count { (app.save.data.progress[it.id] ?: 0) > 0 }
            val col = i / 2
            val row = i % 2
            val x = 20f * s + row * (cw + 20f * s)
            val y = 76f * s + col * (ch + 18f * s)
            val r = RectF(x, y, x + cw, y + ch)
            if (i > 0 && done == 0 && CampaignLevels.levelsOfWorld(i - 1).none { (app.save.data.progress[it.id] ?: 0) > 0 }) {
                button(CampaignLevels.worldNames[i], x, y, cw, ch, 0xFF333A55.toInt(), { tap { } }, sub = "LOCKED - CLEAR PREVIOUS WORLD", dim = true)
            } else {
                button(CampaignLevels.worldNames[i], x, y, cw, ch, if (i % 2 == 0) cyan else violet, {
                    selectedWorld = i
                    goto(Screen.LEVELS)
                }, sub = "" + done + "/" + levels.size + " CLEARED")
            }
        }
    }

    private fun drawLevels() {
        val s = scale()
        header(CampaignLevels.worldNames[selectedWorld])
        val levels = CampaignLevels.levelsOfWorld(selectedWorld)
        var y = 84f * s
        for (lvl in levels) {
            val stars = app.save.data.progress[lvl.id] ?: 0
            val cw = w - 48f * s
            button(lvl.name, 24f * s, y, cw, 62f * s, if (stars > 0) cyan else violet, {
                app.startLevel(lvl, Mode.CAMPAIGN)
                goto(Screen.PLAY)
            }, sub = "PAR " + lvl.par + "   MOVE LIMIT -  " + if (stars == 0) "UNSOLVED" else "BEST " + stars + "*")
            val ss = 14f * s
            for (k in 0..2) {
                pFill.color = if (k < stars) 0xFFFFD54F.toInt() else 0x22FFFFFF.toInt()
                canvas.drawCircle(24f * s + cw - 20f * s - k * 3f * ss, y + 31f * s, ss * 0.42f, pFill)
            }
            y += 72f * s
        }
    }

    private fun drawPlay(time: Float) {
        val s = scale()
        val l = app.logic ?: return
        val lvl = app.level ?: return
        val now = System.currentTimeMillis()

        pText.textSize = 15f * s
        pText.color = white
        canvas.drawText(lvl.name, 20f * s, 34f * s, pText)
        pSmall.textSize = 12f * s
        val mins = app.elapsed / 60f
        val secs = app.elapsed % 60f
        val timer = String.format("%02d:%02d", mins.toInt(), secs.toInt())
        pSmall.color = 0xFF8FA6C8.toInt()
        canvas.drawText(timer, 20f * s, 56f * s, pSmall)
        val gDir = when (l.state.gravityIdx) {
            0 -> "G: -Y"; 1 -> "G: +Y"; 2 -> "G: +X"; 3 -> "G: -X"; 4 -> "G: +Z"; else -> "G: -Z"
        }
        val movesCol = if (l.state.moves <= lvl.par) 0xFF35E8FF.toInt() else 0xFFFFB74D.toInt()
        pSmall.color = movesCol
        canvas.drawText("MOVES " + l.state.moves + " / PAR " + lvl.par + "    " + gDir, 20f * s, 74f * s, pSmall)
        if (app.mode == Mode.ENDLESS) {
            pSmall.color = 0xFF8FA6C8.toInt()
            canvas.drawText("STREAK " + app.endlessStreak, 20f * s, 92f * s, pSmall)
        }
        if (app.mode == Mode.CHALLENGE) {
            val c = app.challenge
            if (c != null) {
                pSmall.color = violet
                canvas.drawText(c.name + " - " + c.desc, 20f * s, 92f * s, pSmall)
                if (c.kind == 0) canvas.drawText("MAX " + c.limit + " MOVES", 20f * s, 108f * s, pSmall)
            }
        }
        button("II", w - 64f * s, 16f * s, 48f * s, 44f * s, white, { goto(Screen.PAUSE) })
        button("RST", w - 124f * s, 16f * s, 52f * s, 44f * s, violet, { app.restart() })
        var phaseBtn = RectF(0f, 0f, 0f, 0f)
        if (lvl.phases > 1) {
            phaseBtn = button("PHASE", w - 250f * s, 16f * s, 118f * s, 44f * s, 0xFF9C6BFF.toInt(), { app.togglePhase() })
            if (!phaseUsed) {
                val pulse = 0.5f + 0.5f * sin(time * 4f)
                pStroke.color = 0xFFFFD54F.toInt()
                pStroke.strokeWidth = (2f + 3f * pulse) * s
                round(RectF(phaseBtn.left - 5f * s, phaseBtn.top - 5f * s, phaseBtn.right + 5f * s, phaseBtn.bottom + 5f * s), 16f * s, pStroke)
                pSmall.textSize = 10.5f * s
                pSmall.color = 0xFFFFD54F.toInt()
                canvas.drawText("BRIDGES!", phaseBtn.centerX(), phaseBtn.bottom + 16f * s, pSmall.also { it.textAlign = Paint.Align.CENTER })
                pSmall.textAlign = Paint.Align.LEFT
            }
        }
        if (app.save.data.settings.hints && !lvl.hint.isEmpty()) {
            button("?", w - 292f * s, 16f * s, 36f * s, 44f * s, cyan, {
                app.showToast("HINT: " + lvl.hint)
            })
        }
        drawDPad()

        if (app.mode == Mode.CAMPAIGN && lvl.id == "w1l1" && l.state.moves == 0 && (app.save.data.progress["w1l1"] ?: 0) == 0) {
            val msg = "SWIPE or use the ARROW BUTTONS - reach the white beacon"
            pSmall.textSize = 13f * s
            pSmall.color = white
            val tw = pSmall.measureText(msg) + 40f * s
            val r = RectF(w / 2 - tw / 2, h - 44f * s, w / 2 + tw / 2, h - 12f * s)
            pFill.color = 0xCC0E1A2E.toInt()
            round(r, 9f * s, pFill)
            pStroke.color = cyan
            pStroke.strokeWidth = 1.2f * s
            round(r, 9f * s, pStroke)
            pSmall.textAlign = Paint.Align.CENTER
            canvas.drawText(msg, w / 2, h - 22f * s, pSmall)
            pSmall.textAlign = Paint.Align.LEFT
        }

        if (now < app.introUntil) {
            pFill.color = 0xB8050810.toInt()
            canvas.drawRect(0f, 0f, w, h, pFill)
            val bw = min(520f * s, w * 0.72f)
            val r = RectF(w / 2 - bw / 2, h * 0.18f, w / 2 + bw / 2, h * 0.66f)
            panel(r)
            pTitle.textSize = 30f * s
            pTitle.color = white
            pTitle.textAlign = Paint.Align.CENTER
            canvas.drawText(lvl.name, w / 2, h * 0.28f, pTitle)
            pSmall.textSize = 13f * s
            pSmall.color = cyan
            canvas.drawText("WORLD " + (lvl.world + 1) + "   PAR " + lvl.par, w / 2, h * 0.35f, pSmall)
            pText.textSize = 15f * s
            pText.color = 0xFFB8C6E0.toInt()
            wrapText(lvl.hint, w / 2, h * 0.44f, bw - 60f * s, 24f * s)
            pSmall.textSize = 13f * s
            pSmall.color = 0xFFFFD54F.toInt()
            canvas.drawText("TAP TO BEGIN", w / 2, h * 0.60f, pSmall.also { if (sin(time * 5f) > 0f) it.alpha = 255 else it.alpha = 120 })
            pSmall.alpha = 255
            pTitle.textAlign = Paint.Align.LEFT
            hits.add(Hit(RectF(0f, 0f, w, h), { app.introUntil = 0 }))
        } else if (l.state.dead && !l.state.finished) {
            pFill.color = 0xAA2A0A0A.toInt()
            canvas.drawRect(0f, 0f, w, h, pFill)
            pTitle.textSize = 34f * s
            pTitle.color = 0xFFFF5252.toInt()
            pTitle.textAlign = Paint.Align.CENTER
            canvas.drawText("SYSTEM FAILURE", w / 2, h * 0.42f, pTitle)
            pSmall.textSize = 15f * s
            pSmall.color = white
            canvas.drawText("TAP TO RETRY", w / 2, h * 0.53f, pSmall)
            pTitle.textAlign = Paint.Align.LEFT
            hits.add(Hit(RectF(0f, 0f, w, h), { tap { app.restart() } }))
        }
    }

    private fun wrapText(text: String, cx: Float, y0: Float, maxW: Float, lineH: Float) {
        val words = text.split(" ")
        val lines = ArrayList<String>()
        var cur = StringBuilder()
        for (wd in words) {
            val test = if (cur.isEmpty()) wd else cur.toString() + " " + wd
            if (pText.measureText(test) > maxW && cur.isNotEmpty()) {
                lines.add(cur.toString()); cur = StringBuilder(wd)
            } else cur = StringBuilder(test)
        }
        if (cur.isNotEmpty()) lines.add(cur.toString())
        var y = y0
        pText.textAlign = Paint.Align.CENTER
        for (ln in lines) { canvas.drawText(ln, cx, y, pText); y += lineH }
        pText.textAlign = Paint.Align.LEFT
    }

    private fun drawDPad() {
        val s = scale()
        val cx = w - 92f * s
        val cy = h - 96f * s
        val bs = 56f * s
        val gap = 62f * s
        fun dpad(dx: Float, dy: Float, label: String) {
            val x = cx + dx * gap - bs / 2
            val y = cy + dy * gap - bs / 2
            val r = RectF(x, y, x + bs, y + bs)
            pFill.color = 0x9910203A.toInt()
            round(r, 12f * s, pFill)
            pStroke.color = cyan
            pStroke.strokeWidth = 1.6f * s
            round(r, 12f * s, pStroke)
            pText.textSize = 16f * s
            pText.color = white
            pText.textAlign = Paint.Align.CENTER
            canvas.drawText(label, x + bs / 2, y + bs / 2 + 6f * s, pText)
            pText.textAlign = Paint.Align.LEFT
            hits.add(Hit(r, {
                val dp = dpToDir(label); app.move(dp.first, dp.second)
            }))
        }
        dpad(0f, -1f, "UP")
        dpad(-1f, 0f, "LF")
        dpad(1f, 0f, "RT")
        dpad(0f, 1f, "DN")
    }

    private fun dpToDir(label: String): Pair<Float, Float> = when (label) {
        "UP" -> 0f to -1f
        "DN" -> 0f to 1f
        "LF" -> -1f to 0f
        else -> 1f to 0f
    }

    private fun drawPause() {
        val s = scale()
        dim()
        val bw = min(400f * s, w * 0.6f)
        val x = w / 2 - bw / 2
        var y = h * 0.20f
        button("RESUME", x, y, bw, 56f * s, cyan, { goto(Screen.PLAY) }); y += 66f * s
        button("RESTART " + (app.moves).toString() + " MOVES", x, y, bw, 56f * s, cyan, { app.restart(); goto(Screen.PLAY) }); y += 66f * s
        button("SETTINGS", x, y, bw, 56f * s, violet, { goto(Screen.SETTINGS) }); y += 66f * s
        button("HELP / LEGEND", x, y, bw, 56f * s, cyan, { helpReturn = Screen.PAUSE; goto(Screen.HELP) }); y += 66f * s
        button("QUIT TO MENU", x, y, bw, 56f * s, violet, { goto(Screen.MENU) })
    }

    private fun dim() {
        pFill.color = 0xB0050810.toInt()
        canvas.drawRect(0f, 0f, w, h, pFill)
    }

    private fun drawComplete() {
        val s = scale()
        dim()
        pTitle.textSize = 40f * s
        pTitle.color = white
        pTitle.textAlign = Paint.Align.CENTER
        canvas.drawText("DIMENSION CLEARED", w / 2, h * 0.16f, pTitle)
        pTitle.textAlign = Paint.Align.LEFT
        val l = app.logic
        val lvl = app.level
        if (l == null || lvl == null) return
        val stars = if (app.mode == Mode.CAMPAIGN) (app.save.data.progress[lvl.id] ?: 0) else app.starsFor(l.state.moves)
        for (k in 0..2) {
            val on = k < stars
            val cx = w / 2 + (k - 1) * 84f * s
            val cy = h * 0.27f
            pFill.color = if (on) 0xFFFFD54F.toInt() else 0x33FFFFFF.toInt()
            canvas.drawCircle(cx, cy, 22f * s, pFill)
            pStroke.color = if (on) 0xFFFFFFFF.toInt() else 0x44FFFFFF.toInt()
            pStroke.strokeWidth = 2f * s
            canvas.drawCircle(cx, cy, 22f * s, pStroke)
        }
        pText.textSize = 16f * s
        pText.color = cyan
        pText.textAlign = Paint.Align.CENTER
        val opt = app.solutionLength
        canvas.drawText("MOVES " + l.state.moves + "   PAR " + lvl.par + (if (opt > 0) "   OPTIMAL " + opt else ""), w / 2, h * 0.40f, pText)
        pText.textAlign = Paint.Align.LEFT
        val bw = min(400f * s, w * 0.6f)
        val x = w / 2 - bw / 2
        var y = h * 0.52f
        val nextLabel = when (app.mode) {
            Mode.ENDLESS -> "NEXT ANOMALY"
            Mode.CAMPAIGN -> "NEXT LEVEL"
            else -> "NEW PUZZLE"
        }
        button(nextLabel, x, y, bw, 56f * s, cyan, { nextAfterComplete() }); y += 66f * s
        button("REPLAY", x, y, bw, 56f * s, violet, { app.restart(); goto(Screen.PLAY) }); y += 66f * s
        button("MENU", x, y, bw, 56f * s, violet, { goto(Screen.MENU) })
    }

    private fun nextAfterComplete() {
        when (app.mode) {
            Mode.CAMPAIGN -> {
                val all = CampaignLevels.all()
                val idx = all.indexOfFirst { it.id == app.level?.id }
                if (idx >= 0 && idx + 1 < all.size) {
                    app.startLevel(all[idx + 1], Mode.CAMPAIGN)
                    goto(Screen.PLAY)
                } else {
                    app.showToast("CAMPAIGN COMPLETE - TRUE PARADOX!")
                    goto(Screen.MENU)
                }
            }
            Mode.ENDLESS -> app.nextEndless().let { goto(Screen.PLAY) }
            Mode.DAILY -> goto(Screen.MENU)
            Mode.CHALLENGE -> goto(Screen.CHALLENGE)
            Mode.CREATIVE_TEST -> goto(Screen.CREATIVE_EDIT)
        }
    }

    private fun drawSettings() {
        val s = scale()
        header("SETTINGS", back = Screen.MENU)
        val bw = min(430f * s, w - 60f * s)
        val x = w / 2 - bw / 2
        var y = 90f * s
        val d = app.save.data.settings

        fun slider(label: String, value: Float, set: (Float) -> Unit) {
            pText.textSize = 15f * s
            pText.color = white
            canvas.drawText(label, x, y + 24f * s, pText)
            val tx = x + 150f * s
            val tw = bw - 150f * s
            val r = RectF(tx, y, tx + tw, y + 34f * s)
            pFill.color = 0xFF101A30.toInt()
            round(r, 8f * s, pFill)
            pFill.color = cyan
            val fr = RectF(tx, y, tx + tw * value, y + 34f * s)
            round(fr, 8f * s, pFill)
            pStroke.color = 0xFF35E8FF.toInt()
            round(r, 8f * s, pStroke)
            hits.add(Hit(r, {
                val nx = ((downX - tx) / tw).coerceIn(0f, 1f)
                set(nx)
            }, draggable = true))
            pSmall.textSize = 12f * s
            pSmall.color = cyan
            canvas.drawText("" + (value * 100).toInt() + "%", tx + tw + 12f * s, y + 24f * s, pSmall)
            y += 56f * s
        }
        slider("MUSIC", d.music) { v -> d.music = v; app.audio.setVolumes(d.music, d.sfx); app.save.save() }
        slider("EFFECTS", d.sfx) { v -> d.sfx = v; app.audio.setVolumes(d.music, d.sfx); app.save.save() }
        toggle("BLOOM + GLOW", d.bloom, x, y, bw) { v -> d.bloom = v; app.scene.gl.bloomEnabled = v; app.save.save() }
        y += 60f * s
        toggle("HINTS", d.hints, x, y, bw) { v -> d.hints = v; app.save.save() }
        y += 80f * s
        button("STATS: " + app.save.data.stats.wins + " WINS - " + app.save.data.stats.plays + " RUNS", x, y, bw, 48f * s, 0xFF90A4AE.toInt(), { }, dim = true)
        y += 58f * s
        button("RESET ALL PROGRESS", x, y, bw, 48f * s, 0xFFFF5252.toInt(), {
            if (resetArmed) {
                app.save.data.progress.clear()
                app.save.data.achievements.clear()
                app.save.data.custom.clear()
                app.save.data.lastLevel = null
                app.save.save()
                resetArmed = false
                app.showToast("PROGRESS RESET")
            } else {
                resetArmed = true
                app.showToast("TAP AGAIN TO CONFIRM RESET")
            }
        })
    }

    var resetArmed = false

    private fun toggle(label: String, v: Boolean, x: Float, y: Float, bw: Float, set: (Boolean) -> Unit) {
        val s = scale()
        pText.textSize = 15f * s
        pText.color = white
        canvas.drawText(label, x, y + 24f * s, pText)
        val tw = 110f * s
        val tx = x + bw - tw
        val r = RectF(tx, y, tx + tw, y + 34f * s)
        pFill.color = if (v) 0xFF16324A.toInt() else 0xFF1A1E2C.toInt()
        round(r, 17f * s, pFill)
        pStroke.color = if (v) cyan else 0xFF444F66.toInt()
        round(r, 17f * s, pStroke)
        pFill.color = if (v) cyan else 0xFF667
        val kx = if (v) tx + tw - 30f * s else tx + 4f * s
        canvas.drawCircle(kx + 13f * s, y + 17f * s, 11f * s, pFill)
        hits.add(Hit(r, { tap { set(!v) } }))
    }

    private fun drawAchievements() {
        val s = scale()
        header("ACHIEVEMENTS", back = Screen.MENU)
        canvas.save()
        canvas.clipRect(0f, 70f * s, w, h)
        var y = 84f * s - scrollY
        val unlocked = app.save.data.achievements
        for (a in com.paradox.game.app.Achievements.all) {
            val has = unlocked.contains(a.id)
            val cw = w - 48f * s
            val r = RectF(24f * s, y, 24f * s + cw, y + 64f * s)
            pFill.color = if (has) 0xCC0E2A3A.toInt() else 0x660A1020.toInt()
            round(r, 12f * s, pFill)
            pStroke.color = if (has) 0xFF35E8FF.toInt() else 0xFF2A3350.toInt()
            pStroke.strokeWidth = 1.4f * s
            round(r, 12f * s, pStroke)
            pTitle.textSize = 15f * s
            pTitle.color = if (has) white else 0xFF5A6A8A.toInt()
            canvas.drawText(a.title, 40f * s, y + 26f * s, pTitle)
            pSmall.textSize = 11.5f * s
            pSmall.color = if (has) cyan else 0xFF3D4A6E.toInt()
            canvas.drawText(a.desc, 40f * s, y + 48f * s, pSmall)
            if (has) {
                pFill.color = 0xFF35E8FF.toInt()
                canvas.drawCircle(24f * s + cw - 26f * s, y + 32f * s, 8f * s, pFill)
            }
            y += 72f * s
        }
        canvas.restore()
        grips()
    }

    private fun grips() {
        val s = scale()
        val maxScroll = scrollMaxY()
        if (maxScroll <= 0f) return
        val barH = maxOf(60f * s, h * h / 1f)
        val frac = scrollY / maxScroll
        pFill.color = 0x5535E8FF.toInt()
        val bh = 120f * s
        val by = 80f * s + (h - 180f * s - bh) * frac.coerceIn(0f, 1f)
        round(RectF(w - 8f * s, by, w - 3f * s, by + bh), 3f * s, pFill)
    }

    private fun scrollMaxY(): Float {
        return when (screen) {
            Screen.ACHIEVEMENTS -> 72f * scale() * com.paradox.game.app.Achievements.all.size - (h - 100f * scale())
            Screen.CREATIVE_LIST -> 82f * scale() * maxOf(1, app.save.data.custom.size) - (h - 140f * scale())
            else -> 0f
        }.coerceAtLeast(0f)
    }

    private fun scrollMaxYPlaceholder() {}

    private fun drawCreativeList() {
        val s = scale()
        header("CREATIVE MODE", back = Screen.MENU)
        val bw = min(430f * s, w - 48f * s)
        var y = 84f * s
        button("NEW LEVEL", 24f * s, y, bw, 56f * s, violet, { app.editor.newLevel(); goto(Screen.CREATIVE_EDIT) }); y += 64f * s
        val customs = app.save.data.custom
        canvas.save()
        canvas.clipRect(0f, y, w, h)
        var yy = y - scrollY
        for (i in customs.indices) {
            val spec = LevelCodec.decode(customs[i])
            button(spec?.name ?: "BROKEN DATA", 24f * s, yy, bw, 56f * s, cyan, {
                if (spec != null) {
                    app.startLevel(spec, Mode.CREATIVE_TEST)
                    goto(Screen.PLAY)
                }
            }, sub = "PLAY - SOLVABILITY VERIFIED AT BUILD TIME")
            button("X", 24f * s + bw - 56f * s, yy + 8f * s, 44f * s, 40f * s, 0xFFFF5252.toInt(), {
                app.save.data.custom.removeAt(i)
                app.save.save()
                scrollY = 0f
            })
            yy += 64f * s
        }
        canvas.restore()
        grips()
    }

    private fun drawHelp() {
        val s = scale()
        header("HOW TO PLAY", back = helpReturn)
        var y = 96f * s
        pText.textSize = 13.5f * s
        val lines = listOf(
            "SWIPE or use the ARROW PAD to move. Moves are relative",
            "to the camera - rotate the view if a direction feels wrong.",
            "DRAG empty space to orbit, PINCH to zoom.",
            "Reach the WHITE BEACON. PAR is the target move count.",
            "",
            "BUTTONS: II pauses - RST restarts - ? gives a hint -",
            "PHASE appears only in levels with illusion bridges."
        )
        for (ln in lines) {
            pText.color = if (ln.startsWith("BUTTONS")) 0xFFFFD54F.toInt() else 0xFFB8C6E0.toInt()
            canvas.drawText(ln, 26f * s, y, pText)
            y += 26f * s
        }
        y += 8f * s
        pTitle.textSize = 15f * s
        pTitle.color = cyan
        canvas.drawText("LEGEND", 26f * s, y, pTitle)
        y += 28f * s
        val legend = listOf(
            Pair(0xFFFFFFFF.toInt(), "WHITE BEACON + COLUMN - the goal. Touch its tile."),
            Pair(0xFFB26BFF.toInt(), "VIOLET RING - portal. Teleports to its twin."),
            Pair(0xFF35E8FF.toInt(), "ARROW TILE - gravity pad. Arrow = new DOWN."),
            Pair(0xFF35E8FF.toInt(), "FLICKERING SLAB - phase bridge. Solid only in"),
            Pair(0xFF35E8FF.toInt(), "the matching phase (use PHASE button)."),
            Pair(0xFFFFD54F.toInt(), "YELLOW PLATE - switch. Stays on while stood on."),
            Pair(0xFFFF5252.toInt(), "RED WALL - closed door. Opens while its switch"),
            Pair(0xFFFF5252.toInt(), "is pressed (park a cube on it to hold it)."),
            Pair(0xFFFF8A3C.toInt(), "AMBER CUBE - pushable crate. Falls with gravity."),
            Pair(0xFFFFEB3B.toInt(), "BLINKING YELLOW - timed gate. Cross when open."),
            Pair(0xFFE040FB.toInt(), "VIOLET PLATFORM - rotator. Turns when its plate"),
            Pair(0xFFE040FB.toInt(), "is pressed.")
        )
        for ((col, txt) in legend) {
            pFill.color = col
            canvas.drawCircle(36f * s, y - 5f * s, 6f * s, pFill)
            pSmall.textSize = 12.5f * s
            pSmall.color = 0xFFB8C6E0.toInt()
            canvas.drawText(txt, 52f * s, y, pSmall)
            y += 24f * s
        }
        y += 6f * s
        pSmall.textSize = 12.5f * s
        pSmall.color = cyan
        canvas.drawText("3 STARS need the optimal line. Multiple routes exist", 26f * s, y, pSmall)
        y += 24f * s
        canvas.drawText("in many levels - your solution does not have to match", 26f * s, y, pSmall)
    }

    private fun drawChallenge() {
        val s = scale()
        header("CHALLENGE MODE", back = Screen.MENU)
        var y = 90f * s
        for (c in ChallengeModeDefs.all) {
            button(c.name, 24f * s, y, w - 48f * s, 64f * s, violet, {
                app.startChallenge(c)
                goto(Screen.PLAY)
            }, sub = c.desc)
            y += 74f * s
        }
    }

    private fun drawCreativeEdit() {
        val s = scale()
        val ed = app.editor
        header("EDITOR - " + ed.tool.name, back = Screen.CREATIVE_LIST)
        val gx0 = 120f * s
        val gy0 = 100f * s
        val cellD = min((w - gx0 - 30f * s) / ed.gridW, (h - gy0 - 220f * s) / ed.gridH)
        ed.setDisplay(gx0, gy0, cellD)
        for (gz in 0 until ed.gridH) {
            for (gx in 0 until ed.gridW) {
                val px = gx0 + gx * cellD
                val py = gy0 + gz * cellD
                val cell = ed.cellAt(gx, gz)
                val r = RectF(px, py, px + cellD - 2f, py + cellD - 2f)
                pFill.color = when {
                    cell == null -> 0x160E1A2E.toInt()
                    else -> edColor(cell)
                }
                round(r, 4f * s, pFill)
                pStroke.color = 0x2235E8FF.toInt()
                pStroke.strokeWidth = 1f
                if (cell == null) round(r, 4f * s, pStroke)
                hits.add(Hit(r, { tap { ed.tap(gx, gz) } }))
            }
        }
        pText.textSize = 12f * s
        pText.color = cyan
        canvas.drawText("SPAWN " + ed.spawnX + "," + ed.spawnZ + "   GOAL " + ed.goalX + "," + ed.goalZ, 20f * s, gy0 + 18f * s, pText)

        val bx = 16f * s
        var by = h - 200f * s
        val tw = 100f * s
        val tools = EdTool.values()
        var col = 0
        for (t in tools) {
            val x = bx + col * (tw + 8f * s)
            val sel = t == ed.tool
            button(t.label, x, by, tw, 34f * s, if (sel) 0xFFFFD54F.toInt() else cyan, { ed.tool = t }, dim = !sel && col > 0)
            col++
            if (col >= 5) { col = 0; by += 40f * s }
        }
        by = h - 200f * s - 8f * s
        button("TEST", bx, by, tw, 34f * s, violet, { testCreative() })
        button("SAVE", bx + (tw + 8f * s), by, tw, 34f * s, violet, { saveCreative() })
        button("CLEAR", bx + 2 * (tw + 8f * s), by, tw, 34f * s, 0xFF90A4AE.toInt(), { ed.newLevel() })
    }

    private fun edColor(cell: EditorCell): Int = when (cell.type) {
        0 -> 0xFF24405E.toInt()
        1 -> 0xFF3A5A82.toInt()
        2 -> 0xFF886BFF.toInt()
        3 -> 0xFFFFD54F.toInt()
        4 -> 0xFF00E5FF.toInt()
        5 -> 0xFFB26BFF.toInt()
        6 -> 0xFFFF8A3C.toInt()
        7 -> 0xFFFFFFFF.toInt()
        8 -> 0xFF30E0A0.toInt()
        else -> 0xFF204060.toInt()
    }

    private fun testCreative() {
        val ed = app.editor
        val spec = ed.buildSpec() ?: run { app.showToast("ADD FLOOR, SPAWN AND GOAL"); return }
        app.editor.pendingSaveSpec = spec
        app.startLevel(spec, Mode.CREATIVE_TEST)
        goto(Screen.PLAY)
    }

    private fun saveCreative() {
        val ed = app.editor
        val spec = ed.buildSpec() ?: run { app.showToast("NOTHING TO SAVE"); return }
        if (!app.editor.lastTestOk) {
            app.showToast("TEST FIRST - LEVEL MUST BE SOLVABLE")
            return
        }
        app.save.data.custom.add(LevelCodec.encode(spec))
        if (app.save.data.custom.size > 0) app.checkAchievements()
        app.save.save()
        app.showToast("LEVEL SAVED")
        goto(Screen.CREATIVE_LIST)
    }

    fun onTouch(ev: MotionEvent): Boolean {
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = ev.x; downY = ev.y
                lastX = ev.x; lastY = ev.y
                downTime = System.currentTimeMillis()
                dragging = false; draggingCam = false
                downHit = hitAt(ev.x, ev.y)
            }
            MotionEvent.ACTION_POINTER_DOWN -> {
                if (ev.pointerCount == 2) {
                    pinchDist = distOf(ev)
                }
            }
            MotionEvent.ACTION_MOVE -> {
                if (ev.pointerCount >= 2 && screen == Screen.PLAY) {
                    val d = distOf(ev)
                    app.scene.zoomBy(d - pinchDist)
                    pinchDist = d
                    return true
                }
                val dx = ev.x - lastX
                val dy = ev.y - lastY
                if (screen == Screen.SETTINGS) {
                    val hit = downHit
                    if (hit != null && hit.draggable) {
                        hit.action()
                        lastX = ev.x; lastY = ev.y
                        return true
                    }
                }
                if (abs(ev.x - downX) + abs(ev.y - downY) > 42f && !dragging) {
                    dragging = true
                    if ((downHit == null || screen == Screen.PLAY || screen == Screen.LEVELS || screen == Screen.ACHIEVEMENTS || screen == Screen.CREATIVE_LIST)) {
                        if (screen == Screen.PLAY) draggingCam = true
                    }
                    if (screen == Screen.ACHIEVEMENTS || screen == Screen.CREATIVE_LIST) draggingCam = false
                }
                if (dragging) {
                    if (draggingCam) {
                        app.scene.orbitBy(dx, dy)
                    } else {
                        scrollY = (scrollY - dy).coerceIn(0f, scrollMaxY())
                    }
                }
                lastX = ev.x; lastY = ev.y
            }
            MotionEvent.ACTION_UP -> {
                val hit = downHit
                val dt = System.currentTimeMillis() - downTime
                val swipeLen = abs(ev.x - downX) + abs(ev.y - downY)
                if (screen == Screen.SETTINGS && hit != null && hit.draggable) {
                    hit.action(); downHit = null; return true
                }
                if (!dragging && hit != null && dt < 600) {
                    hit.action()
                } else if (screen == Screen.PLAY && swipeLen > 30f && dt < 400 && hit == null) {
                    app.move(ev.x - downX, ev.y - downY)
                }
                downHit = null
                dragging = false
                draggingCam = false
            }
        }
        return true
    }

    private fun distOf(ev: MotionEvent): Float {
        val dx = ev.getX(0) - ev.getX(1)
        val dy = ev.getY(0) - ev.getY(1)
        return kotlin.math.sqrt(dx * dx + dy * dy)
    }

    private fun hitAt(x: Float, y: Float): Hit? {
        for (i in hits.indices.reversed()) {
            val r = hits[i].rect
            if (x >= r.left && x <= r.right && y >= r.top && y <= r.bottom) return hits[i]
        }
        return null
    }

    private fun scrollMaxYForList(): Float = 0f
}
