package pt.mvplisboa.sessionoverlay

import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Build
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.ImageView

class OverlayWindow(private val context: Context) {
    private val manager = context.getSystemService(WindowManager::class.java)
    private var image: ImageView? = null

    @Suppress("DEPRECATION")
    fun show() {
        requireMainThread()
        if (image != null) return
        check(Settings.canDrawOverlays(context)) { "Allow Display over other apps in Android settings first." }

        val view = ImageView(context).apply {
            setImageResource(R.drawable.session_ended)
            setBackgroundColor(Color.BLACK)
            scaleType = ImageView.ScaleType.FIT_XY
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            systemUiVisibility = View.SYSTEM_UI_FLAG_FULLSCREEN or
                View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
                View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
                View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE
            addOnLayoutChangeListener { _, left, top, right, bottom, _, _, _, _ ->
                OverlayState.width = right - left
                OverlayState.height = bottom - top
                Log.i(TAG, "window_layout width=${OverlayState.width} height=${OverlayState.height}")
            }
        }
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                WindowManager.LayoutParams.FLAG_FULLSCREEN,
            PixelFormat.OPAQUE,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            title = "MVP Session Overlay"
            if (Build.VERSION.SDK_INT >= 30) {
                setFitInsetsTypes(0)
                layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
            } else if (Build.VERSION.SDK_INT >= 28) {
                layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }

        manager.addView(view, params)
        image = view
        OverlayState.visible = true
    }

    fun hide() {
        requireMainThread()
        val attached = image ?: return
        manager.removeViewImmediate(attached)
        image = null
        OverlayState.visible = false
        OverlayState.width = 0
        OverlayState.height = 0
    }

    private fun requireMainThread() {
        check(Looper.myLooper() == Looper.getMainLooper()) { "Window operations require the main thread" }
    }

    companion object {
        const val TAG = "MVPOverlay"
    }
}

