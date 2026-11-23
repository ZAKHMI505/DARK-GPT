package com.darkgpt.assistant

import android.content.Context
import android.os.Handler
import android.os.Looper
import org.json.JSONArray
import org.json.JSONObject

/** Text leta hai: pehle local command, warna Gemini. Callback main thread par: (reply, localCommand) */
class Brain(private val ctx: Context) {
    private val main = Handler(Looper.getMainLooper())

    companion object {
        val history = ArrayList<Pair<String, String>>()
        private var loaded = false
    }

    init {
        if (!loaded) {
            loaded = true
            runCatching {
                val arr = JSONArray(Prefs.get(ctx, "history", "[]"))
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    history.add(o.getString("r") to o.getString("t"))
                }
            }
        }
    }

    fun memoryCount() = history.size

    fun clearMemory() {
        history.clear()
        save()
    }

    private fun save() {
        val arr = JSONArray()
        history.forEach { arr.put(JSONObject().put("r", it.first).put("t", it.second)) }
        Prefs.put(ctx, "history", arr.toString())
    }

    private fun trim() {
        while (history.size > 20) history.removeAt(0)
        while (history.isNotEmpty() && history[0].first != "user") history.removeAt(0)
    }

    fun handle(text: String, cb: (String, Boolean) -> Unit) {
        val local = runCatching { Commands.tryRun(ctx, text) }.getOrNull()
        if (local != null) {
            cb(local, true)
            return
        }
        val key = Prefs.apiKey(ctx)
        if (key.isBlank()) {
            cb(Prefs.t(ctx, "پہلے سیٹنگز میں جیمنائی API key ڈالیں۔", "Please add your Gemini API key in Settings first."), false)
            return
        }
        val memOn = Prefs.bool(ctx, "memory_on", true)
        if (memOn) {
            history.add("user" to text)
            trim()
        }
        val snapshot = if (memOn) history.toList() else listOf("user" to text)
        val model = Prefs.model(ctx)
        val lang = Prefs.lang(ctx)

        Thread {
            var result: Result<String> = runCatching { GeminiClient.ask(key, model, snapshot, lang) }
            val err = result.exceptionOrNull()
            if (err is GeminiError && (err.code == 404 || (err.message ?: "").contains("no longer available", true))) {
                ErrorLog.add(ctx, "Model $model failed: ${err.message}")
                val alt = runCatching { GeminiClient.autoSelect(key, model, snapshot, lang) }.getOrNull()
                if (alt != null) {
                    Prefs.put(ctx, "model", alt.first)
                    ErrorLog.add(ctx, "Model auto-switched: $model -> ${alt.first}")
                    result = Result.success(alt.second)
                }
            }
            main.post {
                result.onSuccess {
                    if (memOn) {
                        history.add("model" to it)
                        trim()
                        save()
                    }
                    cb(it, false)
                }.onFailure { e ->
                    if (memOn && history.isNotEmpty()) history.removeAt(history.size - 1)
                    ErrorLog.add(ctx, e.message ?: e.toString())
                    cb(GeminiClient.spoken(ctx, e), false)
                }
            }
        }.start()
    }
}
