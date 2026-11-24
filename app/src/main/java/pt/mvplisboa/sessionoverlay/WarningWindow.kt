package pt.mvplisboa.sessionoverlay

import android.content.Context
import android.annotation.SuppressLint
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import java.util.Locale

/** A bounded, non-interactive window. It never takes focus from HDMI or the launcher. */
class WarningWindow(private val context: Context) {
    private val manager = context.getSystemService(WindowManager::class.java)
    private val main = Handler(Looper.getMainLooper())
    private var card: WarningCard? = null
    private val dismiss = Runnable { hide() }

    // Placement is intentionally the physical right edge, including in RTL locales.
    @SuppressLint("RtlHardcoded")
    fun show(thresholdSeconds: Long, remainingSeconds: Long = thresholdSeconds, preview: Boolean = false) {
        check(Looper.myLooper() == Looper.getMainLooper())
        check(Settings.canDrawOverlays(context)) { "Allow Display over other apps first." }
        require(thresholdSeconds in SessionTimerEngine.WARNING_SECONDS) { "Unsupported notice threshold" }
        main.removeCallbacks(dismiss)
        val view = card ?: WarningCard(context).also { view ->
            val params = WindowManager.LayoutParams(
                (dp(312) * CARD_SCALE).toInt(), (dp(166) * CARD_SCALE).toInt(),
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT,
            ).apply {
                gravity = Gravity.TOP or Gravity.RIGHT
                x = dp(12)
                y = dp(12)
                title = "MVP Session Notice"
                if (Build.VERSION.SDK_INT >= 30) setFitInsetsTypes(0)
            }
            manager.addView(view, params)
            card = view
        }
        view.configure(thresholdSeconds, remainingSeconds, preview)
        view.animate().cancel()
        view.translationX = dp(36) * CARD_SCALE
        view.alpha = 0f
        view.animate().translationX(0f).alpha(1f).setDuration(240).start()
        OverlayState.noticeVisible = true
        OverlayState.noticeThresholdSeconds = thresholdSeconds
        main.postDelayed(dismiss, DISPLAY_MILLIS)
        Log.i(OverlayWindow.TAG, "notice_shown threshold_seconds=$thresholdSeconds preview=$preview")
    }

    fun hide() {
        check(Looper.myLooper() == Looper.getMainLooper())
        main.removeCallbacks(dismiss)
        card?.let {
            it.animate().cancel()
            manager.removeViewImmediate(it)
        }
        card = null
        OverlayState.noticeVisible = false
        OverlayState.noticeThresholdSeconds = null
    }

    private fun dp(value: Int) = (value * context.resources.displayMetrics.density).toInt()

    companion object {
        const val DISPLAY_MILLIS = 10_000L
        const val CARD_SCALE = 0.8f
    }
}

private class WarningCard(context: Context) : View(context) {
    private val density = resources.displayMetrics.density * WarningWindow.CARD_SCALE
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val body = RectF()
    private val clip = android.graphics.Path()
    private val ring = RectF()
    private var backgroundShader: Shader? = null
    private var glowShader: Shader? = null
    private var accent = Color.rgb(245, 189, 106)
    private val bold = Typeface.create("sans-serif", Typeface.BOLD)
    private val medium = Typeface.create("sans-serif-medium", Typeface.NORMAL)
    private val regular = Typeface.create("sans-serif", Typeface.NORMAL)
    private var threshold = 900L
    private var remainingAtStart = 900L
    private var shownAt = 0L
    private var preview = false

    fun configure(thresholdSeconds: Long, remainingSeconds: Long, isPreview: Boolean) {
        threshold = thresholdSeconds
        remainingAtStart = remainingSeconds
        shownAt = SystemClock.elapsedRealtime()
        preview = isPreview
        accent = Color.parseColor(when (threshold) {
            900L -> "#F5BD6A"
            300L -> "#FF9254"
            60L -> "#FF596D"
            else -> "#FF364E"
        })
        rebuildAppearance()
        contentDescription = "MVP Lisboa. $remainingSeconds seconds left. Extend your session at reception."
        invalidate()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        rebuildAppearance()
    }

