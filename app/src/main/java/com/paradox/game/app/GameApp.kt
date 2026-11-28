package com.paradox.game.app

import com.paradox.game.audio.AudioEngine
import com.paradox.game.data.SaveManager
import com.paradox.game.engine.Vec3
import com.paradox.game.engine.Vec3i
import com.paradox.game.game.CampaignLevels
import com.paradox.game.game.GameLogic
import com.paradox.game.game.GameState
import com.paradox.game.game.LevelCodec
import com.paradox.game.game.LevelGenerator
import com.paradox.game.game.LevelSpec
import com.paradox.game.game.Solver
import com.paradox.game.render.PlayerAnim
import com.paradox.game.render.SceneRenderer
import android.os.VibrationEffect
import android.os.Vibrator
import java.util.TimeZone

class Achievement(val id: String, val title: String, val desc: String)

object Achievements {
    val all = listOf(
        Achievement("first_light", "FIRST LIGHT", "Complete your first level."),
        Achievement("world_1", "AWAKENED", "Finish World I."),
        Achievement("world_2", "MACHINIST", "Finish World II."),
        Achievement("world_3", "LIGHTWEAVER", "Finish World III."),
        Achievement("world_4", "WALKER OF WORMHOLES", "Finish World IV."),
        Achievement("world_5", "TILTED", "Finish World V."),
        Achievement("world_6", "PARADOX", "Finish every level of World VI."),
        Achievement("perfectionist", "PERFECTIONIST", "Earn 3 stars on 10 levels."),
        Achievement("archivist", "ARCHIVIST", "Earn 3 stars on 20 levels."),
        Achievement("completionist", "COMPLETIONIST", "Finish all 36 campaign levels."),
        Achievement("portal_pro", "THRESHOLD", "Travel 25 portals."),
        Achievement("tilt_master", "TIPPING POINT", "Trigger 20 gravity pads."),
        Achievement("puppeteer", "PUPPETEER", "Push 15 cubes."),
        Achievement("shifty", "SHIFTY", "Shift phase 20 times."),
        Achievement("undead", "UNDEAD", "Fall 10 times. Keep going."),
        Achievement("casual_50", "PERSISTENT", "Play 50 rounds."),
        Achievement("daily_1", "ROUTINE", "Complete a Daily Challenge."),
        Achievement("daily_3", "HABIT", "Keep a 3-day daily streak."),
        Achievement("endless_5", "SPIRALING", "Solve 5 Endless Paradox levels in one run."),
        Achievement("creator", "ARCHITECT", "Create and save a custom level."),
        Achievement("under_par", "OVERACHIEVER", "Beat a level with 0 wasted moves vs optimal.")
    )

    fun byId(id: String): Achievement? = all.firstOrNull { it.id == id }
}

enum class Mode { CAMPAIGN, DAILY, ENDLESS, CREATIVE_TEST, CHALLENGE }

class ChallengeDef(val id: String, val name: String, val desc: String, val kind: Int, val limit: Int, val difficulty: Int)

object ChallengeModeDefs {
    val all = listOf(
        ChallengeDef("c_sprint", "SPRINT", "Solve under a strict move limit.", 0, 14, 3),
        ChallengeDef("c_marathon", "ANOMALY CHAIN", "Solve a long generated maze.", 0, 30, 6),
        ChallengeDef("c_blink", "BLINK", "Doors cycle fast. Time every step.", 1, 0, 4),
        ChallengeDef("c_zen", "NO RESET", "Solve with no retries allowed.", 2, 0, 4)
    )
}

