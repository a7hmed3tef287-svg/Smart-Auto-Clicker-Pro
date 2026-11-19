package com.example.smartautoclicker

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

object RuleStorage {
    private const val PREFS = "smart_clicker_settings"
    private const val RULES = "rules"
    private const val REPEAT_CYCLES = "repeat_cycles"

    fun loadRules(context: Context): List<ClickRule> {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(RULES, "[]") ?: "[]"
        return try {
            val array = JSONArray(raw)
            List(array.length()) { index ->
                val item = array.getJSONObject(index)
                ClickRule(
                    id = item.optInt("id", index + 1),
                    name = item.optString("name", "Point ${index + 1}"),
                    clickX = item.optInt("x", 300),
                    clickY = item.optInt("y", 300),
                    delayMs = item.optLong("delay", 500L).coerceAtLeast(50L)
                )
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun saveRules(context: Context, rules: List<ClickRule>) {
        val array = JSONArray()
        rules.forEach { rule ->
            array.put(
                JSONObject()
                    .put("id", rule.id)
                    .put("name", rule.name)
                    .put("x", rule.clickX)
                    .put("y", rule.clickY)
                    .put("delay", rule.delayMs)
            )
        }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(RULES, array.toString())
            .apply()
    }

    fun loadRepeatCycles(context: Context): Int =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getInt(REPEAT_CYCLES, 0)

    fun saveRepeatCycles(context: Context, cycles: Int) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putInt(REPEAT_CYCLES, cycles.coerceAtLeast(0))
            .apply()
    }
}
