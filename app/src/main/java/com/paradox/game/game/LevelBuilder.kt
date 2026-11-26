package com.paradox.game.game
import com.paradox.game.engine.Vec3i

class LevelBuilder(
    var id: String,
    var name: String,
    var world: Int,
    var tint: Int,
    var hint: String
) {
    val solids = ArrayList<Vec3i>()
    val portals = ArrayList<List<Int>>()
    val switches = ArrayList<SwitchSpec>()
    val doors = ArrayList<DoorSpec>()
    val bridges = ArrayList<BridgeSpec>()
    val pads = ArrayList<GravityPadSpec>()
    val cubes = ArrayList<Vec3i>()
    val timed = ArrayList<TimedDoorSpec>()
    var spawnCell = Vec3i(1, 0, 1)
    var goalCell = Vec3i(2, 0, 1)
    var startGravity = 0
    var phaseCount = 1
    var startPhase = 0
    var par = 10

    fun slab(x0: Int, x1: Int, y: Int, z0: Int, z1: Int) {
        for (x in x0..x1) for (z in z0..z1) solids.add(Vec3i(x, y, z))
    }

    fun block(x: Int, y: Int, z: Int) = solids.add(Vec3i(x, y, z))

    fun removeAt(x: Int, y: Int, z: Int) {
        solids.removeAll { it == Vec3i(x, y, z) }
    }

    fun setSpawn(x: Int, y: Int, z: Int) { spawnCell = Vec3i(x, y, z) }
    fun setGoal(x: Int, y: Int, z: Int) { goalCell = Vec3i(x, y, z) }
    fun spawnOn(x: Int, z: Int) { spawnCell = Vec3i(x, 0, z) }
    fun goalOn(x: Int, z: Int) { goalCell = Vec3i(x, 0, z) }

    fun portal(a: Vec3i, b: Vec3i) = portals.add(listOf(a.x, a.y, a.z, b.x, b.y, b.z))
    fun switch(cell: Vec3i, channel: String) = switches.add(SwitchSpec(cell, channel))
    fun door(cell: Vec3i, channel: String, invert: Boolean = false) = doors.add(DoorSpec(cell, channel, invert))
    fun bridgeAt(x: Int, y: Int, z: Int, phase: Int): BridgeSpec {
        if (phase + 1 > phaseCount) phaseCount = phase + 1
        val b = BridgeSpec(Vec3i(x, y, z), setOf(phase))
        bridges.add(b)
        return b
    }
    fun gravPad(cell: Vec3i, gravity: Int) = pads.add(GravityPadSpec(cell, gravity))
    fun cube(cell: Vec3i) = cubes.add(cell)
    fun timedDoor(cell: Vec3i, period: Float, duty: Float, offset: Float) = timed.add(TimedDoorSpec(cell, period, duty, offset))

    fun build(): LevelSpec = LevelSpec(
        id, name, world, par, spawnCell, startGravity, phaseCount, startPhase,
        solids.distinct(), goalCell, portals, switches, doors, bridges, pads,
        cubes.distinct(), rotatorsSafe(), timed, hint, tint
    )

    var rotators: ArrayList<RotatorSpec> = ArrayList()
    private fun rotatorsSafe(): List<RotatorSpec> = rotators

    fun rotator(pivot: Vec3i, tiles: List<Vec3i>, channel: String, angleCount: Int = 4) {
        rotators.add(RotatorSpec(pivot, tiles, channel, angleCount))
    }
}
