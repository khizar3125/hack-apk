package com.paradox.game.render

import com.paradox.game.engine.GlGameRenderer
import com.paradox.game.engine.Mat4
import com.paradox.game.engine.Mesh
import com.paradox.game.engine.MeshBuilder
import com.paradox.game.engine.Particle
import com.paradox.game.engine.Vec3
import com.paradox.game.engine.Vec3i
import com.paradox.game.engine.smoothStep
import com.paradox.game.game.GameLogic
import com.paradox.game.game.GameState
import com.paradox.game.game.LevelSpec
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

class SceneRenderer(val gl: GlGameRenderer) {

    var yaw = 0.7f
    var pitch = 0.95f
    var dist = 26f
    private var targetDist = 26f
    private var targetYaw = yaw
    private var targetPitch = pitch

    private var spec: LevelSpec? = null
    private var logic: GameLogic? = null

    private var floorMesh: Mesh? = null
    private var wallMesh: Mesh? = null
    private var accentMesh: Mesh? = null

    private var center = Vec3.Zero
    private var extraEntities = ArrayList<EntityMesh>()
    var gfxEnabled = true

    private class EntityMesh(var mesh: Mesh, val cell: Vec3i, val kind: Int, var param: Int)

    private val tints = arrayOf(
        arrayOf(Vec3(0.16f, 0.20f, 0.30f), Vec3(0.30f, 0.45f, 0.62f)),
        arrayOf(Vec3(0.14f, 0.22f, 0.34f), Vec3(0.25f, 0.55f, 0.70f)),
        arrayOf(Vec3(0.18f, 0.16f, 0.30f), Vec3(0.48f, 0.36f, 0.66f)),
        arrayOf(Vec3(0.22f, 0.14f, 0.32f), Vec3(0.60f, 0.30f, 0.72f)),
        arrayOf(Vec3(0.26f, 0.18f, 0.14f), Vec3(0.72f, 0.46f, 0.22f)),
        arrayOf(Vec3(0.13f, 0.24f, 0.26f), Vec3(0.22f, 0.68f, 0.62f))
    )

    fun setLevel(s: LevelSpec, l: GameLogic) {
        spec = s
        logic = l
        yaw = 0.7f; pitch = 0.95f; dist = 26f
        targetYaw = yaw; targetPitch = pitch; targetDist = dist
        rebuild()
    }

