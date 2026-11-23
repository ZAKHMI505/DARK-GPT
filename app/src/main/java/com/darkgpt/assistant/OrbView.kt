package com.darkgpt.assistant

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import android.util.AttributeSet
import android.view.View
import android.view.animation.LinearInterpolator
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/** Particle Orb. Apni halat Bus (VoiceEngine) se leta hai. */
class OrbView(context: Context, attrs: AttributeSet?) : View(context, attrs) {

    private enum class S { IDLE, LISTENING, THINKING, SPEAKING }

    private val dots = ArrayList<FloatArray>()
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var angle = 0f
    private var smooth = 0f
    private var reactive = true
    private var glow: RadialGradient? = null
    private val d = resources.displayMetrics.density
    private val twoPi = (2.0 * PI).toFloat()

    private val anim = ValueAnimator.ofFloat(0f, 1f).apply {
        duration = 6000
        repeatCount = ValueAnimator.INFINITE
        interpolator = LinearInterpolator()
        addUpdateListener {
            angle = it.animatedFraction * twoPi
            invalidate()
        }
    }

    init {
        val n = 1500
        val ga = PI * (3.0 - sqrt(5.0))
        for (i in 0 until n) {
            val y = 1.0 - 2.0 * (i + 0.5) / n
            val r = sqrt(1.0 - y * y)
            val th = ga * i
            dots.add(floatArrayOf((cos(th) * r).toFloat(), y.toFloat(), (sin(th) * r).toFloat()))
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        reactive = Prefs.bool(context, "orb_reactive", true)
        anim.start()
    }

    override fun onDetachedFromWindow() {
        anim.cancel()
        super.onDetachedFromWindow()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        val r = min(w, h) / 2f
        glow = RadialGradient(
            w / 2f, h / 2f, r,
            intArrayOf(Color.argb(120, 28, 107, 255), Color.argb(0, 255, 255, 255)),
            null, Shader.TileMode.CLAMP
        )
    }

    override fun onDraw(canvas: Canvas) {
        val cx = width / 2f
        val cy = height / 2f
        val base = min(width, height) / 2f * 0.60f

        val st = if (!Bus.running) S.IDLE else when (Bus.state) {
            VState.LISTENING -> S.LISTENING
            VState.PROCESSING, VState.EXECUTING -> S.THINKING
            VState.SPEAKING -> S.SPEAKING
            else -> S.IDLE
        }
        val target = when (st) {
            S.LISTENING -> if (reactive) Bus.level else 0.35f
            S.SPEAKING -> 0.5f + 0.5f * sin(angle * 7f)
            S.THINKING -> 0.5f + 0.5f * sin(angle * 4f)
            S.IDLE -> 0f
        }
        smooth += (target - smooth) * 0.18f
        val r = base * (1f + 0.02f + 0.10f * smooth)

        // glow
        paint.style = Paint.Style.FILL
        paint.shader = glow
        canvas.drawCircle(cx, cy, min(width, height) / 2f, paint)
        paint.shader = null

        // sphere
        paint.color = Color.WHITE
        canvas.drawCircle(cx, cy, r, paint)

        // particles
        val ca = cos(angle)
        val sa = sin(angle)
        for (p in dots) {
            val x = p[0] * ca + p[2] * sa
            val z = -p[0] * sa + p[2] * ca
            if (z < -0.15f) continue
            val a = (60 + 195 * ((z + 0.15f) / 1.15f)).toInt().coerceIn(0, 255)
            paint.color = Color.argb(a, 28, 107, 255)
            canvas.drawCircle(cx + x * r, cy + p[1] * r, (0.8f + 1.0f * (z + 0.15f)) * d, paint)
        }

        // circular waves (jab sun/bol raha ho)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 1.4f * d
        if (st != S.IDLE) {
            for (i in 0..1) {
                val w = ((angle / twoPi) * 3f + i * 0.5f) % 1f
                paint.color = Color.argb((140 * (1f - w) * (0.4f + 0.6f * smooth)).toInt(), 28, 107, 255)
                val rr = base * (1.05f + 0.5f * w)
                canvas.drawCircle(cx, cy, rr, paint)
            }
        }

        // rotating arc
        paint.strokeWidth = 1.6f * d
        paint.color = Color.argb(160, 28, 107, 255)
        val rr = base * 1.28f
        val deg = Math.toDegrees(angle.toDouble()).toFloat()
        canvas.drawArc(cx - rr, cy - rr, cx + rr, cy + rr, deg, 200f, false, paint)
        paint.style = Paint.Style.FILL
    }
}
