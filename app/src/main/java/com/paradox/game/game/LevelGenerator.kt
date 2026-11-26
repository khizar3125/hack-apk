package com.paradox.game.game
import com.paradox.game.engine.Vec3i

class Rng(val k: kotlin.random.Random) {
    constructor(seed: Long) : this(kotlin.random.Random(seed))
    fun next(): Float = k.nextFloat()
    fun nextInt(n: Int): Int = k.nextInt(n)
    fun chance(p: Double): Boolean = k.nextDouble() < p
    fun pick(list: List<Int>): Int = list[k.nextInt(list.size)]
    fun nextFloat(): Float = k.nextFloat()
}

object LevelGenerator {

    const val DAILY_SALT = 0x5EEDL

    fun dailySeed(dayEpoch: Long): Long = dayEpoch * 1_000_003L xor DAILY_SALT

    fun generate(seed: Long, difficulty: Int, name: String = "ANOMALY"): LevelSpec {
        for (attempt in 0 until 64) {
            val s = seed xor (attempt * 1_047_291L)
            val built = tryBuild(s, difficulty, name) ?: continue
            val res = Solver.solve(built, maxNodes = 150_000)
            if (res.solvable && res.actions.size >= 4) {
                return built.withPar(res.actions.size + res.actions.size / 3 + 2)
            }
        }
        return fallback(name)
    }

    fun fallback(name: String = "PARADOX"): LevelSpec {
        val b = LevelBuilder("gen", name, 0, 0, "Even the void repeats itself.")
        b.slab(0, 8, -1, 0, 0)
        b.slab(0, 8, -1, 6, 6)
        b.slab(0, 0, -1, 0, 6)
        b.slab(8, 8, -1, 0, 6)
        b.spawnOn(1, 1)
        b.goalOn(7, 5)
        b.par = 18
        return b.build()
    }

    private class Draft(val w: Int) {
        val path = ArrayList<Vec3i>()
        val inPath = HashSet<Vec3i>()
        val slabs = HashSet<Vec3i>()
        val walls = HashSet<Vec3i>()
        var spawn = Vec3i(1, 0, 1)
        var goal = Vec3i(1, 0, 1)
        val used = HashSet<Vec3i>()
        val portals = ArrayList<List<Int>>()
        val switches = ArrayList<SwitchSpec>()
        val doors = ArrayList<DoorSpec>()
        val bridges = ArrayList<BridgeSpec>()
        val pads = ArrayList<GravityPadSpec>()
        val cubes = ArrayList<Vec3i>()
        val timed = ArrayList<TimedDoorSpec>()
        var phases = 1
        var channelSeq = 0
        fun channel(): String { channelSeq++; return "g$channelSeq" }
    }

