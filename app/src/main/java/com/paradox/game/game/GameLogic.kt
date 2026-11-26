package com.paradox.game.game
import com.paradox.game.engine.Vec3i

import kotlin.math.abs

class LevelSpec(
    val id: String,
    val name: String,
    val world: Int,
    val par: Int,
    val spawn: Vec3i,
    val startGravity: Int,
    val phases: Int,
    val startPhase: Int,
    val solids: List<Vec3i>,
    val goal: Vec3i,
    val portals: List<List<Int>>,
    val switches: List<SwitchSpec>,
    val doors: List<DoorSpec>,
    val bridges: List<BridgeSpec>,
    val gravityPads: List<GravityPadSpec>,
    val cubes: List<Vec3i>,
    val rotators: List<RotatorSpec>,
    val timedDoors: List<TimedDoorSpec>,
    val hint: String,
    val tint: Int = 0
) {
    val portalMap: Map<Vec3i, Vec3i> by lazy {
        val m = HashMap<Vec3i, Vec3i>()
        for (p in portals) {
            if (p.size >= 6) {
                val a = Vec3i(p[0], p[1], p[2]); val b = Vec3i(p[3], p[4], p[5])
                m[a] = b; m[b] = a
            }
        }
        m
    }
}

class SwitchSpec(val cell: Vec3i, val channel: String)
class DoorSpec(val cell: Vec3i, val channel: String, val invert: Boolean = false)
class BridgeSpec(val cell: Vec3i, val solidPhases: Set<Int>)
class GravityPadSpec(val cell: Vec3i, val gravity: Int)
class RotatorSpec(val pivot: Vec3i, val tiles: List<Vec3i>, val channel: String, val angleCount: Int = 4)
class TimedDoorSpec(val cell: Vec3i, val period: Float, val duty: Float, val offset: Float)

fun rotateCellY(c: Vec3i, pivot: Vec3i, quarterTurns: Int): Vec3i {
    val dx = c.x - pivot.x
    val dz = c.z - pivot.z
    var nx = dx
    var nz = dz
    var k = ((quarterTurns % 4) + 4) % 4
    while (k > 0) {
        val t = nx
        nx = -nz
        nz = t
        k--
    }
    return Vec3i(pivot.x + nx, c.y, pivot.z + nz)
}

class GameState(
    var player: Vec3i,
    var gravityIdx: Int,
    var phase: Int,
    var cubes: List<Vec3i>,
    var rotStates: IntArray,
    var time: Float,
    var moves: Int,
    var finished: Boolean,
    var dead: Boolean,
    val pressedChannels: MutableSet<String> = HashSet(),
    val lastChannelState: MutableMap<String, Boolean> = HashMap()
) {
    fun copy(): GameState = GameState(
        player, gravityIdx, phase, ArrayList(cubes), rotStates.copyOf(),
        time, moves, finished, dead, HashSet(pressedChannels), HashMap(lastChannelState)
    )
    fun key(rotCount: Int): Long {
        var h = 1469598103934665603L
        fun mix(v: Int) { h = (h xor (v.toLong() and 0xffffffffL)) * 1099511628211L }
        mix(player.x); mix(player.y); mix(player.z)
        mix(gravityIdx); mix(phase)
        for (c in cubes) { mix(c.x); mix(c.y); mix(c.z) }
        for (i in 0 until rotCount) mix(rotStates[i])
        return h
    }
}

open class GameLogic(val spec: LevelSpec) {
    var state: GameState = initial()
        protected set

    val gravityDirs = arrayOf(
        Vec3i(0, -1, 0), Vec3i(0, 1, 0), Vec3i(1, 0, 0), Vec3i(-1, 0, 0), Vec3i(0, 0, 1), Vec3i(0, 0, -1)
    )

    val rotTilesIdx: List<Int> = IntArray(0).indices.toList()

