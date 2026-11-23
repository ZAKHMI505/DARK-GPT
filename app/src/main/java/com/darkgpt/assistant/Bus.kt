package com.darkgpt.assistant

import android.os.Handler
import android.os.Looper
import java.util.concurrent.CopyOnWriteArrayList

enum class VState { IDLE, LISTENING, PROCESSING, SPEAKING, PAUSED, EXECUTING, ERROR }

/** Service aur UI ke darmiyan shared state (same process) */
object Bus {
    @Volatile var running = false
    @Volatile var state = VState.IDLE
    @Volatile var hint = ""
    @Volatile var level = 0f

    private val listeners = CopyOnWriteArrayList<() -> Unit>()
    private val main = Handler(Looper.getMainLooper())

    fun add(l: () -> Unit) { listeners.add(l) }
    fun remove(l: () -> Unit) { listeners.remove(l) }
    fun changed() { main.post { listeners.forEach { it() } } }
}