    private fun rebuildAppearance() {
        if (width == 0 || height == 0) return
        val w = width / density
        val h = height / density
        body.set(1f, 1f, w - 1f, h - 1f)
        clip.reset()
        clip.addRoundRect(body, 16f, 16f, android.graphics.Path.Direction.CW)
        val cx = w - 49f
        ring.set(cx - 24, 52f, cx + 24, 100f)
        backgroundShader = LinearGradient(0f, 0f, w, h,
            intArrayOf(Color.argb(190, 24, 30, 42), Color.argb(185, 11, 15, 23)), null, Shader.TileMode.CLAMP)
        glowShader = RadialGradient(w, 0f, 180f,
            intArrayOf((accent and 0x00FFFFFF) or 0x26000000, Color.TRANSPARENT), null, Shader.TileMode.CLAMP)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.save()
        canvas.scale(density, density)
        val w = width / density
        val h = height / density
        val elapsed = (SystemClock.elapsedRealtime() - shownAt).coerceAtLeast(0)
        val remaining = (remainingAtStart - elapsed / 1000).coerceAtLeast(0)

        paint.style = Paint.Style.FILL
        paint.shader = backgroundShader
        canvas.drawRoundRect(body, 16f, 16f, paint)
        paint.shader = null

        canvas.save()
        canvas.clipPath(clip)
        paint.shader = glowShader
        canvas.drawRect(body, paint)
        paint.shader = null
        paint.color = accent
        canvas.drawRect(w - 4f, 0f, w, h, paint)
        // The quiet progress line counts down the popup's ten seconds on screen.
        paint.color = (accent and 0x00FFFFFF) or 0x80000000.toInt()
        val progress = (1f - elapsed.toFloat() / WarningWindow.DISPLAY_MILLIS).coerceIn(0f, 1f)
        canvas.drawRect(0f, h - 2f, w * progress, h, paint)
        canvas.restore()

        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 1f
        paint.color = Color.rgb(52, 61, 77)
        canvas.drawRoundRect(body, 16f, 16f, paint)
        paint.style = Paint.Style.FILL

        text(canvas, "MVP", 19f, 29f, 16f, Color.WHITE, bold)
        text(canvas, "L I S B O A", 59f, 28f, 8f, Color.rgb(171, 183, 202), medium)
        val badge = when (threshold) {
            900L -> "15 MIN LEFT"
            300L -> "5 MIN LEFT"
            60L -> "FINAL MINUTE"
            else -> "30 SEC LEFT"
        }
        paint.color = (accent and 0x00FFFFFF) or 0x1F000000
        canvas.drawRoundRect(w - 107f, 14f, w - 17f, 34f, 7f, 7f, paint)
        text(canvas, badge, w - 62f, 27.5f, 8f, accent, bold, Paint.Align.CENTER)

        val countdown = String.format(Locale.US, "%02d:%02d", remaining / 60, remaining % 60)
        text(canvas, countdown, 18f, 85f, 45f, Color.WHITE, bold)
        text(canvas, if (threshold <= 60) "YOUR SESSION ENDS SOON" else "TIME LEFT IN YOUR SESSION",
            20f, 103f, 8.5f, accent, medium)

        val cx = w - 49f
        val cy = 76f
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 3f
        paint.color = Color.rgb(50, 57, 72)
        canvas.drawOval(ring, paint)
        paint.color = accent
        paint.strokeCap = Paint.Cap.ROUND
        canvas.drawArc(ring, -90f, (remaining.toFloat() / 1200).coerceIn(0.025f, 1f) * 360, false, paint)
        paint.strokeWidth = 2f
        canvas.drawLine(cx, cy - 11, cx, cy, paint)
        canvas.drawLine(cx, cy, cx + 8, cy + 5, paint)
        paint.strokeCap = Paint.Cap.BUTT
        paint.style = Paint.Style.FILL
        canvas.drawCircle(cx, cy, 2.5f, paint)

        paint.color = Color.rgb(45, 53, 67)
        canvas.drawRect(20f, 119f, w - 20f, 120f, paint)
        text(canvas, "More time? Extend at reception.", 20f, 140f, 11.5f, Color.rgb(224, 230, 240), medium)
        text(canvas, if (preview) "PREVIEW  /  MVP GAME LOUNGE" else "Keep playing. We will take care of the rest.",
            20f, 155f, 8.5f, Color.rgb(140, 153, 176), regular)
        canvas.restore()
        if (elapsed < WarningWindow.DISPLAY_MILLIS) postInvalidateDelayed(100)
    }

    private fun text(canvas: Canvas, value: String, x: Float, y: Float, size: Float,
                     color: Int, typeface: Typeface, align: Paint.Align = Paint.Align.LEFT) {
        paint.shader = null
        paint.style = Paint.Style.FILL
        paint.color = color
        paint.typeface = typeface
        paint.textSize = size
        paint.textAlign = align
        canvas.drawText(value, x, y, paint)
        paint.textAlign = Paint.Align.LEFT
    }
}