    private fun rebuild() {
        val s = spec ?: return
        val fb = MeshBuilder()
        val wb = MeshBuilder()
        val ab = MeshBuilder()
        var minX = 999; var maxX = -999; var minY = 99; var maxY = -99; var minZ = 999; var maxZ = -999
        for (c in s.solids) {
            val m = if (c.y < 0) fb else wb
            val col = if (c.y < 0) Vec3(1f, 1f, 1f) else Vec3(0.85f, 0.85f, 1f)
            m.cube(c.x + 0.5f, c.y + 0.5f, c.z + 0.5f, 0.5f, 0.5f, 0.5f, col, col)
            if (c.x < minX) minX = c.x; if (c.x > maxX) maxX = c.x
            if (c.y < minY) minY = c.y; if (c.y > maxY) maxY = c.y
            if (c.z < minZ) minZ = c.z; if (c.z > maxZ) maxZ = c.z
        }
        centerX = (minX + maxX) / 2f + 0.5f
        centerY = (minY + maxY) / 2f + 0.5f
        centerZ = (minZ + maxZ) / 2f + 0.5f
        val span = maxOf(maxX - minX, maxZ - minZ).coerceAtLeast(6)
        targetDist = 14f + span * 1.4f
        dist = targetDist

        extraEntities = ArrayList()
        for (g in s.gravityPads) {
            val b = MeshBuilder()
            val gt = gravColor(g.gravity)
            b.cube(0f, -0.28f, 0f, 0.46f, 0.08f, 0.46f, gt, gt)
            b.cube(0f, -0.14f, 0f, 0.22f, 0.1f, 0.22f, gt * 1.3f, gt)
            extraEntities.add(EntityMesh(b.build(), g.cell, KIND_PAD, g.gravity))
        }
        for (p in s.portals) {
            if (p.size >= 6) {
                val a = Vec3i(p[0], p[1], p[2])
                val b2 = Vec3i(p[3], p[4], p[5])
                val mb = MeshBuilder()
                mb.cube(0f, 0.05f, 0f, 0.42f, 0.1f, 0.42f, PORTAL_COL * 0.6f, PORTAL_COL)
                mb.cube(0f, 0.28f, 0f, 0.3f, 0.14f, 0.3f, PORTAL_COL, PORTAL_COL)
                extraEntities.add(EntityMesh(mb.build(), a, KIND_PORTAL, 0))
                extraEntities.add(EntityMesh(mb.build(), b2, KIND_PORTAL, 0))
            }
        }
        for (sw in s.switches) {
            val mb = MeshBuilder()
            mb.cube(0f, 0.1f, 0f, 0.34f, 0.2f, 0.34f, Vec3(0.95f, 0.75f, 0.3f), Vec3(0.8f, 0.6f, 0.2f))
            extraEntities.add(EntityMesh(mb.build(), sw.cell, KIND_SWITCH, 0))
        }
        for (c in s.cubes) {
            val mb = MeshBuilder()
            mb.cube(0f, 0f, 0f, 0.42f, 0.42f, 0.42f, CUBE_COL, CUBE_COL * 0.8f)
            extraEntities.add(EntityMesh(mb.build(), c, KIND_CUBE, 0))
        }

        floorMesh = fb.build()
        wallMesh = wb.build()
        accentMesh = ab.build()
    }

    private var centerX = 0f
    private var centerY = 0f
    private var centerZ = 0f

    fun orbitBy(dx: Float, dy: Float) {
        targetYaw += dx * 0.008f
        targetPitch = (targetPitch + dy * 0.006f).coerceIn(0.25f, 1.35f)
    }

    fun zoomBy(s: Float) {
        targetDist = (targetDist * (1f + s * 0.0015f)).coerceIn(9f, 60f)
    }

    fun snapYaw(dir: Vec3i): Vec3i {
        val q = Math.round(targetYaw / (PI.toFloat() / 2f)) % 4
        return dir
    }

    fun screenDirFor(st: GameState, sx: Float, sy: Float, logic: GameLogic): Vec3i {
        val dirs = logic.allMoveDirs(st)
        val camUp = Vec3(sin(pitch) * sin(yaw), cos(pitch), sin(pitch) * cos(yaw))
        val fwd = Vec3(-sin(yaw) * cos(pitch), -sin(pitch), -cos(yaw) * cos(pitch))
        val right = fwd.cross(Vec3.Y).normalized()
        val up = right.cross(fwd)
        var best = dirs[0]; var bestScore = -2f
        for (d in dirs) {
            val v = d.toV3()
            val sxs = v.dot(right)
            val sys = v.dot(up)
            val len = maxOf(0.15f, kotlin.math.sqrt(sxs * sxs + sys * sys))
            val score = (sxs * sx + sys * sy) / len
            if (score > bestScore) { bestScore = score; best = d }
        }
        return best
    }

    fun moveDirFromSwipe(st: GameState, dx: Float, dy: Float, logic: GameLogic): Vec3i {
        val syUp = -dy
        val swiped = kotlin.math.sqrt(dx * dx + syUp * syUp)
        val sxn = dx / maxOf(swiped, 1f)
        val syn = syUp / maxOf(swiped, 1f)
        return screenDirFor(st, sxn, syn, logic)
    }

