package com.paradox.game.render

import android.content.Context
import android.graphics.BitmapFactory
import android.opengl.GLES20
import android.opengl.GLUtils
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
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

class SceneRenderer(val gl: GlGameRenderer, private val context: Context? = null) {

    var yaw = 0.7f
    var pitch = 0.95f
    var dist = 26f
    private var targetDist = 26f
    private var targetYaw = yaw
    private var targetPitch = pitch
    private var shakeAmp = 0f

    private var spec: LevelSpec? = null
    private var logic: GameLogic? = null

    private var tileMesh: Mesh? = null
    private var seamMesh: Mesh? = null
    private var wallMesh: Mesh? = null
    private var wallGlowMesh: Mesh? = null

    private var centerX = 0f
    private var centerY = 0f
    private var centerZ = 0f

    private var entities = ArrayList<Entity>()
    private var doorAnim = HashMap<Vec3i, Float>()
    private var switchAnim = HashMap<Vec3i, Float>()

    private var playerBody: Mesh? = null
    private var portalRing: Mesh? = null
    private var padArrow: Mesh? = null
    private var padFlat: Mesh? = null
    private var bgOcta1: Mesh? = null
    private var bgOcta2: Mesh? = null
    private var goalPlate: Mesh? = null
    private var pillarMesh: Mesh? = null
    private var pillarGlow: Mesh? = null

    private var texFloorD = 0
    private var texFloorN = 0
    private var texWallD = 0
    private var texWallN = 0
    private var texCubeD = 0
    private var texCubeN = 0
    private var texturesReady = false

    private var gfxEnabled = true
    var time = 0f

    private class Entity(val mesh: Mesh, val cell: Vec3i, val kind: Int, val param: Int, val rotY: Float = 0f, val glow: Mesh? = null)

    private val tints = arrayOf(
        arrayOf(Vec3(0.14f, 0.19f, 0.30f), Vec3(0.30f, 0.45f, 0.62f)),
        arrayOf(Vec3(0.12f, 0.21f, 0.34f), Vec3(0.25f, 0.55f, 0.70f)),
        arrayOf(Vec3(0.17f, 0.14f, 0.30f), Vec3(0.48f, 0.36f, 0.66f)),
        arrayOf(Vec3(0.22f, 0.13f, 0.32f), Vec3(0.60f, 0.30f, 0.72f)),
        arrayOf(Vec3(0.27f, 0.17f, 0.13f), Vec3(0.72f, 0.46f, 0.22f)),
        arrayOf(Vec3(0.11f, 0.24f, 0.26f), Vec3(0.22f, 0.68f, 0.62f))
    )

    fun setLevel(s: LevelSpec, l: GameLogic) {
        spec = s
        logic = l
        rebuild()
    }

