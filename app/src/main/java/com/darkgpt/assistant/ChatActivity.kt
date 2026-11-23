package com.darkgpt.assistant

import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class ChatActivity : AppCompatActivity() {

    private lateinit var tvLog: TextView
    private lateinit var scroll: ScrollView
    private lateinit var etMsg: EditText
    private lateinit var brain: Brain

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_chat)
        brain = Brain(this)
        tvLog = findViewById(R.id.tvLog)
        scroll = findViewById(R.id.scroll)
        etMsg = findViewById(R.id.etMsg)

        for ((role, text) in Brain.history) append(if (role == "user") "You" else "AI", text)

        findViewById<TextView>(R.id.btnBack).setOnClickListener { finish() }
        findViewById<Button>(R.id.btnSend).setOnClickListener {
            val msg = etMsg.text.toString().trim()
            if (msg.isEmpty()) return@setOnClickListener
            etMsg.setText("")
            append("You", msg)
            brain.handle(msg) { reply, _ -> append("AI", reply) }
        }
    }

    private fun append(who: String, text: String) {
        tvLog.append("$who: $text\n\n")
        scroll.post { scroll.fullScroll(ScrollView.FOCUS_DOWN) }
    }
}
