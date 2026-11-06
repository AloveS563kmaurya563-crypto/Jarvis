package com.jarvis.assistant

import android.content.Context

object Prefs {
    const val DEFAULT_MODEL = "claude-sonnet-5-5"

    private fun sp(c: Context) = c.getSharedPreferences("jarvis", Context.MODE_PRIVATE)

    fun apiKey(c: Context): String = sp(c).getString("api_key", "") ?: ""
    fun model(c: Context): String =
        (sp(c).getString("model", DEFAULT_MODEL) ?: DEFAULT_MODEL).ifBlank { DEFAULT_MODEL }

    fun save(c: Context, key: String, model: String) {
        sp(c).edit().putString("api_key", key.trim()).putString("model", model.trim()).apply()
    }
}
