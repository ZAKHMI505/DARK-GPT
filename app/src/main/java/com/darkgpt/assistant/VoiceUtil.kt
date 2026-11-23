package com.darkgpt.assistant

import android.content.Context
import android.os.Bundle
import android.speech.tts.TextToSpeech
import java.util.Locale

object VoiceUtil {
    fun apply(ctx: Context, e: TextToSpeech, alert: Boolean) {
        val name = Prefs.get(ctx, if (alert) "voice_alert" else "voice_asst")
        var ok = false
        if (name.isNotBlank()) {
            val v = e.voices?.firstOrNull { it.name == name }
            if (v != null) {
                e.setVoice(v)
                ok = true
            }
        }
        if (!ok) {
            val loc = if (Prefs.lang(ctx) == "ur") Locale("ur", "PK") else Locale.US
            val r = e.setLanguage(loc)
            if (r == TextToSpeech.LANG_MISSING_DATA || r == TextToSpeech.LANG_NOT_SUPPORTED) {
                e.setLanguage(Locale.US)
            }
        }
        e.setSpeechRate(Prefs.int(ctx, "rate", 95) / 100f)
        e.setPitch(Prefs.int(ctx, "pitch", 85) / 100f)
    }

    fun params(ctx: Context, alert: Boolean): Bundle {
        val vol = Prefs.int(ctx, if (alert) "vol_alert" else "vol", 100) / 100f
        return Bundle().apply { putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, vol) }
    }
}
