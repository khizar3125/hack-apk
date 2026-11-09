package com.aimbot.runner.model

import org.json.JSONArray
import org.json.JSONObject

data class ScriptConfig(
    val name: String,
    val type: String,
    val game: String,
    val config: JSONObject?,
    val bypass: List<String>? = null
) {
    fun bypasses(): List<String> = bypass ?: emptyList()
}
