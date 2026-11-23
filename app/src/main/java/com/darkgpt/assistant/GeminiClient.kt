package com.darkgpt.assistant

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

class GeminiError(val code: Int, message: String) : Exception(message)

object GeminiClient {
    private const val BASE = "https://generativelanguage.googleapis.com/v1beta"

    private fun clean(model: String) = model.trim().removePrefix("models/")

    private fun open(url: String, key: String, post: Boolean): HttpURLConnection {
        val c = URL(url).openConnection() as HttpURLConnection
        c.requestMethod = if (post) "POST" else "GET"
        c.setRequestProperty("x-goog-api-key", key)
        if (post) {
            c.setRequestProperty("Content-Type", "application/json")
            c.doOutput = true
        }
        c.connectTimeout = 15000
        c.readTimeout = 60000
        return c
    }

    private fun read(c: HttpURLConnection): String {
        val code = c.responseCode
        val s = if (code in 200..299) c.inputStream else c.errorStream
        val txt = s?.bufferedReader()?.readText().orEmpty()
        if (code !in 200..299) {
            val msg = runCatching { JSONObject(txt).getJSONObject("error").getString("message") }
                .getOrDefault(txt.take(200))
            throw GeminiError(code, msg)
        }
        return txt
    }

    /** history: (role, text), role = "user" ya "model". Aakhri entry "user" honi chahiye. */
    fun ask(apiKey: String, model: String, history: List<Pair<String, String>>, lang: String): String {
        val system = if (lang == "ur")
            "You are DARK GPT, a friendly male personal voice assistant. Reply in Urdu script (اردو) unless the user speaks English. Keep answers short and clear because they are spoken aloud. Do not use markdown symbols."
        else
            "You are DARK GPT, a friendly male personal voice assistant. Reply in English. Keep answers short and clear because they are spoken aloud. Do not use markdown symbols."

        val contents = JSONArray()
        for ((role, text) in history) {
            contents.put(
                JSONObject().put("role", role)
                    .put("parts", JSONArray().put(JSONObject().put("text", text)))
            )
        }
        val body = JSONObject()
            .put("contents", contents)
            .put("systemInstruction", JSONObject().put("parts", JSONArray().put(JSONObject().put("text", system))))

        val c = open("$BASE/models/${clean(model)}:generateContent", apiKey, true)
        c.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
        val txt = read(c)

        val cands = JSONObject(txt).optJSONArray("candidates")
        val parts = cands?.optJSONObject(0)?.optJSONObject("content")?.optJSONArray("parts")
            ?: throw GeminiError(0, "Empty response (blocked by safety filter?)")
        val sb = StringBuilder()
        for (i in 0 until parts.length()) sb.append(parts.getJSONObject(i).optString("text"))
        return sb.toString().trim()
    }

    /** Is key ke liye jo models generateContent support karte hain (naam bina "models/" ke) */
    fun listModels(apiKey: String): List<String> {
        val txt = read(open("$BASE/models?pageSize=200", apiKey, false))
        val arr = JSONObject(txt).optJSONArray("models") ?: return emptyList()
        val out = ArrayList<String>()
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            val methods = o.optJSONArray("supportedGenerationMethods") ?: continue
            var ok = false
            for (j in 0 until methods.length()) if (methods.getString(j) == "generateContent") ok = true
            if (ok) out.add(o.getString("name").removePrefix("models/"))
        }
        return out
    }

    private val blocked = listOf(
        "embedding", "image", "tts", "live", "audio", "aqa", "robotics", "computer-use",
        "vision", "imagen", "veo", "transcribe", "omni", "native"
    )

    /** Text chat ke liye models ki tarteeb: flash > lite > pro, stable > preview, naya version pehle */
    fun rank(names: List<String>): List<String> {
        val re = Regex("gemini-(\\d+(?:\\.\\d+)?)")
        return names.filter { n -> n.startsWith("gemini-") && blocked.none { n.contains(it) } }
            .sortedWith(
                compareBy<String>(
                    { if (it.contains("lite")) 1 else if (it.contains("pro")) 2 else 0 },
                    { if (it.contains("preview") || it.contains("exp")) 1 else 0 },
                    { -(re.find(it)?.groupValues?.get(1)?.toDoubleOrNull() ?: 0.0) }
                )
            )
    }

    /** Chalne wala model dhoondhta hai (list me 2.5 models dikhte hain magar naye users ke liye fail hote hain) */
    fun autoSelect(
        key: String, exclude: String, history: List<Pair<String, String>>, lang: String
    ): Pair<String, String>? {
        val order = rank(listModels(key)).filter { it != clean(exclude) }.take(5)
        for (m in order) {
            try {
                return m to ask(key, m, history, lang)
            } catch (e: GeminiError) {
                continue
            }
        }
        return null
    }

    /** Settings ke liye technical wazahat */
    fun explain(e: GeminiError): String = when (e.code) {
        400 -> "400: Request ya API key ghalat hai. (${e.message})"
        401, 403 -> "${e.code}: API key invalid hai ya is par Gemini API on nahi. (${e.message})"
        404 -> "404: Yeh model aapke account ke liye maujood nahi. (${e.message})"
        429 -> "429: Quota/limit khatam. Thori der baad try karein. (${e.message})"
        in 500..599 -> "${e.code}: Google server masla. Baad mein try karein."
        else -> "${e.code}: ${e.message}"
    }

    /** Awaaz mein bolne ke liye chhota jumla */
    fun spoken(ctx: Context, e: Throwable): String {
        val code = (e as? GeminiError)?.code ?: -1
        return when {
            code == 400 || code == 401 || code == 403 -> Prefs.t(
                ctx, "جیمنائی کی API key ٹھیک نہیں ہے۔ سیٹنگز میں چیک کریں۔",
                "The Gemini API key is not valid. Please check Settings."
            )
            code == 404 -> Prefs.t(
                ctx, "جیمنائی ماڈل دستیاب نہیں ہے۔ سیٹنگز میں ماڈل تبدیل کریں۔",
                "The Gemini model is not available. Change the model in Settings."
            )
            code == 429 -> Prefs.t(
                ctx, "جیمنائی کی حد ختم ہو گئی ہے۔ تھوڑی دیر بعد کوشش کریں۔",
                "Gemini quota reached. Please try again later."
            )
            code in 500..599 -> Prefs.t(ctx, "گوگل سرور میں مسئلہ ہے۔", "Google server problem. Try again later.")
            e is IOException -> Prefs.t(ctx, "انٹرنیٹ نہیں ہے۔", "No internet connection.")
            else -> Prefs.t(ctx, "جیمنائی سے جواب نہیں ملا۔", "Could not get a reply from Gemini.")
        }
    }
}
