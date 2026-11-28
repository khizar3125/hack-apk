package com.khizar.camerapulse

import android.content.Context
import org.json.JSONArray
import org.json.JSONException

data class Reading(val bpm: Int, val timestamp: Long)

class HistoryStore(context: Context) {

    private val prefs = context.getSharedPreferences("camera_pulse_history", Context.MODE_PRIVATE)

    fun add(bpm: Int) {
        val all = all().toMutableList()
        all.add(0, Reading(bpm, System.currentTimeMillis()))
        while (all.size > MAX_ITEMS) all.removeAt(all.size - 1)
        save(all)
    }

    fun all(): List<Reading> {
        val raw = prefs.getString(KEY, null) ?: return emptyList()
        val list = ArrayList<Reading>()
        try {
            val array = JSONArray(raw)
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                list.add(Reading(obj.getInt("bpm"), obj.getLong("ts")))
            }
        } catch (_: JSONException) {
        }
        return list
    }

    fun clear() {
        prefs.edit().remove(KEY).apply()
    }

    private fun save(readings: List<Reading>) {
        val array = JSONArray()
        for (r in readings) {
            array.put(org.json.JSONObject().put("bpm", r.bpm).put("ts", r.timestamp))
        }
        prefs.edit().putString(KEY, array.toString()).apply()
    }

    companion object {
        private const val KEY = "readings"
        private const val MAX_ITEMS = 20
    }
}