@Suppress("unused", "UNUSED_PARAMETER")
class GameApp(
    val activity: android.app.Activity,
    val save: SaveManager,
    val audio: AudioEngine,
    val scene: SceneRenderer
) {
    lateinit var ui: com.paradox.game.ui.UiLayer
    val editor = com.paradox.game.ui.Editor(this)

    var logic: GameLogic? = null
    var level: LevelSpec? = null
    var mode: Mode = Mode.CAMPAIGN
    var anim = PlayerAnim(Vec3(0f, 0f, 0f), Vec3(0f, 0f, 0f))

    var solutionLength = 0
    var moves = 0
    var deaths = 0
    var resets = 0
    var roundStart = 0L
    var challenge: ChallengeDef? = null
    var challengeFailed = false
    var elapsed = 0f
    var sessionStart = System.currentTimeMillis()
    var endlessStreak = 0
    var hintShown = false
    var toast: String? = null
    var toastUntil = 0L
    var newestAchievement: Achievement? = null
    var introUntil = 0L
    val seenTutorial = HashSet<String>()
    private val vibrator = try { activity.getSystemService(android.content.Context.VIBRATOR_SERVICE) as? Vibrator } catch (_: Exception) { null }

    fun buzz(pattern: LongArray) {
        try {
            val v = vibrator ?: return
            if (android.os.Build.VERSION.SDK_INT >= 26) v.vibrate(VibrationEffect.createWaveform(pattern, -1))
            else @Suppress("DEPRECATION") v.vibrate(pattern, -1)
        } catch (_: Exception) {}
    }

    private var lastCell: Vec3i = Vec3i.Zero

    fun accruePlaytime() {
        val now = System.currentTimeMillis()
        if (now > sessionStart) {
            save.data.stats.playtimeMs += now - sessionStart
        }
        sessionStart = now
    }

    fun showToast(msg: String) {
        toast = msg
        toastUntil = System.currentTimeMillis() + 2600
    }

    fun currentDay(): Long = System.currentTimeMillis() / 86400000L + TimeZone.getDefault().getOffset(0) / 86400000L

    fun startLevel(spec: LevelSpec, m: Mode) {
        level = spec
        mode = m
        logic = GameLogic(spec)
        moves = 0
        deaths = 0
        resets = 0
        hintShown = false
        challengeFailed = false
        elapsed = 0f
        roundStart = System.currentTimeMillis()
        solutionLength = 0
        scene.setLevel(spec, logic!!)
        val st = logic!!.state
        lastCell = st.player
        anim = PlayerAnim(st.player.toV3(), st.player.toV3())
        save.data.stats.plays++
        save.save()
        computeSolutionAsync(spec)
        introUntil = System.currentTimeMillis() + 3600
        maybeTutorialToast(spec)
    }

    private fun maybeTutorialToast(spec: LevelSpec) {
        val d = save.data
        if (mode != Mode.CAMPAIGN) return
        if (spec.phases > 1 && d.progress[spec.id] == null && seenTutorial.add("phase")) {
            showToast("NEW: PHASE BUTTON - flickering bridges turn solid in the active phase")
        }
        if (spec.portals.isNotEmpty() && d.progress[spec.id] == null && seenTutorial.add("portal")) {
            showToast("NEW: VIOLET RINGS are portals - step in to teleport")
        }
        if (spec.gravityPads.isNotEmpty() && d.progress[spec.id] == null && seenTutorial.add("pad")) {
            showToast("NEW: COLORED TILES rewrite gravity - the arrow shows the new DOWN")
        }
        if (spec.cubes.isNotEmpty() && d.progress[spec.id] == null && seenTutorial.add("cube")) {
            showToast("NEW: AMBER CUBES can be pushed - park them on switch plates")
        }
        if (spec.timedDoors.isNotEmpty() && d.progress[spec.id] == null && seenTutorial.add("timed")) {
            showToast("NEW: BLINKING GATES open and close - watch the rhythm")
        }
    }

    private fun computeSolutionAsync(spec: LevelSpec) {
        Thread {
            try {
                val r = Solver.solve(spec, maxNodes = 200_000)
                if (r.solvable) solutionLength = r.actions.size
                if (mode == Mode.CREATIVE_TEST) {
                    editor.lastTestOk = r.solvable && r.actions.size >= 2 && editor.pendingSaveSpec != null
                    activity.runOnUiThread {
                        showToast(if (editor.lastTestOk) "SOLVABLE IN " + r.actions.size + " MOVES - SAVE IS LIVE" else "UNSOLVABLE - FIX THE LEVEL")
                    }
                }
            } catch (t: Throwable) {
            }
        }.start()
    }

    fun restart() {
        val l = level ?: return
        if (mode != Mode.CHALLENGE || challenge?.kind != 2) {
            resets++
            challengeFailed = false
            logic = GameLogic(l)
            scene.setLevel(l, logic!!)
            val st = logic!!.state
            lastCell = st.player
            anim = PlayerAnim(st.player.toV3(), st.player.toV3())
        }
    }

    fun move(swipeDx: Float, swipeDy: Float) {
        val l = logic ?: return
        if (l.state.finished || l.state.dead) return
        if (challenge?.kind == 2 && challengeFailed) return
        val st = l.state.copy()
        val dir = scene.moveDirFromSwipe(st, swipeDx, swipeDy, l)
        val before = st.player
        val ok = l.tryMove(st, dir, ::onEvent)
        if (!ok) {
            audio.play(AudioEngine.DENY)
            return
        }
        commit(st, before)
    }

    fun togglePhase() {
        val l = logic ?: return
        val st = l.state.copy()
        l.togglePhase(st, ::onEvent)
        save.data.stats.phases++
        commit(st, st.player)
    }

    private fun commit(st: GameState, before: Vec3i) {
        val l = logic ?: return
        l.adopt(st)
        if (st.player != lastCell) {
            anim.jumpTo(st.player.toV3())
            lastCell = st.player
        }
        save.data.stats.moves += 1
        if (st.dead) {
            deaths++
            save.data.stats.deaths++
            audio.play(AudioEngine.DEATH)
            scene.shake(0.6f)
            buzz(longArrayOf(0, 90, 60, 50))
            spawnBurst(st.player.toV3() + Vec3(0.5f, 0.5f, 0.5f), Vec3(1f, 0.3f, 0.3f), 30)
        }
        if (st.finished) onWin()
        else if (st.dead && challenge?.kind == 2) challengeFailed = true
        if (st.dead) ui.refreshAfterDeath()
    }

    private fun onEvent(kind: String, cell: Vec3i) {
        when (kind) {
            "push" -> { save.data.stats.pushes++; audio.play(AudioEngine.PUSH); spawnBurst(cell.toV3() + Vec3(0.5f, 0.5f, 0.5f), Vec3(1f, 0.7f, 0.3f), 8); buzz(longArrayOf(0, 18)) }
            "portal" -> { save.data.stats.portals++; audio.play(AudioEngine.PORTAL); spawnBurst(cell.toV3() + Vec3(0.5f, 0.5f, 0.5f), SceneRenderer.PORTAL_COL, 26); buzz(longArrayOf(0, 30, 40, 20)) }
            "gravity" -> { save.data.stats.pads++; audio.play(AudioEngine.GRAVITY); spawnBurst(cell.toV3() + Vec3(0.5f, 0.5f, 0.5f), Vec3(0.3f, 0.9f, 1f), 18); buzz(longArrayOf(0, 35)) }
            "phase" -> { audio.play(AudioEngine.PHASE); buzz(longArrayOf(0, 22)); ui.onPhaseShifted() }
        }
    }

    fun spawnBurst(at: Vec3, col: Vec3, n: Int) {
        if (!save.data.settings.bloom) return
        for (i in 0 until n) {
            val a = (i.toFloat() / n) * 6.2832f
            val sp = 1.4f + (i % 3) * 0.7f
            scene.gl.spawnParticle(com.paradox.game.engine.Particle(
                pos = at,
                vel = Vec3(kotlin.math.cos(a) * sp, 1.2f + (i % 5) * 0.4f, kotlin.math.sin(a) * sp),
                color = col, life = 0.55f, maxLife = 0.55f, size = 30f, gravity = 3f
            ))
        }
    }

    fun onWin() {
        val l = level ?: return
        audio.play(AudioEngine.WIN)
        save.data.stats.wins++
        val st = logic!!.state
        if (challenge?.kind == 0) {
            if (st.moves > challenge!!.limit) {
                challengeFailed = true
                showToast("MOVE LIMIT EXCEEDED")
            }
        }
        when (mode) {
            Mode.CAMPAIGN -> {
                val stars = starsFor(st.moves)
                if (stars == 3) save.data.stats.perfects++
                save.data.markComplete(l.id, stars, st.moves)
                save.data.lastLevel = nextIdOf(l.id) ?: l.id
            }
            Mode.DAILY -> {
                save.data.dailyCompletedToday = true
                save.data.dailyLastDay = currentDay()
                val list = save.data.stats.dailyDone
                if (!list.contains(currentDay())) list.add(currentDay())
                Streak.compute(save.data)
            }
            Mode.ENDLESS -> {
                endlessStreak++
                if (endlessStreak > save.data.stats.endlessBest) save.data.stats.endlessBest = endlessStreak
            }
            else -> {}
        }
        checkAchievements()
        accruePlaytime()
        save.save()
        ui.onLevelComplete()
    }

    private fun nextIdOf(id: String): String? {
        val all = CampaignLevels.all()
        val idx = all.indexOfFirst { it.id == id }
        return if (idx >= 0 && idx + 1 < all.size) all[idx + 1].id else null
    }

    fun starsFor(moves: Int): Int {
        val opt = solutionLength
        return if (opt > 0 && moves <= opt) 3
        else if (opt > 0 && moves <= opt * 3 / 2) 2
        else 1
    }

    fun nextEndless() {
        val d = (2 + endlessStreak / 2).coerceAtMost(8)
        val spec = LevelGenerator.generate(System.nanoTime(), d, "ANOMALY " + (endlessStreak + 1))
        startLevel(spec, Mode.ENDLESS)
    }

    fun startDaily() {
        val day = currentDay()
        save.data.dailyCompletedToday = save.data.dailyLastDay == day
        val spec = LevelGenerator.generate(LevelGenerator.dailySeed(day), 5, "DAILY PARADOX")
        startLevel(spec, Mode.DAILY)
    }

    fun startChallenge(def: ChallengeDef) {
        challenge = def
        val spec = LevelGenerator.generate(90210L + def.difficulty * 7717L, def.difficulty, def.name)
        startLevel(spec, Mode.CHALLENGE)
    }

    fun checkAchievements() {
        val d = save.data
        val newly = ArrayList<Achievement>()
        fun has(id: String) = d.achievements.contains(id)
        fun unlock(id: String) {
            if (!has(id)) {
                d.achievements.add(id)
                Achievements.byId(id)?.let { newly.add(it) }
            }
        }
        val done = CampaignLevels.all().filter { (d.progress[it.id] ?: 0) > 0 }
        if (done.isNotEmpty()) unlock("first_light")
        for (w in 0..5) {
            val lvl = CampaignLevels.levelsOfWorld(w)
            if (lvl.isNotEmpty() && lvl.all { (d.progress[it.id] ?: 0) > 0 }) unlock("world_" + (w + 1))
        }
        val triples = d.progress.values.count { it >= 3 }
        if (triples >= 10) unlock("perfectionist")
        if (triples >= 20) unlock("archivist")
        if (done.size >= CampaignLevels.all().size) unlock("completionist")
        if (d.stats.portals >= 25) unlock("portal_pro")
        if (d.stats.pads >= 20) unlock("tilt_master")
        if (d.stats.pushes >= 15) unlock("puppeteer")
        if (d.stats.phases >= 20) unlock("shifty")
        if (d.stats.deaths >= 10) unlock("undead")
        if (d.stats.plays >= 50) unlock("casual_50")
        if (d.stats.dailyDone.isNotEmpty()) unlock("daily_1")
        if (d.stats.dailyStreakBest >= 3) unlock("daily_3")
        if (d.stats.endlessBest >= 5) unlock("endless_5")
        if (d.custom.isNotEmpty()) unlock("creator")
        if (d.stats.perfects > 0) unlock("under_par")
        if (newly.isNotEmpty()) {
            newestAchievement = newly.first()
            showToast("ACHIEVEMENT - " + newly.first().title)
            audio.play(AudioEngine.STAR)
            save.save()
        }
    }
}

object Streak {
    fun compute(d: com.paradox.game.data.SaveData) {
        var streak = 0
        var day = d.dailyLastDay
        while (d.stats.dailyDone.contains(day)) {
            streak++
            day--
        }
        if (streak > d.stats.dailyStreakBest) d.stats.dailyStreakBest = streak
    }
}