    private fun tryBuild(seed: Long, difficulty: Int, name: String): LevelSpec? {
        val d = difficulty.coerceIn(1, 10)
        val rng = Rng(seed)

        val w = 6 + (d / 2).coerceIn(0, 4)
        val t = Draft(w)

        var px = 1
        var pz = 1
        t.path.add(Vec3i(px, 0, pz))
        t.inPath.add(t.path[0])
        val targetLen = 8 + d * 2 + rng.nextInt(4)
        var guard = 0
        while (t.path.size < targetLen && guard < 1200) {
            guard++
            val nd = listOf(Vec3i(0, 0, 1), Vec3i(0, 0, -1), Vec3i(1, 0, 0), Vec3i(-1, 0, 0))[rng.nextInt(4)]
            val nx = px + nd.x
            val nz = pz + nd.z
            if (nx < 1 || nx > w - 2 || nz < 1 || nz > w - 2) continue
            val cell = Vec3i(nx, 0, nz)
            if (t.inPath.contains(cell)) continue
            t.inPath.add(cell)
            t.path.add(cell)
            px = nx; pz = nz
        }
        if (t.path.size < 7) return null
        t.spawn = t.path.first()
        t.goal = t.path.last()

        fun at(i: Int): Vec3i = t.path[((i % t.path.size) + t.path.size) % t.path.size]
        fun slab(x: Int, z: Int) = t.slabs.add(Vec3i(x, -1, z))
        fun slab(c: Vec3i) = t.slabs.add(Vec3i(c.x, -1, c.z))
        fun wall(c: Vec3i) = t.walls.add(Vec3i(c.x, 0, c.z))
        fun hasSlab(x: Int, z: Int) = t.slabs.contains(Vec3i(x, -1, z))
        fun open(x: Int, z: Int) = x in 1..w - 2 && z in 1..w - 2 && !t.inPath.contains(Vec3i(x, 0, z))

        for (c in t.path) slab(c)

        for (c in t.path) {
            for (dir in listOf(Vec3i(1, 0, 0), Vec3i(-1, 0, 0), Vec3i(0, 0, 1), Vec3i(0, 0, -1))) {
                val n = Vec3i(c.x + dir.x, 0, c.z + dir.z)
                if (!open(n.x, n.z) || t.used.contains(n)) continue
                if (hasSlab(n.x, n.z) || n.x < 1 || n.z < 1) continue
                if (t.walls.any { it.x == n.x && it.z == n.z }) continue
                if (rng.chance(0.36)) {
                    if (rng.chance(0.6)) slab(n) else wall(n)
                }
            }
        }

        val doorsCells = HashSet<Vec3i>()

        if (d >= 6 && rng.chance(0.55) && t.path.size > 8) {
            val i = 2 + rng.nextInt(t.path.size - 5)
            val j = i + 2 + rng.nextInt(3)
            val a = at(i)
            val b = at(j)
            if (j < t.path.size - 1) {
                t.portals.add(listOf(a.x, a.y, a.z, b.x, b.y, b.z))
                t.used.add(a); t.used.add(b)
            }
        }

        if (d >= 3 && rng.chance(0.8) && t.path.size > 5) {
            val gi = 2 + rng.nextInt(t.path.size - 4)
            val gap = at(gi)
            val slabCell = Vec3i(gap.x, -1, gap.z)
            if (!t.used.contains(gap) && gap != t.goal) {
                t.slabs.remove(slabCell)
                t.bridges.add(BridgeSpec(slabCell, setOf(1)))
                t.used.add(gap)
                t.phases = 2
            }
        }

        if (d >= 4 && rng.chance(0.6) && t.path.size > 5) {
            val ti = 2 + rng.nextInt(t.path.size - 4)
            val tc = at(ti)
            if (!t.used.contains(tc) && tc != t.goal) {
                t.timed.add(TimedDoorSpec(tc, 4.6f, 0.5f, rng.nextFloat()))
                t.used.add(tc)
            }
        }

        if (d >= 2 && t.path.size > 4) {
            val pi = 1 + rng.nextInt(t.path.size - 2)
            val doorCell = at(pi)
            if (!t.used.contains(doorCell) && doorCell != t.goal) {
                val ch = t.channel()
                var plate: Vec3i? = null
                for (sd in listOf(Vec3i(1, 0, 0), Vec3i(-1, 0, 0), Vec3i(0, 0, 1), Vec3i(0, 0, -1)).shuffled(rng.k)) {
                    val n = Vec3i(doorCell.x + sd.x, 0, doorCell.z + sd.z)
                    if (n.x in 1..w - 2 && n.z in 1..w - 2 && !t.inPath.contains(n) && n != doorCell) {
                        while (!hasSlab(n.x, n.z)) {
                            t.slabs.remove(Vec3i(n.x, 0, n.z))
                            slab(n)
                        }
                        plate = n
                        break
                    }
                }
                if (plate != null) {
                    t.doors.add(DoorSpec(doorCell, ch))
                    t.switches.add(SwitchSpec(plate, ch))
                    t.used.add(doorCell); t.used.add(plate)
                    doorsCells.add(doorCell)
                }
            }
        }

        if (d >= 5 && rng.chance(0.55) && t.path.size > 6) {
            val ci = 1 + rng.nextInt(t.path.size - 2)
            val cubeStart = at(ci)
            for (sd in listOf(Vec3i(1, 0, 0), Vec3i(-1, 0, 0), Vec3i(0, 0, 1), Vec3i(0, 0, -1)).shuffled(rng.k)) {
                val c1 = Vec3i(cubeStart.x + sd.x, 0, cubeStart.z + sd.z)
                val c2 = Vec3i(cubeStart.x + sd.x * 2, 0, cubeStart.z + sd.z * 2)
                if (c1.x in 1..w - 2 && c1.z in 1..w - 2 && c2.x in 1..w - 2 && c2.z in 1..w - 2 &&
                    !t.inPath.contains(c1) && !t.inPath.contains(c2) &&
                    !t.used.contains(c1) && !t.used.contains(c2)) {
                    slab(c1); slab(c2)
                    t.walls.remove(Vec3i(c1.x, 0, c1.z))
                    t.walls.remove(Vec3i(c1.x, 1, c1.z))
                    t.walls.remove(Vec3i(c2.x, 0, c2.z))
                    t.walls.remove(Vec3i(c2.x, 1, c2.z))
                    val ch = t.channel()
                    val di = ci + 2
                    if (di < t.path.size - 1) {
                        val doorCell = at(di)
                        if (!t.used.contains(doorCell) && doorCell != t.goal) {
                            t.cubes.add(c1)
                            t.switches.add(SwitchSpec(c2, ch))
                            t.doors.add(DoorSpec(doorCell, ch))
                            t.used.add(c1); t.used.add(c2); t.used.add(doorCell)
                        }
                    }
                    break
                }
            }
        }

        for (wl in ArrayList(t.walls)) {
            if (wl.y == 0 && t.inPath.contains(Vec3i(wl.x, 0, wl.z))) t.walls.remove(wl)
        }
        for (sl in ArrayList(t.slabs)) {
            if (t.walls.any { it.x == sl.x && it.z == sl.z && it.y == 0 }) { t.slabs.remove(sl) }
        }
        t.walls.removeAll { it.x == t.goal.x && it.z == t.goal.z }

        val b = LevelBuilder("gen", name, 0, 0, "The pattern resolves itself.")
        b.phaseCount = t.phases
        for (s in t.slabs) b.solids.add(s)
        for (wl in t.walls) b.solids.add(wl)
        b.spawnCell = t.spawn
        b.goalCell = t.goal
        b.portals.addAll(t.portals)
        b.switches.addAll(t.switches)
        b.doors.addAll(t.doors)
        b.bridges.addAll(t.bridges)
        b.pads.addAll(t.pads)
        b.cubes.addAll(t.cubes)
        b.timed.addAll(t.timed)
        return b.build()
    }
}

fun LevelSpec.withPar(newPar: Int): LevelSpec {
    val patched = LevelSpec(
        id, name, world, newPar, spawn, startGravity, phases, startPhase, solids, goal,
        portals, switches, doors, bridges, gravityPads, cubes, rotators, timedDoors, hint, tint
    )
    return patched
}