    private fun rebuild() {
        val s = spec ?: return
        val tb = MeshBuilder()
        val sb = MeshBuilder()
        val wb = MeshBuilder()
        val wg = MeshBuilder()
        var minX = 999; var maxX = -999; var minY = 99; var maxY = -99; var minZ = 999; var maxZ = -999

        for (c in s.solids) {
            val cx = c.x + 0.5f; val cy = c.y + 0.5f; val cz = c.z + 0.5f
            if (c.y < 0) {
                tb.texCube(cx, cy - 0.03f, cz, 0.49f, 0.47f, 0.49f, Vec3(1f, 1f, 1f), Vec3(1f, 1f, 1f))
                val f = 0.44f
                sb.cube(cx, c.y + 0.94f, cz - f, 0.44f, 0.035f, 0.045f, SEAM, SEAM)
                sb.cube(cx, c.y + 0.94f, cz + f, 0.44f, 0.035f, 0.045f, SEAM, SEAM)
                sb.cube(cx - f, c.y + 0.94f, cz, 0.045f, 0.035f, 0.44f, SEAM, SEAM)
                sb.cube(cx + f, c.y + 0.94f, cz, 0.045f, 0.035f, 0.44f, SEAM, SEAM)
            } else {
                wb.texCube(cx, cy, cz, 0.48f, 0.5f, 0.48f, Vec3(1f, 1f, 1f), Vec3(1f, 1f, 1f))
                wg.cube(cx, c.y + 1.0f, cz, 0.40f, 0.03f, 0.40f, Vec3(1f, 1f, 1f), Vec3(1f, 1f, 1f))
                wg.cube(cx, c.y + 0.45f, cz, 0.42f, 0.015f, 0.42f, GLOWLINE, GLOWLINE)
            }
            if (c.x < minX) minX = c.x; if (c.x > maxX) maxX = c.x
            if (c.y < minY) minY = c.y; if (c.y > maxY) maxY = c.y
            if (c.z < minZ) minZ = c.z; if (c.z > maxZ) maxZ = c.z
        }
        centerX = (minX + maxX) / 2f + 0.5f
        centerY = (minY + maxY) / 2f + 0.5f
        centerZ = (minZ + maxZ) / 2f + 0.5f
        val span = maxOf(maxX - minX, maxZ - minZ).coerceAtLeast(6)
        targetDist = 14f + span * 1.4f

        tileMesh = tb.build()
        seamMesh = sb.build()
        wallMesh = wb.build()
        wallGlowMesh = wg.build()

        val plb = MeshBuilder()
        val plg = MeshBuilder()
        val prRadius = span / 2f + 6.5f
        val prCount = 18
        for (i in 0 until prCount) {
            val a = i.toFloat() / prCount * 6.2832f
            val px = centerX + prRadius * kotlin.math.cos(a)
            val pz = centerZ + prRadius * kotlin.math.sin(a)
            val ph = 5f + (i % 4) * 1.7f + (if (i % 2 == 0) 0.8f else 0f)
            plb.texCube(px, minY - 1f + ph / 2f, pz, 0.55f, ph / 2f, 0.55f, Vec3(1f, 1f, 1f), Vec3(1f, 1f, 1f), ph / 1.6f)
            plg.cube(px, minY - 1f + ph + 0.06f, pz, 0.30f, 0.05f, 0.30f, Vec3(0.4f, 0.9f, 1.1f), Vec3(0.3f, 0.7f, 0.9f))
        }
        pillarMesh = plb.build()
        pillarGlow = plg.build()

        val ents = ArrayList<Entity>()
        val pr = MeshBuilder()
        pr.ring(0f, 0.14f, 0f, 0.36f, 0.05f, PORTAL_COL, 10)
        pr.cube(0f, 0.14f, 0f, 0.30f, 0.06f, 0.30f, PORTAL_COL * 1.4f, PORTAL_COL)
        pr.cube(0f, 0.30f, 0f, 0.16f, 0.10f, 0.16f, PORTAL_COL * 1.6f, PORTAL_COL)
        portalRing = pr.build()

        val pa = MeshBuilder()
        pa.cube(0f, -0.30f, 0f, 0.46f, 0.08f, 0.46f, Vec3(0.7f, 0.7f, 0.75f), Vec3(0.5f, 0.5f, 0.55f))
        pa.chevronX(0f, -0.16f, 0f, 0.05f, Vec3(1f, 1f, 1f))
        pa.chevronX(-0.16f, -0.16f, 0f, 0.035f, Vec3(0.8f, 0.8f, 0.85f))
        padArrow = pa.build()

        val pf = MeshBuilder()
        pf.cube(0f, -0.30f, 0f, 0.46f, 0.08f, 0.46f, Vec3(0.7f, 0.7f, 0.75f), Vec3(0.5f, 0.5f, 0.55f))
        pf.ring(0f, -0.17f, 0f, 0.22f, 0.045f, Vec3(1f, 1f, 1f), 8)
        padFlat = pf.build()

        for (g in s.gravityPads) {
            val rot = when (g.gravity) {
                2 -> 0f; 3 -> 180f; 4 -> 90f; 5 -> 270f
                else -> 0f
            }
            val flat = g.gravity <= 1
            ents.add(Entity(if (flat) padFlat!! else padArrow!!, g.cell, KIND_PAD, g.gravity, rot))
        }

        for (p in s.portals) {
            if (p.size >= 6) {
                ents.add(Entity(portalRing!!, Vec3i(p[0], p[1], p[2]), KIND_PORTAL, 0))
                ents.add(Entity(portalRing!!, Vec3i(p[3], p[4], p[5]), KIND_PORTAL, 0))
            }
        }

        for (sw in s.switches) {
            val mb = MeshBuilder()
            mb.cube(0f, 0.06f, 0f, 0.36f, 0.14f, 0.36f, Vec3(0.35f, 0.38f, 0.5f), Vec3(0.28f, 0.30f, 0.42f))
            mb.cube(0f, 0.20f, 0f, 0.26f, 0.07f, 0.26f, Vec3(1f, 0.85f, 0.35f), Vec3(0.85f, 0.65f, 0.2f))
            mb.ring(0f, 0.30f, 0f, 0.14f, 0.03f, Vec3(1f, 0.9f, 0.4f), 6)
            ents.add(Entity(mb.build(), sw.cell, KIND_SWITCH, 0))
        }

        for (c in s.cubes) {
            val mb = MeshBuilder()
            mb.texCube(0f, 0f, 0f, 0.40f, 0.40f, 0.40f, Vec3(1f, 1f, 1f), Vec3(1f, 1f, 1f))
            val gb = MeshBuilder()
            gb.cube(0f, 0.41f, 0f, 0.28f, 0.03f, 0.28f, Vec3(1f, 0.9f, 0.5f), Vec3(1f, 0.85f, 0.4f))
            gb.cube(0f, 0f, 0f, 0.16f, 0.17f, 0.16f, CUBE_COL * 1.5f, CUBE_COL * 1.2f)
            ents.add(Entity(mb.build(), c, KIND_CUBE, 0, glow = gb.build()))
        }

        for (r in s.rotators) {
            val mb = MeshBuilder()
            for (t in r.tiles) {
                mb.cube(t.x + 0.5f - r.pivot.x - 0.5f, (t.y - r.pivot.y).toFloat(), t.z + 0.5f - r.pivot.z - 0.5f, 0.47f, 0.15f, 0.47f, Vec3(0.9f, 0.4f, 1f), Vec3(0.6f, 0.25f, 0.8f))
            }
            mb.octahedron(0f, 0.55f, 0f, 0.16f, Vec3(1f, 0.6f, 1f), Vec3(0.7f, 0.3f, 0.9f))
            ents.add(Entity(mb.build(), r.pivot, KIND_ROTATOR, 0))
        }

        val pb = MeshBuilder()
        pb.cube(0f, 0.0f, 0f, 0.26f, 0.26f, 0.22f, Vec3(0.95f, 1f, 1f), Vec3(0.55f, 0.65f, 0.75f))
        pb.cube(0f, 0.30f, 0f, 0.17f, 0.15f, 0.15f, Vec3(1f, 1f, 1f), Vec3(0.7f, 0.8f, 0.9f))
        pb.cube(0f, 0.32f, 0.14f, 0.10f, 0.05f, 0.045f, Vec3(0.2f, 1f, 1.4f), Vec3(0.1f, 0.9f, 1.2f))
        pb.cube(-0.30f, -0.05f, 0f, 0.06f, 0.16f, 0.10f, Vec3(0.4f, 0.9f, 1.2f), Vec3(0.3f, 0.7f, 0.9f))
        pb.cube(0.30f, -0.05f, 0f, 0.06f, 0.16f, 0.10f, Vec3(0.4f, 0.9f, 1.2f), Vec3(0.3f, 0.7f, 0.9f))
        pb.cube(0f, 0.50f, 0f, 0.02f, 0.09f, 0.02f, Vec3(0.6f, 1f, 1f), Vec3(0.5f, 0.9f, 1f))
        pb.cube(0f, 0.60f, 0f, 0.05f, 0.045f, 0.05f, Vec3(0.3f, 1.4f, 1.5f), Vec3(0.2f, 1.2f, 1.3f))
        pb.cube(0f, 0.02f, 0.21f, 0.07f, 0.07f, 0.035f, Vec3(0.3f, 1.3f, 1.6f), Vec3(0.2f, 1.0f, 1.3f))
        playerBody = pb.build()

        val gp = MeshBuilder()
        gp.cube(0f, -0.44f, 0f, 0.46f, 0.05f, 0.46f, Vec3(1f, 1f, 1f), Vec3(0.8f, 0.85f, 0.9f))
        gp.ring(0f, -0.36f, 0f, 0.34f, 0.04f, Vec3(1f, 1f, 1.2f), 8)
        goalPlate = gp.build()

        val o1 = MeshBuilder()
        o1.octahedron(0f, 0f, 0f, 1f, Vec3(0.35f, 0.30f, 0.6f), Vec3(0.22f, 0.18f, 0.42f))
        bgOcta1 = o1.build()
        val o2 = MeshBuilder()
        o2.octahedron(0f, 0f, 0f, 1f, Vec3(0.2f, 0.3f, 0.45f), Vec3(0.14f, 0.22f, 0.36f))
        bgOcta2 = o2.build()

        doorAnim = HashMap()
        switchAnim = HashMap()
        entities = ents
        yaw = targetYaw + 2.6f
        pitch = 1.25f
        dist = targetDist * 2.1f
        shakeAmp = 0f
    }