    private val rotMemberIdx: Map<Vec3i, Int> by lazy {
        val m = HashMap<Vec3i, Int>()
        for (i in spec.rotators.indices) {
            for (t in spec.rotators[i].tiles) { m.put(t, i); m.put(rotateCellY(t, spec.rotators[i].pivot, 1), i); m.put(rotateCellY(t, spec.rotators[i].pivot, 2), i); m.put(rotateCellY(t, spec.rotators[i].pivot, 3), i) }
        }
        m
    }

    protected val baseSolids: MutableSet<Vec3i> = HashSet()

    init { baseSolids.addAll(spec.solids) }

    open fun initial(): GameState {
        val st = GameState(
            spec.spawn, spec.startGravity, spec.startPhase,
            ArrayList(spec.cubes), IntArray(spec.rotators.size),
            0f, 0, false, false
        )
        refreshChannels(st, fireEdges = false)
        return st
    }

    fun reset() { state = initial() }

    fun adopt(st: GameState) { state = st }

    fun gravityOf(st: GameState): Vec3i = gravityDirs[st.gravityIdx]

    fun upOf(st: GameState): Vec3i = gravityDirs[st.gravityIdx] * -1

    fun isTimedClosed(st: GameState, d: TimedDoorSpec): Boolean {
        if ((st.time / d.period + d.offset) % 1f < d.duty) return false
        return true
    }

    private val bridgeIdx: Map<Vec3i, BridgeSpec> by lazy {
        val m = HashMap<Vec3i, BridgeSpec>()
        for (b in spec.bridges) m[b.cell] = b
        m
    }

    fun isSolid(st: GameState, c: Vec3i): Boolean {
        if (baseSolids.contains(c)) return true
        val b = bridgeIdx[c]
        if (b != null && st.phase in b.solidPhases) return true
        val r = rotMemberIdx[c]
        if (r != null && rotatorCells(r, st).contains(c)) return true
        if (st.cubes.contains(c)) return true
        for (d in spec.doors) {
            if (d.cell == c && doorClosed(st, d)) return true
        }
        for (t in spec.timedDoors) {
            if (t.cell == c && isTimedClosed(st, t)) return true
        }
        return false
    }

    private fun doorClosed(st: GameState, d: DoorSpec): Boolean {
        val active = st.pressedChannels.contains(d.channel)
        return if (d.invert) active else !active
    }

    open fun passable(st: GameState, c: Vec3i): Boolean = !isSolid(st, c)

    fun rotatorCells(i: Int, st: GameState): List<Vec3i> {
        val r = spec.rotators[i]
        val q = ((st.rotStates[i] % r.angleCount) + r.angleCount) % r.angleCount
        return r.tiles.map { rotateCellY(it, r.pivot, q) }
    }

    fun refreshChannels(st: GameState, fireEdges: Boolean = true): List<Int> {
        val occupied = HashSet<Vec3i>()
        occupied.add(st.player)
        occupied.addAll(st.cubes)
        val pressed = HashSet<String>()
        for (s in spec.switches) if (occupied.contains(s.cell)) pressed.add(s.channel)
        val edges = ArrayList<Int>()
        for (i in spec.rotators.indices) {
            val ch = spec.rotators[i].channel
            val now = pressed.contains(ch)
            val was = st.lastChannelState[verb(ch)] ?: false
            st.lastChannelState[verb(ch)] = now
            if (now && !was && fireEdges) edges.add(i)
        }
        st.pressedChannels.clear()
        st.pressedChannels.addAll(pressed)
        for (i in edges) st.rotStates[i] = (st.rotStates[i] + 1) % spec.rotators[i].angleCount
        return edges
    }

    private fun verb(ch: String) = "on:" + ch

    open fun moveCube(st: GameState, cubes: List<Vec3i>): List<Vec3i> {
        return cubes
    }

    fun fallCell(st: GameState, from: Vec3i, cap: Int = 40): Vec3i? {
        val g = gravityOf(st)
        var c = from
        var i = 0
        while (i < cap) {
            val below = c + g
            if (isSolid(st, below)) return c
            c = below
            i++
        }
        return null
    }

