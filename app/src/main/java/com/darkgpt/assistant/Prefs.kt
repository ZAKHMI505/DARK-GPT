package com.darkgpt.assistant

import android.content.Context
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object Prefs {
    const val DEFAULT_MODEL = "gemini-3.8-flash"

    private fun sp(c: Context) = c.getSharedPreferences("darkgpt", Context.MODE_PRIVATE)

    fun get(c: Context, k: String, d: String = ""): String = sp(c).getString(k, d) ?: d
    fun put(c: Context, k: String, v: String) = sp(c).edit().putString(k, v).apply()
    fun int(c: Context, k: String, d: Int): Int = sp(c).getInt(k, d)
    fun putInt(c: Context, k: String, v: Int) = sp(c).edit().putInt(k, v).apply()
    fun bool(c: Context, k: String, d: Boolean): Boolean = sp(c).getBoolean(k, d)
    fun putBool(c: Context, k: String, v: Boolean) = sp(c).edit().putBoolean(k, v).apply()

    fun apiKey(c: Context) = get(c, "api_key")
    fun model(c: Context) = get(c, "model", DEFAULT_MODEL).ifBlank { DEFAULT_MODEL }
    fun userName(c: Context) = get(c, "name")
    fun city(c: Context) = get(c, "city", "Lahore").ifBlank { "Lahore" }
    fun lang(c: Context) = get(c, "lang", "ur")
    fun localeTag(c: Context) = if (lang(c) == "ur") "ur-PK" else "en-US"

    /** Urdu ya English text, current language ke mutabiq */
    fun t(c: Context, ur: String, en: String) = if (lang(c) == "ur") ur else en

    private val defaultMsgs = mapOf(
        "full_msg" to "{name}، آپ کے موبائل کی بیٹری فل ہو چکی ہے۔ چارجر نکال دیں۔",
        "low_msg" to "{name}، آپ کے موبائل کی بیٹری {level} فیصد رہ گئی ہے۔ چارجنگ لگا دیں۔",
        "chg_start_msg" to "{name}، چارجنگ شروع ہو گئی ہے۔",
        "chg_stop_msg" to "{name}، چارجنگ بند ہو گئی ہے۔",
        "temp_msg" to "{name}، موبائل کا درجہ حرارت زیادہ ہے۔ اسے ٹھنڈی جگہ پر رکھیں۔"
    )

    fun defaultMsg(key: String) = defaultMsgs[key] ?: ""
    fun msg(c: Context, key: String) = get(c, key, defaultMsg(key)).ifBlank { defaultMsg(key) }

    fun fmt(c: Context, tpl: String, level: Int): String =
        tpl.replace("{name}", userName(c)).replace("{level}", level.toString())
            .trim().trimStart('،', ',', ' ')
}

object ErrorLog {
    fun add(c: Context, msg: String) {
        val line = SimpleDateFormat("dd/MM HH:mm", Locale.US).format(Date()) + "  " + msg.take(300)
        val old = Prefs.get(c, "errlog").split("\n").filter { it.isNotBlank() }
        Prefs.put(c, "errlog", (listOf(line) + old).take(30).joinToString("\n"))
    }

    fun all(c: Context) = Prefs.get(c, "errlog")
    fun clear(c: Context) = Prefs.put(c, "errlog", "")
}
