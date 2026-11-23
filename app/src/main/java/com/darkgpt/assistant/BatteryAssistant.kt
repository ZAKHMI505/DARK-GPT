package com.darkgpt.assistant

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import androidx.core.content.ContextCompat

/** Battery full / low / charging / temperature alerts (service chalne par) */
class BatteryAssistant(private val ctx: Context, private val say: (String) -> Unit) {

    private var fullDone = false
    private var lastFull = 0L
    private var lowDone = false
    private var lastLow = 0L
    private var lastTemp = 0L
    private var lastPlugged: Boolean? = null
    private var registered = false

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context, i: Intent) {
            if (i.action == Intent.ACTION_BATTERY_CHANGED) onBattery(i, false)
        }
    }

    fun start() {
        if (registered) return
        val sticky = ContextCompat.registerReceiver(
            ctx, receiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED), ContextCompat.RECEIVER_NOT_EXPORTED
        )
        registered = true
        if (sticky != null) onBattery(sticky, true)
    }

    fun stop() {
        if (registered) {
            runCatching { ctx.unregisterReceiver(receiver) }
            registered = false
        }
    }

    private fun onBattery(i: Intent, initial: Boolean) {
        val level = i.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = i.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
        if (level < 0 || scale <= 0) return
        val pct = level * 100 / scale
        val plugged = i.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) != 0
        val temp = i.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) / 10f
        val now = System.currentTimeMillis()

        // charging started / stopped (BATTERY_CHANGED mein plugged badalne se pata chalta hai)
        val prev = lastPlugged
        lastPlugged = plugged
        if (!initial && prev != null && prev != plugged) {
            if (plugged && Prefs.bool(ctx, "chg_start_en", true)) say(Prefs.fmt(ctx, Prefs.msg(ctx, "chg_start_msg"), pct))
            if (!plugged && Prefs.bool(ctx, "chg_stop_en", true)) say(Prefs.fmt(ctx, Prefs.msg(ctx, "chg_stop_msg"), pct))
            return
        }

        // full
        val fullPct = Prefs.int(ctx, "full_pct", 100)
        if (plugged && pct >= fullPct) {
            if (Prefs.bool(ctx, "full_en", true)) {
                val every = Prefs.int(ctx, "full_every", 5) * 60_000L
                val repeat = Prefs.bool(ctx, "full_repeat", false)
                if (!fullDone || (repeat && now - lastFull >= every)) {
                    fullDone = true
                    lastFull = now
                    say(Prefs.fmt(ctx, Prefs.msg(ctx, "full_msg"), pct))
                    return
                }
            }
        } else {
            fullDone = false
        }

        // low
        val lowPct = Prefs.int(ctx, "low_pct", 20)
        if (!plugged && pct <= lowPct) {
            if (Prefs.bool(ctx, "low_en", true)) {
                val every = Prefs.int(ctx, "low_every", 5) * 60_000L
                val repeat = Prefs.bool(ctx, "low_repeat", false)
                if (!lowDone || (repeat && now - lastLow >= every)) {
                    lowDone = true
                    lastLow = now
                    say(Prefs.fmt(ctx, Prefs.msg(ctx, "low_msg"), pct))
                    return
                }
            }
        } else {
            lowDone = false
        }

        // temperature
        if (Prefs.bool(ctx, "temp_en", true) && temp >= 42f && now - lastTemp >= 10 * 60_000L) {
            lastTemp = now
            say(Prefs.fmt(ctx, Prefs.msg(ctx, "temp_msg"), pct))
        }
    }
}