    fun orbitBy(dx: Float, dy: Float) {
        targetYaw += dx * 0.008f
        targetPitch = (targetPitch + dy * 0.006f).coerceIn(0.25f, 1.35f)
    }

    fun zoomBy(s: Float) {
        targetDist = (targetDist * (1f + s * 0.0015f)).coerceIn(9f, 60f)
    }

    fun screenDirFor(st: GameState, sx: Float, sy: Float, logic: GameLogic): Vec3i {
        val dirs = logic.allMoveDirs(st)
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

    fun shake(a: Float) { shakeAmp = maxOf(shakeAmp, a) }

    fun update(dt: Float, st: GameState, anim: PlayerAnim) {
        time += dt
        shakeAmp *= (1f - 4f * dt).coerceIn(0f, 1f)
        val l = (1f - Math.exp(-5.0 * dt)).toFloat()
        yaw += (targetYaw - yaw) * l
        pitch += (targetPitch - pitch) * l
        dist += (targetDist - dist) * l
        animateMaps(dt, st)
        if (gfxEnabled && st != null && !st.dead && !st.finished && kotlin.random.Random.nextFloat() < 0.10f) {
            spawnDust()
        }
        val cy = cos(yaw); val sy = sin(yaw)
        val cp = cos(pitch); val sp = sin(pitch)
        val shx = (kotlin.random.Random.nextFloat() - 0.5f) * shakeAmp
        val shy = (kotlin.random.Random.nextFloat() - 0.5f) * shakeAmp
        gl.camera.eye = Vec3(centerX + dist * cp * sy + shx, centerY + dist * sp + shy, centerZ + dist * cp * cy)
        gl.camera.target = Vec3(centerX, centerY - 1f, centerZ)
        draw(st, dt, anim)
    }

    private fun animateMaps(dt: Float, st: GameState) {
        val s = spec ?: return
        val wl = logic ?: return
        for (d in s.doors) {
            val cur = doorAnim.getOrPut(d.cell) { 1f }
            val target = if (wl.isSolid(st, d.cell)) 1f else 0f
            doorAnim[d.cell] = cur + (target - cur) * (1f - Math.exp(-10.0 * dt)).toFloat()
        }
        for (t in s.timedDoors) {
            val cur = doorAnim.getOrPut(t.cell) { 1f }
            val target = if (wl.isTimedClosed(st, t)) 1f else 0f
            doorAnim[t.cell] = cur + (target - cur) * (1f - Math.exp(-10.0 * dt)).toFloat()
        }
        for (sw in s.switches) {
            val cur = switchAnim.getOrPut(sw.cell) { 0f }
            val target = if (st.pressedChannels.contains(sw.channel)) 1f else 0f
            switchAnim[sw.cell] = cur + (target - cur) * (1f - Math.exp(-12.0 * dt)).toFloat()
        }
    }

    private fun spawnDust() {
        val r = 4f + kotlin.random.Random.nextFloat() * 14f
        val a = kotlin.random.Random.nextFloat() * 6.2832f
        gl.spawnParticle(Particle(
            pos = Vec3(centerX + r * cos(a), centerY - 4f + kotlin.random.Random.nextFloat() * 10f, centerZ + r * sin(a)),
            vel = Vec3(0.05f, 0.10f, 0.05f),
            color = Vec3(0.35f, 0.5f, 0.8f),
            life = 2.5f, maxLife = 2.5f, size = 14f, gravity = 0f
        ))
    }

    private fun loadTex(name: String): Int {
        val ctx = context ?: return 0
        return try {
            val tex = IntArray(1)
            GLES20.glGenTextures(1, tex, 0)
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, tex[0])
            ctx.assets.open("tex/$name").use { ins ->
                val bmp = BitmapFactory.decodeStream(ins)
                GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, bmp, 0)
                bmp.recycle()
            }
            GLES20.glGenerateMipmap(GLES20.GL_TEXTURE_2D)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR_MIPMAP_LINEAR)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_REPEAT)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_REPEAT)
            tex[0]
        } catch (e: Exception) {
            0
        }
    }

    private fun ensureTextures() {
        if (texturesReady || context == null) return
        texturesReady = true
        texFloorD = loadTex("floor_d.png")
        texFloorN = loadTex("floor_n.png")
        texWallD = loadTex("wall_d.png")
        texWallN = loadTex("wall_n.png")
        texCubeD = loadTex("cube_d.png")
        texCubeN = loadTex("cube_n.png")
    }

    private fun draw(st: GameState, dt: Float, anim: PlayerAnim) {
        val s = spec ?: return
        val wl = logic ?: return
        ensureTextures()
        val pal = tints[s.tint % tints.size]
        gl.beginScene()
        gl.clearColor = Vec3(0.012f, 0.016f, 0.04f)
        gl.fogNear = 26f
        gl.fogFar = 95f

        bgOcta1?.let {
            val m = Mat4.mul(Mat4.translation(Vec3(centerX, centerY + 9f, centerZ)), Mat4.mul(Mat4.rotationY(time * 0.06f), Mat4.scale(Vec3(26f, 26f, 26f))))
            gl.drawMesh(it, m, Vec3(1f, 1f, 1f), 0.06f)
        }
        bgOcta2?.let {
            val m = Mat4.mul(Mat4.translation(Vec3(centerX + 4f, centerY + 13f, centerZ - 5f)), Mat4.mul(Mat4.rotationY(-time * 0.045f), Mat4.scale(Vec3(38f, 38f, 38f))))
            gl.drawMesh(it, m, Vec3(1f, 1f, 1f), 0.04f)
        }

        tileMesh?.let { gl.drawMesh(it, Mat4.identity(), pal[0] * 4.5f, 0.0f, texFloorD, texFloorN) }
        seamMesh?.let { gl.drawMesh(it, Mat4.identity(), Vec3(1f, 1f, 1f), 0.85f) }
        wallMesh?.let { gl.drawMesh(it, Mat4.identity(), pal[1] * 3.8f, 0.05f, texWallD, texWallN) }
        wallGlowMesh?.let { gl.drawMesh(it, Mat4.identity(), Vec3(1f, 1f, 1f), 0.7f) }
        pillarMesh?.let { gl.drawMesh(it, Mat4.identity(), pal[1] * 3.0f, 0.02f, texWallD, texWallN) }
        pillarGlow?.let { gl.drawMesh(it, Mat4.identity(), Vec3(1f, 1f, 1f), 0.75f) }

        val shadowCol = Vec3(0.012f, 0.016f, 0.028f)
        if (!st.dead) {
            val pp = anim.visualPos()
            gl.drawCube(Vec3(pp.x + 0.5f, 0.035f, pp.z + 0.5f), Vec3(0.46f, 0.012f, 0.46f), 0f, shadowCol, 0f)
        }
        for (cb in st.cubes) {
            gl.drawCube(cb.toV3() + Vec3(0.5f, 0.03f, 0.5f), Vec3(0.40f, 0.012f, 0.40f), 0f, shadowCol, 0f)
        }
        gl.drawCube(s.goal.toV3() + Vec3(0.5f, 0.028f, 0.5f), Vec3(0.34f, 0.01f, 0.34f), 0f, shadowCol, 0f)

        for (b in s.bridges) {
            val solid = st.phase in b.solidPhases
            val flick = 0.5f + 0.5f * sin(time * 5f)
            val col = if (solid) Vec3(0.35f, 0.9f, 1.0f) else Vec3(0.22f, 0.26f, 0.38f)
            val h = if (solid) 0.16f else 0.05f
            gl.drawCube(b.cell.toV3() + Vec3(0.5f, 0.06f, 0.5f), Vec3(0.92f, h, 0.92f), 0f, col, if (solid) 0.55f + 0.25f * flick else 0.06f)
            if (solid) {
                gl.drawCube(b.cell.toV3() + Vec3(0.5f, 0.02f, 0.5f), Vec3(0.96f, 0.02f, 0.96f), 0f, Vec3(0.5f, 1f, 1.2f), 0.9f)
            }
        }

        for (e in entities) {
            when (e.kind) {
                KIND_PAD -> {
                    val model = Mat4.mul(Mat4.translation(e.cell.toV3() + Vec3(0.5f, 0f, 0.5f)), Mat4.rotationY(e.rotY * Math.PI.toFloat() / 180f))
                    gl.drawMesh(e.mesh, model, gravColor(e.param), 0.55f)
                }
                KIND_PORTAL -> {
                    val bob = 0.07f * sin(time * 2f + e.cell.x * 1.3f + e.cell.z)
                    gl.drawMesh(e.mesh, Mat4.mul(Mat4.translation(e.cell.toV3() + Vec3(0.5f, bob, 0.5f)), Mat4.rotationY(time * 1.2f)), PORTAL_COL, 0.95f)
                    if (gfxEnabled && kotlin.random.Random.nextFloat() < 0.25f) {
                        val a = kotlin.random.Random.nextFloat() * 6.2832f
                        gl.spawnParticle(Particle(
                            pos = e.cell.toV3() + Vec3(0.5f + 0.3f * cos(a), 0.2f, 0.5f + 0.3f * sin(a)),
                            vel = Vec3(0f, 0.9f, 0f), color = PORTAL_COL * 1.4f,
                            life = 0.6f, maxLife = 0.6f, size = 24f, gravity = 0f
                        ))
                    }
                }
                KIND_SWITCH -> {
                    val press = switchAnim[e.cell] ?: 0f
                    val base = Mat4.translation(e.cell.toV3() + Vec3(0.5f, 0f, 0.5f))
                    gl.drawMesh(e.mesh, base, Vec3(1f, 1f, 1f), 0.15f + 0.5f * press)
                    gl.drawCube(e.cell.toV3() + Vec3(0.5f, 0.26f - 0.10f * press, 0.5f), Vec3(0.24f, 0.05f, 0.24f), 0f, if (press > 0.5f) Vec3(0.4f, 1.4f, 0.5f) else Vec3(1f, 0.9f, 0.35f), 0.8f)
                }
                KIND_CUBE -> {
                    val idx = st.cubes.indexOf(e.cell)
                    if (idx >= 0) {
                        val bob = if (!st.dead) 0.02f * sin(time * 3f + idx) else 0f
                        val m = Mat4.translation(e.cell.toV3() + Vec3(0.5f, 0.42f + bob, 0.5f))
                        gl.drawMesh(e.mesh, m, Vec3(1.5f, 1.25f, 0.85f), 0.12f, texCubeD, texCubeN)
                        e.glow?.let { gl.drawMesh(it, m, Vec3(1f, 1f, 1f), 0.85f) }
                    }
                }
                KIND_ROTATOR -> {
                    val pivot = e.cell
                    val q = st.rotStates.getOrNull(spec!!.rotators.indexOfFirst { it.pivot == pivot }) ?: 0
                    val angle = -q * (Math.PI.toFloat() / 2f) + time * 0.3f
                    val m = Mat4.mul(Mat4.translation(pivot.toV3() + Vec3(0.5f, 0.5f, 0.5f)), Mat4.rotationY(angle))
                    gl.drawMesh(e.mesh, m, Vec3(1f, 1f, 1f), 0.3f)
                }
            }
        }

        for (d in s.doors) {
            val open = 1f - (doorAnim[d.cell] ?: 1f)
            val closed = wl.isSolid(st, d.cell)
            val col = if (closed) Vec3(1.0f, 0.30f, 0.22f) else Vec3(0.35f, 0.9f, 0.4f)
            val h = 0.5f * (doorAnim[d.cell] ?: 1f).coerceIn(0.06f, 1f)
            gl.drawCube(d.cell.toV3() + Vec3(0.5f, h, 0.5f), Vec3(0.9f, h, 0.9f), 0f, col, if (closed) 0.5f else 0.35f)
            if (open > 0.05f) gl.drawCube(d.cell.toV3() + Vec3(0.5f, 0.03f, 0.5f), Vec3(0.85f, 0.02f, 0.85f), 0f, Vec3(0.3f, 1.2f, 0.4f), 0.9f)
        }
        for (t in s.timedDoors) {
            val closed = wl.isTimedClosed(st, t)
            val h = (doorAnim[t.cell] ?: 1f).coerceIn(0.06f, 1f) * 0.5f
            val blink = 0.5f + 0.5f * sin(st.time / t.period * 6.2832f + t.offset * 6.2832f)
            val col = if (closed) Vec3(1.0f, 0.85f, 0.25f) else Vec3(0.4f, 0.55f, 0.2f)
            gl.drawCube(t.cell.toV3() + Vec3(0.5f, h, 0.5f), Vec3(0.9f, h, 0.9f), 0f, col, if (closed) 0.45f + 0.3f * blink else 0.15f)
        }

        goalPlate?.let {
            gl.drawMesh(it, Mat4.translation(s.goal.toV3() + Vec3(0.5f, 0f, 0.5f)), Vec3(1f, 1f, 1f), 0.9f)
        }
        val pulse = 0.5f + 0.5f * sin(time * 2.4f)
        gl.drawCube(s.goal.toV3() + Vec3(0.5f, 1.4f, 0.5f), Vec3(0.10f, 1.5f, 0.10f), 0f, Vec3(0.75f, 0.95f, 1.1f), 0.85f)
        val dm = Mat4.mul(Mat4.translation(s.goal.toV3() + Vec3(0.5f, 2.3f + 0.15f * pulse, 0.5f)), Mat4.rotationY(time * 1.5f))
        gl.drawMesh(bgOcta1 ?: return, Mat4.mul(Mat4.translation(s.goal.toV3() + Vec3(0.5f, 2.3f, 0.5f)), Mat4.mul(Mat4.rotationY(-time * 2.2f), Mat4.scale(Vec3(0.22f, 0.22f, 0.22f)))), Vec3(1.2f, 1.3f, 1.4f), 1.0f)
        if (gfxEnabled && kotlin.random.Random.nextFloat() < 0.5f) {
            val a = kotlin.random.Random.nextFloat() * 6.2832f
            gl.spawnParticle(Particle(
                pos = s.goal.toV3() + Vec3(0.5f + 0.5f * cos(a), 1.0f + kotlin.random.Random.nextFloat() * 2.2f, 0.5f + 0.5f * sin(a)),
                vel = Vec3(0f, 0.5f, 0f), color = Vec3(0.8f, 0.95f, 1f),
                life = 1.0f, maxLife = 1.0f, size = 26f, gravity = 0f
            ))
        }

        if (!st.dead) {
            val pp = anim.visualPos()
            val moving = anim.t < 1f
            val bob = 0.06f * sin(time * 5f) + (if (moving) 0.10f * sin(anim.t * 3.1416f) else 0f)
            val squash = if (moving) 1f + 0.10f * sin(anim.t * 3.1416f) else 1f
            val pm = Mat4.mul(
                Mat4.translation(Vec3(pp.x + 0.5f, pp.y + 0.55f * squash + bob, pp.z + 0.5f)),
                Mat4.mul(Mat4.rotationY(anim.spin), Mat4.scale(Vec3(1f, squash, 1f)))
            )
            playerBody?.let { gl.drawMesh(it, pm, Vec3(1f, 1f, 1f), 0.5f) }
            gl.drawCube(Vec3(pp.x + 0.5f, pp.y + 0.04f, pp.z + 0.5f), Vec3(0.42f, 0.02f, 0.42f), anim.spin, Vec3(0.3f, 0.8f, 1.1f), 0.9f)
        }
    }

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
        const val KIND_ROTATOR = 4
        val PORTAL_COL = Vec3(0.65f, 0.4f, 1.0f)
        val CUBE_COL = Vec3(1.0f, 0.72f, 0.25f)
        val CUBE_TOP = Vec3(1.0f, 0.8f, 0.4f)
        val SEAM = Vec3(0.25f, 0.7f, 1.0f)
        val GLOWLINE = Vec3(0.5f, 0.3f, 0.9f)
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
