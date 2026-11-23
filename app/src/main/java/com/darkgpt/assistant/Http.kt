package com.darkgpt.assistant

import java.net.HttpURLConnection
import java.net.URL

object Http {
    fun get(url: String): String {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 15000
        c.readTimeout = 20000
        val code = c.responseCode
        val s = if (code in 200..299) c.inputStream else c.errorStream
        val txt = s.bufferedReader().readText()
        if (code !in 200..299) throw Exception("HTTP $code")
        return txt
    }
}