    fun update(dt: Float, st: GameState, anim: PlayerAnim) {
        val l = (1f - Math.exp(-9.0 * dt)).toFloat()
        yaw += (targetYaw - yaw) * l
        pitch += (targetPitch - pitch) * l
        dist += (targetDist - dist) * l
        val cy = cos(yaw); val sy = sin(yaw)
        val cp = cos(pitch); val sp = sin(pitch)
        val eye = Vec3(centerX + dist * cp * sy, centerY + dist * sp, centerZ + dist * cp * cy)
        gl.camera.eye = eye
        gl.camera.target = Vec3(centerX, centerY - 1f, centerZ)
        draw(st, dt, anim)
    }

    private fun draw(st: GameState, dt: Float, anim: PlayerAnim) {
        val s = spec ?: return
        val pal = tints[s.tint % tints.size]
        gl.beginScene()
        gl.clearColor = Vec3(0.012f, 0.016f, 0.04f)
        gl.drawMesh(floorMesh ?: return, Mat4.identity(), pal[0], 0.0f)

        val wl = logic ?: return

        for (w in (s.solids.filter { it.y >= 0 })) {
            var sz = Vec3(1f, 1f, 1f)
            var col = pal[1]
            var emi = 0.08f
            if (s.doors.any { it.cell == w }) {
                val closedNow = wl.isSolid(st, w)
                col = if (closedNow) Vec3(1.0f, 0.36f, 0.28f) else Vec3(1.0f, 0.62f, 0.20f) * 0.5f
                emi = if (closedNow) 0.5f else 0.15f
                sz = if (closedNow) Vec3(1f, 1f, 1f) else Vec3(1f, 0.16f, 1f)
            } else if (s.timedDoors.isNotEmpty() && s.timedDoors.any { it.cell == w }) {
                val closedNow = wl.isSolid(st, w)
                col = if (closedNow) Vec3(0.95f, 0.9f, 0.3f) else Vec3(0.4f, 0.4f, 0.16f)
                emi = if (closedNow) 0.55f else 0.1f
                sz = if (closedNow) Vec3(1f, 1f, 1f) else Vec3(1f, 0.16f, 1f)
            }
            gl.drawCube(w.toV3() + Vec3(0.5f, 0.5f, 0.5f), sz, 0f, col, emi)
        }

        for (b in s.bridges) {
            val solid = st.phase in b.solidPhases
            val col = if (solid) Vec3(0.4f, 0.95f, 1.0f) else Vec3(0.25f, 0.3f, 0.42f)
            val sz = if (solid) Vec3(1f, 0.9f, 1f) else Vec3(0.7f, 0.25f, 0.7f)
            val flick = if (solid) 0.15f * sin(System.nanoTime() / 300_000_000f).coerceAtLeast(-1f) + 0.4f else 0.0f
            gl.drawCube(b.cell.toV3() + Vec3(0.5f, 0.5f, 0.5f), sz, 0f, col, if (solid) 0.45f + flick else 0.05f)
        }

        for (r in s.rotators.indices) {
            val cells = wl.rotatorCells(r, st)
            for (c in cells) {
                gl.drawCube(c.toV3() + Vec3(0.5f, 0.5f, 0.5f), Vec3(0.94f, 0.3f, 0.94f), 0f, Vec3(0.85f, 0.35f, 1.0f), 0.3f)
            }
            gl.drawCube(s.rotators[r].pivot.toV3() + Vec3(0.5f, 1.1f, 0.5f), Vec3(0.2f, 0.2f, 0.2f), 0f, Vec3(1f, 0.5f, 1f), 0.8f)
        }

        val time = System.currentTimeMillis() / 1000f
        for (e in extraEntities) {
            when (e.kind) {
                KIND_PAD -> gl.drawMesh(e.mesh, Mat4.translation(e.cell.toV3()), gravColor(e.param), 0.5f)
                KIND_PORTAL -> {
                    val bob = 0.06f * sin(time * 2f + e.cell.x + e.cell.z)
                    gl.drawMesh(e.mesh, Mat4.translation(e.cell.toV3() + Vec3(0f, bob, 0f)), PORTAL_COL, 0.85f)
                }
                KIND_SWITCH -> {
                    val pressed = st.pressedChannels.contains(s.switches.firstOrNull { it.cell == e.cell }?.channel ?: "")
                    gl.drawMesh(e.mesh, Mat4.translation(e.cell.toV3()), if (pressed) Vec3(1f, 0.9f, 0.3f) else Vec3(0.6f, 0.5f, 0.25f), if (pressed) 0.7f else 0.2f)
                }
                KIND_CUBE -> {
                    val idx = st.cubes.indexOf(e.cell)
                    if (idx >= 0) gl.drawMesh(e.mesh, Mat4.translation(e.cell.toV3() + Vec3(0.5f, 0.5f, 0.5f)), CUBE_COL, 0.15f)
                }
            }
        }

        val goal = s.goal
        val pulse = 0.5f + 0.5f * sin(time * 2.2f)
        gl.drawCube(goal.toV3() + Vec3(0.5f, 1.1f, 0.5f), Vec3(0.55f, 0.12f, 0.55f), time, Vec3(1f, 1f, 1f), 1.0f)
        gl.drawCube(goal.toV3() + Vec3(0.5f, 0.5f + 1.2f + 0.1f * pulse, 0.5f), Vec3(0.18f, 0.18f, 0.18f), time * 1.4f, Vec3(0.8f, 1f, 1f), 1.0f)
        if (gfxEnabled && dt > 0f && kotlin.random.Random.nextFloat() < 0.35f) {
            val a = kotlin.random.Random.nextFloat() * 2f * PI.toFloat()
            gl.spawnParticle(Particle(
                pos = goal.toV3() + Vec3(0.5f + 0.6f * cos(a), 1.2f, 0.5f + 0.6f * sin(a)),
                vel = Vec3(0f, 0.7f, 0f), color = Vec3(0.7f, 0.95f, 1f),
                life = 0.8f, maxLife = 0.8f, size = 26f, gravity = 0.2f
            ))
        }

        val pp = anim.visualPos()
        val bob = 0.04f * sin(time * 6f)
        gl.drawCube(Vec3(pp.x + 0.5f, pp.y + 0.5f + bob, pp.z + 0.5f), Vec3(0.62f, 0.62f, 0.62f), anim.spin, Vec3(0.9f, 1f, 1f), 0.55f)
        gl.drawCube(Vec3(pp.x + 0.5f, pp.y + 1.0f + bob, pp.z + 0.5f), Vec3(0.28f, 0.28f, 0.28f), -anim.spin * 0.7f, Vec3(0.55f, 0.85f, 1f), 0.8f)
    }

