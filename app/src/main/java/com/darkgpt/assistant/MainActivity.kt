package com.darkgpt.assistant

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import org.json.JSONObject
import kotlin.math.roundToInt

class MainActivity : AppCompatActivity() {

    private lateinit var tvMicStatus: TextView
    private lateinit var tvHint: TextView
    private lateinit var tvWelcome: TextView
    private lateinit var tvTemp: TextView
    private lateinit var tvWTitle: TextView
    private lateinit var tvWSub: TextView
    private lateinit var tvLang: TextView
    private lateinit var tvMemSub: TextView
    private lateinit var brain: Brain

    private var autoTried = false
    private val busListener: () -> Unit = { updateUi() }

    private val permLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
            if (micGranted()) AssistantService.start(this)
            else {
                Bus.hint = "Microphone permission chahiye (Settings > Permissions)"
                updateUi()
            }
        }

    private fun micGranted() =
        ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        brain = Brain(this)

        tvMicStatus = findViewById(R.id.tvMicStatus)
        tvHint = findViewById(R.id.tvHint)
        tvWelcome = findViewById(R.id.tvWelcome)
        tvTemp = findViewById(R.id.tvTemp)
        tvWTitle = findViewById(R.id.tvWTitle)
        tvWSub = findViewById(R.id.tvWSub)
        tvLang = findViewById(R.id.tvLang)
        tvMemSub = findViewById(R.id.tvMemSub)

        // ON / OFF
        tvMicStatus.setOnClickListener {
            if (Bus.running) AssistantService.stop(this) else startAssistant()
        }
        // Orb aur Mic button: sunna shuru / bolna band
        findViewById<android.view.View>(R.id.orb).setOnClickListener { wake() }
        findViewById<android.view.View>(R.id.btnMic).setOnClickListener { wake() }

        val openSettings = { startActivity(Intent(this, SettingsActivity::class.java)) }
        val openChat = { startActivity(Intent(this, ChatActivity::class.java)) }
        findViewById<android.view.View>(R.id.btnSettings).setOnClickListener { openSettings() }
        findViewById<android.view.View>(R.id.navSettings).setOnClickListener { openSettings() }
        findViewById<android.view.View>(R.id.btnChat).setOnClickListener { openChat() }
        findViewById<android.view.View>(R.id.navChat).setOnClickListener { openChat() }
        findViewById<android.view.View>(R.id.navHome).setOnClickListener { updateUi() }
        findViewById<android.view.View>(R.id.navUpdates).setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle("DARK GPT v2.0")
                .setMessage("Naya version lene ke liye GitHub se naya APK download karke install karein.")
                .setPositiveButton("OK", null).show()
        }
        findViewById<android.view.View>(R.id.cardWeather).setOnClickListener { loadWeather() }
        findViewById<android.view.View>(R.id.cardScreen).setOnClickListener {
            Toast.makeText(this, "Screen sharing abhi shamil nahi hai", Toast.LENGTH_SHORT).show()
        }
        findViewById<android.view.View>(R.id.cardMemory).setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle("Memories")
                .setMessage("Yaad rakhe gaye messages: ${brain.memoryCount()}")
                .setPositiveButton("Clear") { _, _ -> brain.clearMemory(); updateUi() }
                .setNegativeButton("Close", null).show()
        }
        findViewById<android.view.View>(R.id.cardLang).setOnClickListener {
            Prefs.put(this, "lang", if (Prefs.lang(this) == "ur") "en" else "ur")
            updateUi()
        }
        loadWeather()
    }

    override fun onStart() {
        super.onStart()
        Bus.add(busListener)
        updateUi()
    }

    override fun onResume() {
        super.onResume()
        if (!autoTried) {
            autoTried = true
            if (Prefs.bool(this, "autostart", true) && !Bus.running) startAssistant()
        }
        updateUi()
    }

    override fun onStop() {
        Bus.remove(busListener)
        super.onStop()
    }

    private fun startAssistant() {
        val need = ArrayList<String>()
        if (!micGranted()) need.add(Manifest.permission.RECORD_AUDIO)
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED &&
            !Prefs.bool(this, "asked_notif", false)
        ) {
            need.add(Manifest.permission.POST_NOTIFICATIONS)
            Prefs.putBool(this, "asked_notif", true)
        }
        if (need.isNotEmpty()) permLauncher.launch(need.toTypedArray()) else AssistantService.start(this)
    }

    private fun wake() {
        if (!Bus.running) startAssistant() else AssistantService.send(this, AssistantService.ACTION_WAKE)
    }

    private fun updateUi() {
        val name = Prefs.userName(this)
        tvWelcome.text = if (name.isBlank()) "Welcome back" else "Welcome back, $name"
        tvLang.text = if (Prefs.lang(this) == "ur") "Urdu" else "English"
        tvMemSub.text = "${brain.memoryCount()} saved"

        if (!Bus.running) {
            tvMicStatus.text = "● ASSISTANT OFF"
            tvHint.text = if (Bus.hint.isNotBlank()) Bus.hint else "Tap to turn ON"
            return
        }
        tvMicStatus.text = when (Bus.state) {
            VState.IDLE -> "● MIC ON • READY"
            VState.LISTENING -> "● MIC ON • LISTENING"
            VState.PROCESSING -> "● PROCESSING…"
            VState.SPEAKING -> "● SPEAKING…"
            VState.PAUSED -> "● PAUSED"
            VState.EXECUTING -> "● EXECUTING…"
            VState.ERROR -> "● ERROR"
        }
        tvHint.text = if (Bus.hint.isNotBlank()) Bus.hint else "Tap Orb to talk • Tap pill to turn OFF"
    }

    // ---------- Weather ----------
    private fun loadWeather() {
        tvTemp.text = "--°C"
        tvWTitle.text = "Live Weather • Loading"
        val city = Prefs.city(this)
        Thread {
            try {
                val g = JSONObject(
                    Http.get("https://geocoding-api.open-meteo.com/v1/search?count=1&name=" + java.net.URLEncoder.encode(city, "UTF-8"))
                ).getJSONArray("results").getJSONObject(0)
                val lat = g.getDouble("latitude")
                val lon = g.getDouble("longitude")
                val w = JSONObject(
                    Http.get("https://api.open-meteo.com/v1/forecast?latitude=$lat&longitude=$lon&current_weather=true")
                ).getJSONObject("current_weather")
                val temp = w.getDouble("temperature").roundToInt()
                runOnUiThread {
                    tvTemp.text = "$temp°C"
                    tvWTitle.text = "Live Weather • $city"
                    tvWSub.text = "Tap to refresh live atmosphere"
                }
            } catch (e: Exception) {
                runOnUiThread {
                    tvTemp.text = "--°C"
                    tvWTitle.text = "Live Weather • Tap to retry"
                    tvWSub.text = "Internet ya City check karein"
                }
            }
        }.start()
    }
}
