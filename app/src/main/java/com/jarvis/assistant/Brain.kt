package com.jarvis.assistant

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Talks to Claude, runs the tool-use loop, and remembers a short conversation. */
class Brain(private val ctx: Context) {

    private val history = JSONArray() // plain text turns only (user / assistant)

    private fun systemPrompt(): String {
        val now = SimpleDateFormat("EEEE, d MMMM yyyy, HH:mm", Locale.ENGLISH).format(Date())
        return """
You are Jarvis, a voice assistant that controls the user's Android phone. Current local time: $now.

Your replies are spoken aloud by text-to-speech: keep them very short (one or two sentences), with no markdown, no emojis, no lists, no URLs.
Reply in the language the user spoke (Hindi, Hinglish or English). Write Hindi in Devanagari script.

Use your tools to actually do things. For tasks inside other apps: open_app first, then read_screen, then tap_text / type_text / scroll, and read_screen again after each step to verify what happened. Screens need a moment to load, so if read_screen shows the old screen, try again.
Never say a task is done unless tool results confirm it; if something failed, say so briefly and what you tried.
Never make payments or purchases, and never type passwords, OTPs or PINs: stop and ask the user to do that final step. For deleting data or other hard-to-undo actions, ask for a spoken yes first.
For simple questions that need no phone action, just answer.
""".trimIndent()
    }

    fun ask(userText: String): String {
        val key = Prefs.apiKey(ctx)
        if (key.isBlank()) return "पहले ऐप में अपनी API key डालिए।"

        val msgs = JSONArray()
        for (i in 0 until history.length()) msgs.put(history.get(i))
        msgs.put(JSONObject().put("role", "user").put("content", userText))

        val tools = Tools.definitions()
        val system = systemPrompt()
        var finalText = ""

        for (step in 0 until 12) {
            val resp = ClaudeClient.call(key, Prefs.model(ctx), system, tools, msgs)
            val content = resp.getJSONArray("content")
            msgs.put(JSONObject().put("role", "assistant").put("content", content))

            val sb = StringBuilder()
            val results = JSONArray()
            for (i in 0 until content.length()) {
                val block = content.getJSONObject(i)
                when (block.getString("type")) {
                    "text" -> sb.append(block.getString("text"))
                    "tool_use" -> {
                        val name = block.getString("name")
                        val input = block.optJSONObject("input") ?: JSONObject()
                        JarvisLog.add("⚙ $name $input")
                        val out = try {
                            Tools.execute(ctx, name, input)
                        } catch (e: Exception) {
                            "Error: ${e.message}"
                        }
                        JarvisLog.add("   → ${out.take(200)}")
                        results.put(
                            JSONObject()
                                .put("type", "tool_result")
                                .put("tool_use_id", block.getString("id"))
                                .put("content", out)
                        )
                    }
                }
            }
            if (sb.isNotEmpty()) finalText = sb.toString().trim()
            if (results.length() == 0) break
            msgs.put(JSONObject().put("role", "user").put("content", results))
        }

        if (finalText.isBlank()) finalText = "ठीक है।"

        history.put(JSONObject().put("role", "user").put("content", userText))
        history.put(JSONObject().put("role", "assistant").put("content", finalText))
        while (history.length() > 12) history.remove(0)
        return finalText
    }
}
