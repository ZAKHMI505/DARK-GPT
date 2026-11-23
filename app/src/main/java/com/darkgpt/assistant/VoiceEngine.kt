package com.darkgpt.assistant

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import androidx.core.content.ContextCompat

/**
 * Voice state machine:
 * IDLE -> LISTENING -> PROCESSING -> (EXECUTING) -> SPEAKING -> LISTENING ...
 * PAUSED = "wait" kaha gaya, sirf "listen" ka intezar.
 */
class VoiceEngine(private val ctx: Context) {

    var onState: (() -> Unit)? = null

    private val main = Handler(Looper.getMainLooper())
    private val brain = Brain(ctx)
    private var recognizer: SpeechRecognizer? = null
    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private var started = false
    private var paused = false
    private var speaking = false
    private var interruptMode = false
    private var reqId = 0
    private var uttCounter = 0
    private var currentUtt = ""
    private var errCount = 0
    private var pendingAlert: String? = null
    private val restart = Runnable { startListening() }

    private val stopWords = listOf(
        "stop", "chup ho jao", "chup ho jaao", "chup", "خاموش", "چپ ہو جاؤ", "چپ ہوجاؤ",
        "چپ", "رک جاؤ", "اسٹاپ", "سٹاپ"
    )
    private val waitWords = listOf("wait", "ruko", "ruk jao", "رکو", "انتظار", "ویٹ")
    private val listenWords = listOf("listen", "suno", "sunao", "سنو", "سنیں", "لسن", "سناؤ")
    private val cancelWords = listOf("cancel", "cancel karo", "کینسل", "کینسل کرو", "منسوخ")

    // ------------------------------------------------------------ lifecycle
    fun start() {
        if (started) return
        started = true
        paused = false
        setState(VState.IDLE, "")
        tts = TextToSpeech(ctx) { status ->
            main.post {
                if (!started) return@post
                ttsReady = status == TextToSpeech.SUCCESS
                if (ttsReady) {
                    tts?.setOnUtteranceProgressListener(utterListener)
                    if (Prefs.bool(ctx, "greet", true)) {
                        val n = Prefs.userName(ctx).let { if (it.isBlank()) "" else " $it" }
                        speak(
                            Prefs.t(ctx, "السلام علیکم$n، آپ کیا کرنا چاہتے ہیں؟", "Assalam-o-Alaikum$n, what would you like to do?"),
                            false
                        )
                    } else afterSpeech()
                } else {
                    ErrorLog.add(ctx, "TextToSpeech init failed")
                    setState(VState.ERROR, "Text-to-speech start nahi hua")
                    main.postDelayed(restart, 1500)
                }
            }
        }
    }

    fun stop() {
        started = false
        main.removeCallbacksAndMessages(null)
        recognizer?.destroy()
        recognizer = null
        tts?.stop()
        tts?.shutdown()
        tts = null
        ttsReady = false
        Bus.level = 0f
    }

    /** Orb tap / mic button: bol rahe hon to chup, warna sunna shuru */
    fun wake() {
        if (!started) return
        if (speaking) {
            doStop()
            return
        }
        if (Bus.state == VState.PROCESSING || Bus.state == VState.EXECUTING) return
        paused = false
        startListening()
    }

    fun stopTalk() {
        if (started) doStop()
    }

    /** Battery waghera ke alerts */
    fun announce(text: String) {
        if (!started || !ttsReady) return
        // Agar abhi bol ya sun rahe hain to alert queue mein rakhein, kisi ki baat na kaatein
        if (speaking || Bus.state == VState.PROCESSING || Bus.state == VState.EXECUTING) {
            pendingAlert = text
        } else speak(text, true)
    }

    // ------------------------------------------------------------ state
    private fun setState(s: VState, hint: String? = null) {
        Bus.state = s
        if (hint != null) Bus.hint = hint
        Bus.changed()
        onState?.invoke()
    }

    private fun hasMic() = ContextCompat.checkSelfPermission(ctx, Manifest.permission.RECORD_AUDIO) ==
        PackageManager.PERMISSION_GRANTED

    // ------------------------------------------------------------ listening
    private fun ensureRecognizer(): SpeechRecognizer? {
        if (!SpeechRecognizer.isRecognitionAvailable(ctx)) {
            setState(VState.ERROR, "Speech service nahi mili (Google app install/enable karein)")
            return null
        }
        return recognizer ?: SpeechRecognizer.createSpeechRecognizer(ctx).also {
            it.setRecognitionListener(listener)
            recognizer = it
        }
    }

