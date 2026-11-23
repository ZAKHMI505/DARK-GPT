package com.darkgpt.assistant

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import kotlin.math.abs

/** Doosri apps ke upar tairta hua, draggable Orb */
class OverlayOrb(
    private val ctx: Context,
    private val onTap: () -> Unit,
    private val onLong: () -> Unit
) {
    private val wm = ctx.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private var view: OrbView? = null

    @SuppressLint("ClickableViewAccessibility")
    fun show() {
        if (view != null || !Settings.canDrawOverlays(ctx)) return
        val dm = ctx.resources.displayMetrics
        val d = dm.density
        val size = (Prefs.int(ctx, "orb_size", 100) * d).toInt()
        val type = if (Build.VERSION.SDK_INT >= 26) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE
        val p = WindowManager.LayoutParams(
            size, size, type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        )
        p.gravity = Gravity.TOP or Gravity.START
        val maxX = (dm.widthPixels - size).coerceAtLeast(0)
        val maxY = (dm.heightPixels - size).coerceAtLeast(0)
        val sx = Prefs.int(ctx, "orb_x", -1)
        val sy = Prefs.int(ctx, "orb_y", -1)
        p.x = if (sx < 0) maxX - (8 * d).toInt() else sx.coerceIn(0, maxX)
        p.y = if (sy < 0) dm.heightPixels / 3 else sy.coerceIn(0, maxY)

        val v = OrbView(ctx, null)
        v.alpha = Prefs.int(ctx, "orb_alpha", 100) / 100f
        val slop = 8 * d

        v.setOnTouchListener(object : View.OnTouchListener {
            var sx0 = 0
            var sy0 = 0
            var tx = 0f
            var ty = 0f
            var t0 = 0L
            var moved = false

            override fun onTouch(view: View, e: MotionEvent): Boolean {
                when (e.action) {
                    MotionEvent.ACTION_DOWN -> {
                        sx0 = p.x; sy0 = p.y
                        tx = e.rawX; ty = e.rawY
                        t0 = System.currentTimeMillis()
                        moved = false
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val dx = e.rawX - tx
                        val dy = e.rawY - ty
                        if (abs(dx) > slop || abs(dy) > slop) moved = true
                        if (moved) {
                            p.x = (sx0 + dx).toInt().coerceIn(0, maxX)
                            p.y = (sy0 + dy).toInt().coerceIn(0, maxY)
                            runCatching { wm.updateViewLayout(view, p) }
                        }
                    }
                    MotionEvent.ACTION_UP -> {
                        if (moved) {
                            Prefs.putInt(ctx, "orb_x", p.x)
                            Prefs.putInt(ctx, "orb_y", p.y)
                        } else if (System.currentTimeMillis() - t0 > 600) onLong()
                        else onTap()
                    }
                }
                return true
            }
        })
        try {
            wm.addView(v, p)
            view = v
        } catch (e: Exception) {
            ErrorLog.add(ctx, "Overlay: ${e.message}")
        }
    }

    fun hide() {
        view?.let { runCatching { wm.removeView(it) } }
        view = null
    }
}
