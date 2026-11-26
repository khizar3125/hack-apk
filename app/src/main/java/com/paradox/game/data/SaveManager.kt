package com.paradox.game.data

import org.json.JSONObject
import java.io.File

class Settings(var music: Float = 0.7f, var sfx: Float = 0.8f, var bloom: Boolean = true, var hints: Boolean = true)

class Stats(
    var plays: Int = 0,
    var deaths: Int = 0,
    var moves: Int = 0,
    var portals: Int = 0,
    var pads: Int = 0,
    var pushes: Int = 0,
    var phases: Int = 0,
    var wins: Int = 0,
    var endlessBest: Int = 0,
    var perfects: Int = 0,
    var dailyStreakBest: Int = 0,
    val dailyDone: LinkedHashSet<Long> = LinkedHashSet()
)

class SaveData {
    val progress = LinkedHashMap<String, Int>()
    var lastLevel: String? = null
    val achievements = LinkedHashSet<String>()
    val custom = ArrayList<String>()
    val settings = Settings()
    val stats = Stats()
    var dailyLastDay: Long = -1
    var dailyCompletedToday = false

    fun starsTotal(): Int = progress.values.sum()
    fun completedCount(): Int = progress.size

    fun markComplete(id: String, stars: Int) {
        val old = progress[id] ?: 0
        if (stars > old) progress[id] = stars
    }

    fun toJson(): JSONObject {
        val o = JSONObject()
        val p = JSONObject()
        for ((k, v) in progress) p.put(k, v)
        o.put("progress", p)
        o.put("lastLevel", lastLevel ?: "")
        o.put("achievements", org.json.JSONArray(achievements.toList()))
        o.put("custom", org.json.JSONArray(custom))
        o.put("music", settings.music.toDouble())
        o.put("sfx", settings.sfx.toDouble())
        o.put("bloom", settings.bloom)
        o.put("hints", settings.hints)
        o.put("plays", stats.plays)
        o.put("deaths", stats.deaths)
        o.put("moves", stats.moves)
        o.put("portals", stats.portals)
        o.put("pads", stats.pads)
        o.put("pushes", stats.pushes)
        o.put("phases", stats.phases)
        o.put("wins", stats.wins)
        o.put("endlessBest", stats.endlessBest)
        o.put("perfects", stats.perfects)
        o.put("dailyStreakBest", stats.dailyStreakBest)
        o.put("dailyDone", org.json.JSONArray(stats.dailyDone.map { it }))
        o.put("dailyLastDay", dailyLastDay)
        o.put("dailyCompletedToday", dailyCompletedToday)
        return o
    }

    fun fromJson(o: JSONObject) {
        val p = o.optJSONObject("progress")
        if (p != null) for (k in p.keys()) progress[k] = p.optInt(k, 0)
        val ll = o.optString("lastLevel", "")
        if (ll.isNotEmpty()) lastLevel = ll
        val a = o.optJSONArray("achievements")
        if (a != null) for (i in 0 until a.length()) achievements.add(a.optString(i))
        val c = o.optJSONArray("custom")
        if (c != null) for (i in 0 until c.length()) custom.add(c.optString(i))
        settings.music = o.optDouble("music", 0.7).toFloat()
        settings.sfx = o.optDouble("sfx", 0.8).toFloat()
        settings.bloom = o.optBoolean("bloom", true)
        settings.hints = o.optBoolean("hints", true)
        stats.plays = o.optInt("plays", 0)
        stats.deaths = o.optInt("deaths", 0)
        stats.moves = o.optInt("moves", 0)
        stats.portals = o.optInt("portals", 0)
        stats.pads = o.optInt("pads", 0)
        stats.pushes = o.optInt("pushes", 0)
        stats.phases = o.optInt("phases", 0)
        stats.wins = o.optInt("wins", 0)
        stats.endlessBest = o.optInt("endlessBest", 0)
        stats.perfects = o.optInt("perfects", 0)
        stats.dailyStreakBest = o.optInt("dailyStreakBest", 0)
        val dd = o.optJSONArray("dailyDone")
        if (dd != null) for (i in 0 until dd.length()) stats.dailyDone.add(dd.optLong(i))
        dailyLastDay = o.optLong("dailyLastDay", -1)
        dailyCompletedToday = o.optBoolean("dailyCompletedToday", false)
    }
}

class SaveManager(filesDir: File) {
    private val file = File(filesDir, "paradox_save.json")
    val data = SaveData()

    fun load() {
        try {
            if (file.exists()) data.fromJson(JSONObject(file.readText()))
        } catch (_: Exception) {
        }
    }

    fun save() {
        try {
            file.writeText(data.toJson().toString())
        } catch (_: Exception) {
        }
    }
}