    open fun settleCube(st: GameState, idx: Int) {
        val c = st.cubes[idx]
        val landed = fallCell(st, c) ?: run { st.dead = true; return }
        st.cubes = st.cubes.mapIndexed { j, v -> if (j == idx) landed else v }
    }

    fun tryMove(st: GameState, dir: Vec3i, onEvent: ((String, Vec3i) -> Unit)? = null): Boolean {
        if (st.finished || st.dead) return false
        val g = gravityOf(st)
        if (dir.dot(g) != 0) return false
        val p = st.player
        val q = p + dir
        val cubeIdx = st.cubes.indexOf(q)

        if (cubeIdx >= 0) {
            val target = q + dir
            if (isSolid(st, target) || st.cubes.contains(target)) return false
            st.cubes = st.cubes.mapIndexed { i, v -> if (i == cubeIdx) target else v }
            onEvent?.invoke("push", q)
            st.player = q
            settleCube(st, cubeIdx)
        } else if (passable(st, q)) {
            st.player = q
        } else {
            val climb = q + upOf(st)
            if (!passable(st, climb)) return false
            st.player = climb
        }
        st.moves++
        resolve(st, onEvent)
        refreshChannels(st, fireEdges = true)
        return true
    }

    open fun resolve(st: GameState, onEvent: ((String, Vec3i) -> Unit)? = null) {
        var guard = 0
        var teleported = false
        while (guard < 64) {
            guard++
            if (st.dead || st.finished) return
            val pad0 = spec.gravityPads.firstOrNull { it.cell == st.player }
            if (pad0 != null && pad0.gravity != st.gravityIdx) {
                st.gravityIdx = pad0.gravity
                onEvent?.invoke("gravity", st.player)
                continue
            }
            val below = st.player + gravityOf(st)
            if (passable(st, below)) {
                st.player = below
                continue
            }
            val link = if (teleported) null else spec.portalMap[st.player]
            if (link != null) {
                st.player = link
                teleported = true
                onEvent?.invoke("portal", link)
                continue
            }
            break
        }
        if (guard >= 64) { st.dead = true; return }
        if (abs(st.player.x) + abs(st.player.y) + abs(st.player.z) > 48) st.dead = true
        if (fallCell(st, st.player, cap = 1) == null && passable(st, st.player + gravityOf(st))) { st.dead = true }
        if (st.player == spec.goal) st.finished = true
    }

    fun togglePhase(st: GameState, onEvent: ((String, Vec3i) -> Unit)?) {
        st.phase = (st.phase + 1) % maxOf(1, spec.phases)
        onEvent?.invoke("phase", st.player)
        settleCubesForPhase(st)
        resolve(st, onEvent)
        refreshChannels(st, fireEdges = true)
    }

    fun settleCubesForPhase(st: GameState) {
        for (i in st.cubes.indices) {
            if (passable(st, st.cubes[i] + gravityOf(st)) && !st.cubes.contains(st.cubes[i] + gravityOf(st))) settleCube(st, i)
        }
    }

    fun performAction(action: Int, onEvent: ((String, Vec3i) -> Unit)? = null): Boolean {
        if (action == 4) { togglePhase(state, onEvent); return true }
        if (action !in 0..3) return false
        val dirs = moveDirsFor(state)
        return tryMove(state, dirs[action], onEvent)
    }

    fun moveDirsFor(st: GameState): List<Vec3i> {
        val g = gravityOf(st)
        return if (g.y != 0) {
            listOf(Vec3i(0, 0, 1), Vec3i(0, 0, -1), Vec3i(1, 0, 0), Vec3i(-1, 0, 0))
        } else if (g.x != 0) {
            listOf(Vec3i(0, 1, 0), Vec3i(0, -1, 0), Vec3i(0, 0, 1), Vec3i(0, 0, -1))
        } else {
            listOf(Vec3i(1, 0, 0), Vec3i(-1, 0, 0), Vec3i(0, 1, 0), Vec3i(0, -1, 0))
        }
    }

    fun allMoveDirs(st: GameState): List<Vec3i> = moveDirsFor(st)
}
