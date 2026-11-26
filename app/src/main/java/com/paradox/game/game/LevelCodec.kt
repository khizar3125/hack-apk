package com.paradox.game.game

import com.paradox.game.engine.Vec3i
import org.json.JSONArray
import org.json.JSONObject

object LevelCodec {

    fun encode(spec: LevelSpec): String {
        val o = JSONObject()
        o.put("id", spec.id)
        o.put("name", spec.name)
        o.put("par", spec.par)
        o.put("spawn", cell(spec.spawn))
        o.put("gravity", spec.startGravity)
        o.put("phases", spec.phases)
        o.put("goal", cell(spec.goal))
        o.put("hint", spec.hint)
        o.put("tint", spec.tint)
        o.put("solids", JSONArray(spec.solids.map { cell(it) }))
        o.put("portals", JSONArray(spec.portals.map { JSONArray(it) }))
        o.put("switches", JSONArray(spec.switches.map { JSONObject().put("cell", cell(it.cell)).put("ch", it.channel) }))
        o.put("doors", JSONArray(spec.doors.map { JSONObject().put("cell", cell(it.cell)).put("ch", it.channel).put("inv", it.invert) }))
        o.put("bridges", JSONArray(spec.bridges.map { JSONObject().put("cell", cell(it.cell)).put("ph", JSONArray(it.solidPhases.toList())) }))
        o.put("pads", JSONArray(spec.gravityPads.map { JSONObject().put("cell", cell(it.cell)).put("g", it.gravity) }))
        o.put("cubes", JSONArray(spec.cubes.map { cell(it) }))
        o.put("timed", JSONArray(spec.timedDoors.map { JSONObject().put("cell", cell(it.cell)).put("p", it.period.toDouble()).put("d", it.duty.toDouble()).put("o", it.offset.toDouble()) }))
        o.put("rotators", JSONArray(spec.rotators.map { JSONObject().put("p", cell(it.pivot)).put("t", JSONArray(it.tiles.map { c -> cell(c) })).put("ch", it.channel).put("n", it.angleCount) }))
        return o.toString()
    }

    fun decode(s: String): LevelSpec? {
        return try {
            val o = JSONObject(s)
            LevelSpec(
                o.optString("id", "custom"),
                o.optString("name", "CUSTOM"),
                6,
                o.optInt("par", 10),
                parseCell(o.getJSONObject("spawn")),
                o.optInt("gravity", 0),
                o.optInt("phases", 1),
                0,
                parseCells(o.optJSONArray("solids")),
                parseCell(o.getJSONObject("goal")),
                parsePortals(o.optJSONArray("portals")),
                parseList(o.optJSONArray("switches")) { j -> SwitchSpec(parseCell(j.getJSONArray("cell")), j.optString("ch", "a")) },
                parseList(o.optJSONArray("doors")) { j -> DoorSpec(parseCell(j.getJSONArray("cell")), j.optString("ch", "a"), j.optBoolean("inv", false)) },
                parseList(o.optJSONArray("bridges")) { j -> BridgeSpec(parseCell(j.getJSONArray("cell")), parseSet(j.optJSONArray("ph"))) },
                parseList(o.optJSONArray("pads")) { j -> GravityPadSpec(parseCell(j.getJSONArray("cell")), j.optInt("g", 0)) },
                parseCells(o.optJSONArray("cubes")),
                parseList(o.optJSONArray("rotators")) { j -> RotatorSpec(parseCell(j.getJSONArray("p")), parseCells(j.optJSONArray("t")), j.optString("ch", "r"), j.optInt("n", 4)) },
                parseList(o.optJSONArray("timed")) { j -> TimedDoorSpec(parseCell(j.getJSONArray("cell")), j.optDouble("p", 3.0).toFloat(), j.optDouble("d", 0.5).toFloat(), j.optDouble("o", 0.0).toFloat()) },
                o.optString("hint", ""),
                o.optInt("tint", 0)
            )
        } catch (_: Exception) {
            null
        }
    }

    private fun cell(v: Vec3i) = JSONArray(listOf(v.x, v.y, v.z))
    private fun parseCell(a: JSONArray) = Vec3i(a.optInt(0), a.optInt(1), a.optInt(2))
    private fun parseCell(o: JSONObject) = Vec3i(o.optInt("x", 0), o.optInt("y", 0), o.optInt("z", 0))
    private fun parseCells(a: JSONArray?): List<Vec3i> {
        val out = ArrayList<Vec3i>()
        if (a != null) for (i in 0 until a.length()) out.add(parseCell(a.optJSONArray(i) ?: JSONArray()))
        return out
    }
    private fun parsePortals(a: JSONArray?): List<List<Int>> {
        val out = ArrayList<List<Int>>()
        if (a != null) for (i in 0 until a.length()) {
            val p = a.optJSONArray(i) ?: continue
            val l = ArrayList<Int>()
            for (j in 0 until p.length()) l.add(p.optInt(j))
            out.add(l)
        }
        return out
    }
    private fun parseSet(a: JSONArray?): Set<Int> {
        val out = HashSet<Int>()
        if (a != null) for (i in 0 until a.length()) out.add(a.optInt(i))
        return out
    }
    private fun <T> parseList(a: JSONArray?, f: (JSONObject) -> T): List<T> {
        val out = ArrayList<T>()
        if (a != null) for (i in 0 until a.length()) {
            val jo = a.optJSONObject(i) ?: continue
            out.add(f(jo))
        }
        return out
    }
}
