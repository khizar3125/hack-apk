package com.aimbot.runner

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import java.util.concurrent.ConcurrentHashMap

/**
 * Single source of truth for which features are ON or OFF.
 * Both ScriptExecutor (feature loops) and FloatingIconService (toggles)
 * read/write here, so the floating menu can turn any feature on or off
 * while a script is running.
 */
object FeatureController {

    private const val TAG = "FeatureController"
    private const val PREFS = "feature_state"

    /** featureKey -> enabled. Key format: "gameName|scriptName|featureName" */
    private val state = ConcurrentHashMap<String, Boolean>()

    @Volatile
    private var prefs: SharedPreferences? = null

    fun init(context: Context) {
        prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        // Load persisted state.
        prefs!!.all.forEach { (k, v) ->
            if (v is Boolean) state[k] = v
        }
        Log.i(TAG, "FeatureController initialized with ${state.size} persisted features")
    }

    /** Is [feature] currently ON for [game]|[script]? Defaults to true if never set. */
    fun isOn(game: String, script: String, feature: String): Boolean {
        val key = "$game|$script|$feature"
        return state.getOrDefault(key, true)
    }

    /** Turn [feature] ON or OFF for [game]|[script] and persist it. */
    fun set(game: String, script: String, feature: String, enabled: Boolean) {
        val key = "$game|$script|$feature"
        state[key] = enabled
        prefs?.edit()?.putBoolean(key, enabled)?.apply()
        Log.i(TAG, "$key = $enabled")
    }

    /** Turn every feature for [game]|[script] OFF. */
    fun clearAll(game: String, script: String) {
        val prefix = "$game|$script|"
        state.keys.filter { it.startsWith(prefix) }.forEach { state.remove(it) }
        prefs?.edit()?.remove(prefix)?.apply() // best-effort; individual keys remain but are ignored
    }

    /** Snapshot of all feature keys currently ON. */
    fun activeKeys(): List<String> = state.entries.filter { it.value }.map { it.key }
}