    private fun recognizerIntent(): Intent {
        val pause = Prefs.int(ctx, "pause_ds", 20) * 100L
        return Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Prefs.localeTag(ctx))
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, pause)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, pause)
        }
    }

    private fun startListening() {
        main.removeCallbacks(restart)
        if (!started || speaking) return
        if (!hasMic()) {
            setState(VState.ERROR, "Microphone permission chahiye")
            return
        }
        val r = ensureRecognizer() ?: return
        interruptMode = false
        setState(if (paused) VState.PAUSED else VState.LISTENING)
        try {
            r.startListening(recognizerIntent())
        } catch (e: Exception) {
            ErrorLog.add(ctx, "listen: ${e.message}")
            main.postDelayed(restart, 1500)
        }
    }

    private fun listenForInterrupt() {
        if (!started || !speaking || !hasMic()) return
        val r = ensureRecognizer() ?: return
        interruptMode = true
        try {
            r.startListening(recognizerIntent())
        } catch (e: Exception) {
            interruptMode = false
        }
    }

    private fun first(b: Bundle?): String? =
        b?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()

    private val listener = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {}
        override fun onBeginningOfSpeech() {}
        override fun onRmsChanged(rmsdB: Float) {
            Bus.level = ((rmsdB + 2f) / 12f).coerceIn(0f, 1f)
        }
        override fun onBufferReceived(buffer: ByteArray?) {}
        override fun onEndOfSpeech() {
            Bus.level = 0f
        }
        override fun onEvent(eventType: Int, params: Bundle?) {}

        override fun onPartialResults(partialResults: Bundle?) {
            if (!interruptMode) return
            val t = first(partialResults) ?: return
            if (matches(norm(t), stopWords)) doStop()
        }

        override fun onResults(results: Bundle?) {
            val t = first(results)
            if (interruptMode) {
                if (!speaking) return
                if (t != null && matches(norm(t), stopWords)) doStop()
                else main.postDelayed({ if (speaking) listenForInterrupt() }, 300)
                return
            }
            errCount = 0
            if (t.isNullOrBlank()) {
                main.postDelayed(restart, 300)
                return
            }
            handleText(t)
        }

        override fun onError(error: Int) {
            if (interruptMode) {
                if (speaking) main.postDelayed({ if (speaking) listenForInterrupt() }, 800)
                return
            }
            if (error == SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS) {
                setState(VState.ERROR, "Microphone permission chahiye")
                return
            }
            if (error == SpeechRecognizer.ERROR_RECOGNIZER_BUSY) recognizer?.cancel()
            if (!started || speaking) return
            if (Bus.state != VState.LISTENING && Bus.state != VState.PAUSED) return
            errCount++
            if (error == SpeechRecognizer.ERROR_NETWORK || error == SpeechRecognizer.ERROR_NETWORK_TIMEOUT ||
                error == SpeechRecognizer.ERROR_SERVER
            ) {
                Bus.hint = "Speech ke liye internet chahiye"
                Bus.changed()
            }
            main.postDelayed(restart, minOf(600L * errCount, 5000L))
        }
    }

    // ------------------------------------------------------------ understanding
    private fun norm(s: String) = s.lowercase().replace(Regex("[.,!?،؟۔]"), " ")
        .replace(Regex("\\s+"), " ").trim()

    private fun matches(t: String, words: List<String>) =
        words.any { t == it || t.startsWith("$it ") || t.endsWith(" $it") }

    private fun handleText(raw: String) {
        val t = norm(raw)
        when {
            matches(t, stopWords) -> doStop()
            matches(t, cancelWords) -> doCancel()
            matches(t, listenWords) -> {
                paused = false
                Bus.hint = Prefs.t(ctx, "سن رہا ہوں", "Listening")
                startListening()
            }
            matches(t, waitWords) -> {
                paused = true
                Bus.hint = Prefs.t(ctx, "رکا ہوا ہوں۔ دوبارہ شروع کرنے کے لیے 'سنو' کہیں", "Paused. Say LISTEN to resume")
                startListening()
            }
            paused -> startListening()
            else -> runCommand(raw.trim())
        }
    }

    private fun runCommand(text: String) {
        val id = ++reqId
        setState(VState.PROCESSING, "You: $text")
        brain.handle(text) { reply, local ->
            if (id != reqId || !started) return@handle
            if (local) setState(VState.EXECUTING)
            Bus.hint = reply
            speak(reply, false)
        }
    }

    private fun doStop() {
        reqId++
        tts?.stop()
        speaking = false
        currentUtt = ""
        interruptMode = false
        recognizer?.cancel()
        Bus.hint = Prefs.t(ctx, "رک گیا", "Stopped")
        main.removeCallbacks(restart)
        main.postDelayed({ afterSpeech() }, 300)
    }

    private fun doCancel() {
        reqId++
        tts?.stop()
        speaking = false
        currentUtt = ""
        Bus.hint = Prefs.t(ctx, "منسوخ کر دیا", "Cancelled")
        startListening()
    }

    // ------------------------------------------------------------ speaking
    private fun speak(text: String, alert: Boolean) {
        val e = tts
        if (!started) return
        if (e == null || !ttsReady) {
            afterSpeech()
            return
        }
        main.removeCallbacks(restart)
        recognizer?.cancel()
        interruptMode = false
        VoiceUtil.apply(ctx, e, alert)
        currentUtt = "u${++uttCounter}"
        speaking = true
        setState(VState.SPEAKING)
        e.speak(text.take(3500), TextToSpeech.QUEUE_FLUSH, VoiceUtil.params(ctx, alert), currentUtt)
        if (Prefs.bool(ctx, "interrupt", true)) {
            main.postDelayed({ if (speaking) listenForInterrupt() }, 700)
        }
    }

    private val utterListener = object : UtteranceProgressListener() {
        override fun onStart(utteranceId: String?) {}
        override fun onDone(utteranceId: String?) {
            main.post { speakDone(utteranceId) }
        }
        @Deprecated("Deprecated in Java")
        override fun onError(utteranceId: String?) {
            main.post { speakDone(utteranceId) }
        }
    }

    private fun speakDone(id: String?) {
        if (id != currentUtt) return
        speaking = false
        interruptMode = false
        recognizer?.cancel()
        afterSpeech()
    }

    private fun afterSpeech() {
        if (!started) return
        pendingAlert?.let {
            pendingAlert = null
            speak(it, true)
            return
        }
        if (Prefs.get(ctx, "listen_mode", "always") == "tap" && !paused) {
            setState(VState.IDLE)
        } else {
            startListening()
        }
    }
}
