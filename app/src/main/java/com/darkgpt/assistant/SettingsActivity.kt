package com.darkgpt.assistant

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.speech.tts.TextToSpeech
import android.view.Gravity
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat

class SettingsActivity : AppCompatActivity() {

    private lateinit var root: LinearLayout
    private var tts: TextToSpeech? = null
    private var voices: List<String> = emptyList()
    private val dp get() = resources.displayMetrics.density
    private fun px(v: Int) = (v * dp).toInt()

    private val blue = Color.parseColor("#1C6BFF")
    private val ink = Color.parseColor("#0B1B4D")
    private val muted = Color.parseColor("#6B7FA8")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val scroll = ScrollView(this).apply {
            setBackgroundColor(Color.WHITE)
            isFillViewport = true
        }
        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(px(18), px(20), px(18), px(30))
        }
        scroll.addView(root)
        setContentView(scroll)

        buildHeader()
        buildVoice()
        buildBackground()
        buildOrb()
        buildBattery()
        buildAi()
        buildPermissions()
        buildLogs()

        tts = TextToSpeech(this) { st ->
            if (st == TextToSpeech.SUCCESS) {
                voices = runCatching {
                    tts?.voices?.filter { it.locale.language == "ur" || it.locale.language == "en" }
                        ?.map { it.name }?.sorted()
                }.getOrNull() ?: emptyList()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        refreshPermissions()
    }

    override fun onDestroy() {
        tts?.shutdown()
        super.onDestroy()
    }

    // ------------------------------------------------------------ UI helpers
    private fun card(title: String): LinearLayout {
        val c = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = android.graphics.drawable.GradientDrawable().apply {
                setColor(Color.parseColor("#F6FAFF"))
                setStroke(px(1), Color.parseColor("#D6E4FF"))
                cornerRadius = px(20).toFloat()
            }
            setPadding(px(16), px(14), px(16), px(14))
        }
        val lp = LinearLayout.LayoutParams(-1, -2).apply { topMargin = px(14) }
        root.addView(c, lp)
        c.addView(TextView(this).apply {
            text = title
            setTextColor(blue)
            textSize = 17f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            setPadding(0, 0, 0, px(6))
        })
        return c
    }

    private fun label(parent: LinearLayout, t: String) {
        parent.addView(TextView(this).apply {
            text = t
            setTextColor(ink)
            textSize = 14f
            setPadding(0, px(10), 0, px(4))
        })
    }

    private fun row(parent: LinearLayout, t: String, sw: Boolean, key: String, def: Boolean, onChange: ((Boolean) -> Unit)? = null) {
        val h = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, px(6), 0, px(6))
        }
        h.addView(TextView(this).apply {
            text = t; setTextColor(ink); textSize = 15f
        }, LinearLayout.LayoutParams(0, -2, 1f))
        val s = Switch(this).apply {
            isChecked = Prefs.bool(this@SettingsActivity, key, def)
            setOnCheckedChangeListener { _, v ->
                Prefs.putBool(this@SettingsActivity, key, v)
                onChange?.invoke(v)
            }
        }
        h.addView(s)
        parent.addView(h)
    }

    private fun input(parent: LinearLayout, key: String, hint: String, def: String, secret: Boolean = false): EditText {
        val e = EditText(this).apply {
            setText(Prefs.get(this@SettingsActivity, key, def))
            this.hint = hint
            setSingleLine(true)
            setBackgroundColor(Color.WHITE)
            setPadding(px(12), px(10), px(12), px(10))
            if (secret) inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
            addTextChangedListener(object : android.text.TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
                override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) { Prefs.put(this@SettingsActivity, key, s?.toString().orEmpty()) }
                override fun afterTextChanged(s: android.text.Editable?) {}
            })
        }
        parent.addView(e, LinearLayout.LayoutParams(-1, -2))
        return e
    }

    private fun button(parent: LinearLayout, t: String, primary: Boolean = true, onClick: () -> Unit) {
        val b = Button(this).apply {
            text = t
            isAllCaps = false
            setTextColor(if (primary) Color.WHITE else blue)
            background = android.graphics.drawable.GradientDrawable().apply {
                cornerRadius = px(14).toFloat()
                if (primary) setColor(blue) else {
                    setColor(Color.parseColor("#EAF2FF")); setStroke(px(1), Color.parseColor("#A9C8FF"))
                }
            }
            setOnClickListener { onClick() }
        }
        parent.addView(b, LinearLayout.LayoutParams(-1, px(48)).apply { topMargin = px(8) })
    }

    private fun toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_SHORT).show()

    private fun openSys(i: Intent) {
        try { startActivity(i) } catch (e: Exception) { toast("Is phone mein yeh screen nahi mili") }
    }

    private fun seek(parent: LinearLayout, t: String, key: String, def: Int, min: Int, max: Int, suffix: String) {
        val cur = Prefs.int(this, key, def)
        val tv = TextView(this).apply { setTextColor(muted); textSize = 13f }
        fun upd(v: Int) { tv.text = "$t: $v$suffix" }
        upd(cur)
        parent.addView(tv)
        val sb = SeekBar(this).apply {
            this.max = max - min
            progress = cur - min
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(s: SeekBar, p: Int, fromUser: Boolean) {
                    val v = p + min
                    upd(v)
                    if (fromUser) Prefs.putInt(this@SettingsActivity, key, v)
                }
                override fun onStartTrackingTouch(s: SeekBar) {}
                override fun onStopTrackingTouch(s: SeekBar) {}
            })
        }
        parent.addView(sb)
    }

    private fun spinner(parent: LinearLayout, items: List<String>, selected: String, onPick: (String) -> Unit): Spinner {
        val sp = Spinner(this)
        sp.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, items)
        val idx = items.indexOf(selected).coerceAtLeast(0)
        sp.setSelection(idx, false)
        sp.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p: AdapterView<*>?, v: View?, pos: Int, id: Long) {
                onPick(items[pos])
            }
            override fun onNothingSelected(p: AdapterView<*>?) {}
        }
        parent.addView(sp, LinearLayout.LayoutParams(-1, -2))
        return sp
    }

    // ------------------------------------------------------------ sections
    private fun buildHeader() {
        val h = TextView(this).apply {
            text = "Settings"
            setTextColor(blue)
            textSize = 26f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
        }
        root.addView(h)
        val back = TextView(this).apply {
            text = "← Back"; setTextColor(muted); textSize = 14f; setPadding(0, px(4), 0, 0)
            setOnClickListener { finish() }
        }
        root.addView(back)
    }

    private fun buildVoice() {
        val c = card("A. Voice (Awaaz)")
        label(c, "Awaaz (Male voice chunein)")
        val names = listOf("Default") + voicesOrLoading()
        spinner(c, names, Prefs.get(this, "voice_asst", "Default")) {
            Prefs.put(this, "voice_asst", if (it == "Default") "" else it)
        }
        seek(c, "Speed", "rate", 95, 50, 150, "%")
        seek(c, "Pitch (kam = mardana)", "pitch", 85, 50, 120, "%")
        seek(c, "Volume", "vol", 100, 0, 100, "%")
        label(c, "Zubaan")
        spinner(c, listOf("Urdu", "English"), if (Prefs.lang(this) == "ur") "Urdu" else "English") {
            Prefs.put(this, "lang", if (it == "Urdu") "ur" else "en")
        }
        row(c, "Greeting bolein on start", false, "greet", true)
        row(c, "Bolte waqt rukawat (stop) sunein", false, "interrupt", true)
        button(c, "Test voice", true) {
            val t = Prefs.t(this, "السلام علیکم ${Prefs.userName(this)}۔ میں ڈارک جی پی ٹی ہوں۔", "Hello ${Prefs.userName(this)}. This is DARK GPT.")
            testSpeak(t)
        }
    }

    private fun voicesOrLoading(): List<String> = if (voices.isEmpty()) listOf("(voices load ho rahi hain, dobara kholein)") else voices

    private fun testSpeak(t: String) {
        val e = tts ?: return
        val name = Prefs.get(this, "voice_asst")
        e.voices?.firstOrNull { it.name == name }?.let { e.voice = it }
        e.setSpeechRate(Prefs.int(this, "rate", 95) / 100f)
        e.setPitch(Prefs.int(this, "pitch", 85) / 100f)
        e.speak(t, TextToSpeech.QUEUE_FLUSH, null, "test")
    }

    private fun buildBackground() {
        val c = card("B. Background Assistant")
        row(c, "Assistant ON / OFF (service)", false, "svc_on", Bus.running) {
            if (it) AssistantService.start(this) else AssistantService.stop(this)
        }
        row(c, "App khulte hi auto-start", true, "autostart", true)
        row(c, "Sirf tap par sunein (battery bachayein)", false, "listen_mode_tap", false) {
            Prefs.put(this, "listen_mode", if (it) "tap" else "always")
        }
        label(c, "Awaaz pakarne ki sensitivity: khamoshi kitni der baad command khatam ho")
        seek(c, "Pause", "pause_ds", 20, 5, 40, " (x0.1s)")
        row(c, "Orb ko reactive banayein (awaaz ke saath harkat)", true, "orb_reactive", true)
        button(c, "Battery optimization guidance kholein", false) {
            openSys(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:$packageName")))
        }
        c.addView(TextView(this).apply {
            text = "Android background restrictions: 'Unrestricted' battery aur Autostart allow karein, warna phone service band kar sakta hai."
            setTextColor(muted); textSize = 12f; setPadding(0, px(6), 0, 0)
        })
    }

    private fun buildOrb() {
        val c = card("C. Floating Orb")
        row(c, "Floating Orb ON / OFF", false, "orb_enabled", false) { on ->
            if (on && !Settings.canDrawOverlays(this)) {
                toast("Pehle 'Display over other apps' allow karein")
                openSys(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
            }
            AssistantService.send(this, AssistantService.ACTION_REFRESH_ORB)
        }
        seek(c, "Orb size", "orb_size", 100, 60, 180, " dp")
        seek(c, "Orb transparency", "orb_alpha", 100, 30, 100, "%")
        c.addView(TextView(this).apply {
            text = "Position: Orb ko screen par drag karein. Position save ho jati hai."
            setTextColor(muted); textSize = 12f; setPadding(0, px(6), 0, 0)
        })
        button(c, "Orb ki position reset karein", false) {
            Prefs.putInt(this, "orb_x", -1); Prefs.putInt(this, "orb_y", -1)
            AssistantService.send(this, AssistantService.ACTION_REFRESH_ORB)
            toast("Reset ho gaya")
        }
    }

    private fun buildBattery() {
        val c = card("D. Battery Assistant")
        row(c, "Battery FULL alert", true, "full_en", true)
        seek(c, "Full percentage", "full_pct", 100, 50, 100, "%")
        input(c, "full_msg", "Full message", Prefs.defaultMsg("full_msg"))
        row(c, "Full alert repeat karein", false, "full_repeat", false)
        seek(c, "Full repeat gap", "full_every", 5, 1, 60, " min")

        row(c, "LOW battery alert", true, "low_en", true)
        seek(c, "Low percentage", "low_pct", 20, 5, 50, "%")
        input(c, "low_msg", "Low message", Prefs.defaultMsg("low_msg"))
        row(c, "Low alert repeat karein", false, "low_repeat", false)
        seek(c, "Low repeat gap", "low_every", 5, 1, 60, " min")

        row(c, "Charging START alert", true, "chg_start_en", true)
        input(c, "chg_start_msg", "Charging start message", Prefs.defaultMsg("chg_start_msg"))
        row(c, "Charging STOP alert", true, "chg_stop_en", true)
        input(c, "chg_stop_msg", "Charging stop message", Prefs.defaultMsg("chg_stop_msg"))
        row(c, "Temperature warning", true, "temp_en", true)
        input(c, "temp_msg", "Temperature message", Prefs.defaultMsg("temp_msg"))

        seek(c, "Alert volume", "vol_alert", 100, 0, 100, "%")
        button(c, "Save battery messages", true) {
            Prefs.put(this, "full_msg", (c.findTextByHint("Full message")))
            toast("Saved")
        }
        button(c, "Test battery voice", false) {
            testSpeak(Prefs.fmt(this, Prefs.msg(this, "low_msg"), Prefs.int(this, "low_pct", 20)))
        }
        c.addView(TextView(this).apply {
            text = "Placeholders: {name} = aapka naam, {level} = battery %. Messages Save karne ke baad lagu honge."
            setTextColor(muted); textSize = 12f; setPadding(0, px(6), 0, 0)
        })
    }

    /** Battery messages save ke liye: EditText ko hint se dhoondta hai */
    private fun LinearLayout.findTextByHint(hint: String): String {
        val out = ArrayList<EditText>()
        fun walk(v: View) {
            if (v is EditText) out.add(v)
            if (v is LinearLayout) for (i in 0 until v.childCount) walk(v.getChildAt(i))
        }
        walk(this)
        return out.firstOrNull { it.hint?.toString() == hint }?.text?.toString().orEmpty()
    }

    private fun buildAi() {
        val c = card("E. AI (Gemini)")
        label(c, "Provider: Google Gemini")
        val key = input(c, "api_key", "AIza... (API key)", "", secret = true)
        label(c, "Model")
        val modelInput = input(c, "model", "gemini-3.8-flash", Prefs.model(this))
        val status = TextView(this).apply { setTextColor(muted); textSize = 13f; setPadding(0, px(6), 0, 0) }
        c.addView(status)
        row(c, "Yaad rakhein (memory)", true, "memory_on", true)

        button(c, "Save", true) {
            Prefs.put(this, "api_key", key.text.toString().trim())
            Prefs.put(this, "model", modelInput.text.toString().trim().ifBlank { Prefs.DEFAULT_MODEL })
            status.text = "Saved"
        }
        button(c, "Test connection", false) {
            Prefs.put(this, "api_key", key.text.toString().trim())
            val k = Prefs.apiKey(this)
            if (k.isBlank()) { status.text = "Pehle API key daalein"; return@button }
            status.text = "Testing…"
            Thread {
                val r = runCatching {
                    GeminiClient.ask(k, modelInput.text.toString().trim().ifBlank { Prefs.DEFAULT_MODEL },
                        listOf("user" to "Reply with one word: OK"), "en")
                }
                runOnUiThread {
                    r.onSuccess { status.text = "✅ Connection OK. Model: ${modelInput.text}" }
                        .onFailure { e ->
                            val m = if (e is GeminiError) GeminiClient.explain(e) else (e.message ?: "unknown")
                            ErrorLog.add(this, "Test: $m")
                            status.text = "❌ $m"
                        }
                }
            }.start()
        }
        button(c, "Model list dekhein (aapke account ke liye)", false) {
            val k = Prefs.apiKey(this)
            if (k.isBlank()) { status.text = "Pehle API key save karein"; return@button }
            status.text = "Models load ho rahe hain…"
            Thread {
                val r = runCatching { GeminiClient.rank(GeminiClient.listModels(k)) }
                runOnUiThread {
                    r.onSuccess { list ->
                        status.text = if (list.isEmpty()) "Koi model nahi mila" else "Models: " + list.take(8).joinToString(", ")
                    }.onFailure { e ->
                        status.text = "❌ " + (if (e is GeminiError) GeminiClient.explain(e) else e.message.orEmpty())
                    }
                }
            }.start()
        }
        c.addView(TextView(this).apply {
            text = "Note: 2.5 models naye accounts ke liye band hain. Model badalne ke liye upar naam likh kar Save dabayein, app ke dobara install ki zarurat nahi."
            setTextColor(muted); textSize = 12f; setPadding(0, px(6), 0, 0)
        })
    }

    private val permRows = ArrayList<Pair<TextView, () -> Unit>>()

    private fun buildPermissions() {
        val c = card("F. Permissions")
        permRows.clear()
        perm(c, "Microphone") {
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName"))
        } ; permLast(c)
        perm(c, "Notifications") {
            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
        }; permLast(c)
        perm(c, "Display over other apps (Orb)") {
            Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))
        }; permLast(c)
        perm(c, "Battery monitoring (automatic, no permission)") { Intent(Settings.ACTION_BATTERY_SAVER_SETTINGS) }; permLast(c)
        perm(c, "Install apps (unknown sources)") {
            Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:$packageName"))
        }; permLast(c)
        perm(c, "Speech recognition (Google app)") { Intent(Settings.ACTION_VOICE_INPUT_SETTINGS) }; permLast(c)
        refreshPermissions()
    }

    private var pendingRow: TextView? = null
    private fun perm(c: LinearLayout, name: String, intent: () -> Intent) {
        val h = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, px(6), 0, px(6))
        }
        val st = TextView(this).apply { textSize = 14f; setPadding(0, 0, px(8), 0) }
        val tv = TextView(this).apply { text = name; setTextColor(ink); textSize = 14f }
        h.addView(tv, LinearLayout.LayoutParams(0, -2, 1f))
        h.addView(st)
        val btn = TextView(this).apply {
            text = "Kholein"; setTextColor(blue); textSize = 13f; setPadding(px(10), px(4), 0, px(4))
            setOnClickListener { openSys(intent()) }
        }
        h.addView(btn)
        c.addView(h)
        pendingRow = st
    }

    private fun permLast(c: LinearLayout) {
        val st = pendingRow ?: return
        permRows.add(st to {})
    }

    /** Asli status: sirf system se poochh kar dikhate hain, jhoot nahi */
    private fun refreshPermissions() {
        if (permRows.isEmpty()) return
        val mic = ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        val notif = Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        val overlay = Settings.canDrawOverlays(this)
        val unknown = Build.VERSION.SDK_INT < 26 || packageManager.canRequestPackageInstalls()
        val speech = android.speech.SpeechRecognizer.isRecognitionAvailable(this)
        val states = listOf(
            if (mic) "✅ Di gayi" else "❌ Nahi di",
            if (notif) "✅ Di gayi" else "❌ Nahi di",
            if (overlay) "✅ Di gayi" else "❌ Nahi di",
            "✅ Automatic",
            if (unknown) "✅ Di gayi" else "❌ Nahi di",
            if (speech) "✅ Available" else "❌ Google app chahiye"
        )
        permRows.forEachIndexed { i, (tv, _) -> if (i < states.size) tv.text = states[i] }
    }

    private fun buildLogs() {
        val c = card("Error logs")
        val tv = TextView(this).apply {
            setTextColor(ink); textSize = 12f
            text = ErrorLog.all(this@SettingsActivity).ifBlank { "Koi error nahi" }
        }
        c.addView(tv)
        button(c, "Logs clear karein", false) {
            ErrorLog.clear(this); tv.text = "Koi error nahi"
        }
    }
}
