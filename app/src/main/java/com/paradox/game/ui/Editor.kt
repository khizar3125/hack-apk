package com.paradox.game.ui

import com.paradox.game.engine.Vec3i
import com.paradox.game.game.BridgeSpec
import com.paradox.game.game.GravityPadSpec
import com.paradox.game.game.LevelSpec
import com.paradox.game.game.SwitchSpec
import com.paradox.game.game.DoorSpec

enum class EdTool(val label: String) {
    FLOOR("FLOOR"), WALL("WALL"), SPAWN("SPAWN"), GOAL("GOAL"), CUBE("CUBE"),
    PAD("PAD"), PORTAL("PORTAL"), SWITCH("SWITCH"), DOOR("DOOR"), BRIDGE("BRIDGE"), ERASE("ERASE")
}

class EditorCell(val x: Int, val z: Int, var type: Int, var param: Int = 0)

class Editor(private val app: com.paradox.game.app.GameApp) {
    val gridW = 14
    val gridH = 14

    var tool = EdTool.FLOOR
    val cells = LinkedHashMap<Long, EditorCell>()
    var spawnX = 0
    var spawnZ = 0
    var goalX = gridW - 1
    var goalZ = gridH - 1
    var portalA: Vec3i? = null
    var levelName = "MY PARADOX"
    var lastTestOk = false
    var pendingSaveSpec: LevelSpec? = null
    var padSeq = 0

    private var dispX = 0f
    private var dispY = 0f
    private var dispCell = 20f

    fun setDisplay(x: Float, y: Float, cellD: Float) {
        dispX = x; dispY = y; dispCell = cellD
    }

    fun key(x: Int, z: Int): Long = (x.toLong() shl 32) or z.toLong()

    fun cellAt(x: Int, z: Int): EditorCell? = cells[key(x, z)]

    fun gravityIndexOf(gx: Int, gz: Int): Int {
        val rel = (gx + gz) % 6
        return rel
    }

    fun tap(gx: Int, gz: Int) {
        if (gx < 0 || gz < 0 || gx >= gridW || gz >= gridH) return
        when (tool) {
            EdTool.FLOOR -> cells.put(key(gx, gz), EditorCell(gx, gz, 0))
            EdTool.WALL -> cells.put(key(gx, gz), EditorCell(gx, gz, 1))
            EdTool.SPAWN -> { spawnX = gx; spawnZ = gz }
            EdTool.GOAL -> { goalX = gx; goalZ = gz }
            EdTool.CUBE -> cells.put(key(gx, gz), EditorCell(gx, gz, 4))
            EdTool.PAD -> {
                val cell = EditorCell(gx, gz, 5, (padSeq) % 6)
                padSeq++
                cells.put(key(gx, gz), cell)
            }
            EdTool.PORTAL -> {
                val c = Vec3i(gx, 0, gz)
                val a = portalA
                if (a == null) {
                    portalA = c
                    cells.put(key(gx, gz), EditorCell(gx, gz, 6))
                    app.showToast("PORTAL A SET - TAP B")
                } else {
                    if (a.x == gx && a.z == gz) {
                        portalA = null
                        cells.remove(key(gx, gz))
                    } else {
                        cells.put(key(gx, gz), EditorCell(gx, gz, 6, 1))
                        portalA = null
                        app.showToast("PORTAL PAIRED")
                    }
                }
            }
            EdTool.SWITCH -> cells.put(key(gx, gz), EditorCell(gx, gz, 7))
            EdTool.DOOR -> cells.put(key(gx, gz), EditorCell(gx, gz, 2))
            EdTool.BRIDGE -> cells.put(key(gx, gz), EditorCell(gx, gz, 8))
            EdTool.ERASE -> { cells.remove(key(gx, gz)); portalA = null }
        }
        lastTestOk = false
    }

    fun fromTouch(px: Float, py: Float) {
        val gx = ((px - dispX) / dispCell).toInt()
        val gz = ((py - dispY) / dispCell).toInt()
        tap(gx, gz)
    }

    fun newLevel() {
        cells.clear()
        for (x in 2..8) for (z in 5..9) cells.put(key(x, z), EditorCell(x, z, 0))
        spawnX = 3; spawnZ = 7
        goalX = 7; goalZ = 7
        portalA = null
        lastTestOk = false
    }

    fun buildSpec(): LevelSpec? {
        val solids = ArrayList<Vec3i>()
        val cubes = ArrayList<Vec3i>()
        val pads = ArrayList<GravityPadSpec>()
        val switches = ArrayList<SwitchSpec>()
        val doors = ArrayList<DoorSpec>()
        val bridges = ArrayList<BridgeSpec>()
        var hasFloor = false
        for (c in cells.values) {
            when (c.type) {
                0 -> { solids.add(Vec3i(c.x, -1, c.z)); hasFloor = true }
                1 -> solids.add(Vec3i(c.x, 0, c.z))
                2 -> doors.add(DoorSpec(Vec3i(c.x, 0, c.z), "a"))
                4 -> cubes.add(Vec3i(c.x, 0, c.z))
                5 -> pads.add(GravityPadSpec(Vec3i(c.x, 0, c.z), c.param))
                7 -> switches.add(SwitchSpec(Vec3i(c.x, 0, c.z), "a"))
                8 -> bridges.add(BridgeSpec(Vec3i(c.x, -1, c.z), setOf(1)))
                else -> {}
            }
        }
        val spawn = Vec3i(spawnX, 0, spawnZ)
        val goal = Vec3i(goalX, 0, goalZ)
        if (!(cells[key(spawnX, spawnZ)]?.type == 0 && cells[key(goalX, goalZ)]?.type == 0)) return null
        if (!hasFloor) return null
        val phases = if (bridges.isNotEmpty()) 2 else 1
        var portals: List<List<Int>> = emptyList()
        val pa = ArrayList<Vec3i>()
        for (c in cells.values) if (c.type == 6) pa.add(Vec3i(c.x, 0, c.z))
        if (pa.size >= 2) portals = listOf(listOf(pa[0].x, pa[0].y, pa[0].z, pa[1].x, pa[1].y, pa[1].z))
        return LevelSpec(
            "user_" + System.currentTimeMillis(), levelName, 6, 10,
            spawn, 0, phases, 0,
            solids.distinct(), goal, portals, switches, doors, bridges, pads,
            cubes.distinct(), emptyList(), emptyList(),
            "A custom-crafted impossibility.", 5
        )
    }
}