    private fun baseSolidHas(s: LevelSpec, c: Vec3i): Boolean = s.solids.contains(c)

    private fun gravColor(g: Int): Vec3 = when (g) {
        0 -> Vec3(0.3f, 0.9f, 1f)
        1 -> Vec3(1f, 0.75f, 0.3f)
        2 -> Vec3(1f, 0.4f, 0.75f)
        3 -> Vec3(0.55f, 1f, 0.4f)
        4 -> Vec3(0.75f, 0.5f, 1f)
        else -> Vec3(0.35f, 1f, 0.65f)
    }

    companion object {
        const val KIND_PAD = 0
        const val KIND_PORTAL = 1
        const val KIND_SWITCH = 2
        const val KIND_CUBE = 3
        val PORTAL_COL = Vec3(0.65f, 0.4f, 1.0f)
        val CUBE_COL = Vec3(1.0f, 0.72f, 0.25f)
    }
}

class PlayerAnim(private var from: Vec3, private var to: Vec3, var t: Float = 1f) {
    var spin = 0f
    fun jumpTo(c: Vec3) {
        from = visualPos()
        to = c
        t = 0f
    }
    fun update(dt: Float) {
        t = min(1f, t + dt / 0.16f)
        spin += dt * (if (t < 1f) 7f else 0.6f)
    }
    fun visualPos(): Vec3 {
        val e = smoothStep(t.coerceIn(0f, 1f))
        return from.lerp(to, e)
    }
}
