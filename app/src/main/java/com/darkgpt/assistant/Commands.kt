package com.darkgpt.assistant

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.net.Uri

/** Local voice commands: app kholna, YouTube search, Google search. null = Gemini ko bhejo. */
object Commands {

    private val openWords = setOf(
        "open", "launch", "start", "kholo", "khol", "kholdo", "کھولو", "کھول", "اوپن", "چلاؤ"
    )
    private val searchWords = setOf(
        "search", "find", "play", "talash", "dhundo", "dhoondo", "chalao", "lagao", "dikhao", "show",
        "سرچ", "تلاش", "ڈھونڈو", "لگاؤ", "دکھاؤ", "پلے"
    )
    private val fillers = setOf(
        "do", "karo", "kar", "par", "pe", "on", "in", "for", "the", "a", "me", "mein", "please",
        "youtube", "google", "دو", "کرو", "کریں", "پر", "میں", "یوٹیوب", "گوگل", "پلیز", "کا", "کی", "کو", "پہ"
    )
    private val allStop = openWords + searchWords + fillers

    private val alias = mapOf(
        "واٹس ایپ" to "whatsapp", "واٹس اپ" to "whatsapp", "whats app" to "whatsapp",
        "کروم" to "chrome", "کیمرہ" to "camera", "کیمرا" to "camera",
        "سیٹنگز" to "settings", "سیٹنگ" to "settings", "فیس بک" to "facebook",
        "انسٹاگرام" to "instagram", "ٹک ٹاک" to "tiktok", "ٹیلیگرام" to "telegram",
        "کیلکولیٹر" to "calculator", "گیلری" to "gallery", "میپس" to "maps"
    )

    private fun tokens(s: String) = s.lowercase().split(" ", "،", ",", ".", "؟", "?").filter { it.isNotBlank() }
    private fun strip(t: List<String>, stop: Set<String>) = t.filter { it !in stop }.joinToString(" ")

    fun tryRun(ctx: Context, text: String): String? {
        val t = tokens(text)
        if (t.isEmpty()) return null
        if (t.any { it == "battery" || it == "بیٹری" }) return batteryText(ctx)
        val hasOpen = t.any { it in openWords }
        val hasSearch = t.any { it in searchWords }
        val hasYT = t.any { it == "youtube" || it == "یوٹیوب" }
        val hasGoogle = t.any { it == "google" || it == "گوگل" }

        if (hasYT) {
            val q = strip(t, allStop)
            if (q.isBlank()) {
                if (openApp(ctx, "youtube") == null) openUrl(ctx, "https://www.youtube.com")
                return Prefs.t(ctx, "یوٹیوب کھول رہا ہوں", "Opening YouTube")
            }
            openUrl(ctx, "https://www.youtube.com/results?search_query=" + Uri.encode(q))
            return Prefs.t(ctx, "یوٹیوب پر $q تلاش کر رہا ہوں", "Searching YouTube for $q")
        }

        if (hasOpen) {
            var name = strip(t, allStop)
            if (name.isBlank()) return null
            name = alias[name] ?: name
            val label = openApp(ctx, name)
            return if (label != null) Prefs.t(ctx, "$label کھول رہا ہوں", "Opening $label")
            else Prefs.t(ctx, "مجھے $name ایپ نہیں ملی", "I couldn't find the app $name")
        }

        if (hasGoogle || hasSearch) {
            val q = strip(t, allStop)
            if (q.isBlank()) return null
            openUrl(ctx, "https://www.google.com/search?q=" + Uri.encode(q))
            return Prefs.t(ctx, "گوگل پر $q تلاش کر رہا ہوں", "Searching Google for $q")
        }
        return null
    }

    /** App ka label return karta hai agar khul gayi, warna null */
    private fun openApp(ctx: Context, name: String): String? {
        val pm = ctx.packageManager
        val main = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val n = name.lowercase()
        val match = pm.queryIntentActivities(main, 0).firstOrNull {
            val lab = it.loadLabel(pm).toString().lowercase()
            lab == n || lab.contains(n) || (lab.length >= 3 && n.contains(lab))
        } ?: return null
        val launch = pm.getLaunchIntentForPackage(match.activityInfo.packageName) ?: return null
        launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        ctx.startActivity(launch)
        return match.loadLabel(pm).toString()
    }

    private fun openUrl(ctx: Context, url: String) {
        val i = Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        ctx.startActivity(i)
    }

    private fun batteryText(ctx: Context): String {
        val i = ctx.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level = i?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = i?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100
        val plugged = (i?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) ?: 0) != 0
        if (level < 0) return Prefs.t(ctx, "بیٹری کی معلومات نہیں ملیں", "I could not read the battery")
        val pct = level * 100 / scale
        val n = Prefs.userName(ctx).let { if (it.isBlank()) "" else "$it، " }
        val nEn = Prefs.userName(ctx).let { if (it.isBlank()) "" else "$it, " }
        return if (plugged) Prefs.t(ctx, "${n}بیٹری $pct فیصد ہے اور چارج ہو رہی ہے", "${nEn}battery is $pct percent and charging")
        else Prefs.t(ctx, "${n}بیٹری $pct فیصد ہے", "${nEn}battery is $pct percent")
    }
}
