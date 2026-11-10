package com.aimbot.runner.model

import org.json.JSONObject

data class GameConfig(
    val name: String,
    val packageName: String,
    val module: String,
    val offsets: JSONObject?,
    val anticheat: String? = null,
    val category: String? = null,
    val bypass: List<String>? = null,
    val features: List<String>? = null,
    val research: String? = null
) {
    fun bypasses(): List<String> = bypass ?: emptyList()
}
