package pt.mvplisboa.sessionoverlay

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

class SetupActivity : Activity() {
    private lateinit var status: TextView
    private val main = Handler(Looper.getMainLooper())
    private val refresh = object : Runnable {
        override fun run() {
            renderStatus()
            main.postDelayed(this, 1000)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Opening administration is also the local recovery path for a visible test.
        if (OverlayState.running) command(OverlayService.ACTION_DISMISS_WINDOWS)
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(48), dp(24), dp(48), dp(24))
        }
        content.addView(TextView(this).apply {
            text = getString(R.string.setup_title, BuildConfig.VERSION_NAME)
            textSize = 26f
            setTextColor(Color.WHITE)
        })
        content.addView(TextView(this).apply {
            text = getString(R.string.setup_instructions)
            textSize = 16f
            setPadding(0, dp(8), 0, dp(8))
        })
        status = TextView(this).apply {
            textSize = 16f
            setPadding(0, dp(8), 0, dp(8))
            accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE
        }
        content.addView(status)
        fun button(label: Int, action: () -> Unit): Button = Button(this).also { button ->
            button.setText(label)
            button.isAllCaps = false
            button.setOnClickListener { action() }
            content.addView(button, LinearLayout.LayoutParams(-1, dp(52)))
        }
        val permission = button(R.string.grant_permission) { openPermissionSettings() }
        button(R.string.start_service) { command(OverlayService.ACTION_START) }
        button(R.string.show_test) {
            if (command(OverlayService.ACTION_SHOW_DELAYED)) {
                Toast.makeText(this, R.string.test_scheduled, Toast.LENGTH_LONG).show()
                finish()
            }
        }
        button(R.string.hide_test) {
            if (OverlayState.running) command(OverlayService.ACTION_HIDE)
        }
        button(R.string.demo_warnings) {
            if (command(OverlayService.ACTION_DEMO)) finish()
        }
        button(R.string.start_timer) {
            if (command(OverlayService.ACTION_TIMER_START)) finish()
        }
        button(R.string.extend_timer) { command(OverlayService.ACTION_TIMER_EXTEND) }
        button(R.string.cancel_timer) { command(OverlayService.ACTION_TIMER_CANCEL) }
        button(R.string.stop_service) { stopService(Intent(this, OverlayService::class.java)) }
        val scroll = ScrollView(this).apply { addView(content) }
        setContentView(scroll)
        permission.requestFocus()
    }

    override fun onResume() {
        super.onResume()
        main.post(refresh)
    }

    override fun onPause() {
        main.removeCallbacks(refresh)
        super.onPause()
    }

    private fun renderStatus() {
        status.text = getString(
            R.string.status,
            if (Settings.canDrawOverlays(this)) getString(R.string.allowed) else getString(R.string.missing),
            if (OverlayState.running) getString(R.string.running) else getString(R.string.stopped),
            if (OverlayState.visible) getString(R.string.visible) else getString(R.string.hidden),
            if (OverlayState.pendingShow) getString(R.string.show_pending) else "",
            OverlayState.lastError ?: "—",
        )
        status.append("\nForeground: ${OverlayState.foregroundRunning} · Timer: ${OverlayState.timerState} · ${OverlayState.remainingSeconds}s · Notice: ${OverlayState.noticeVisible}")
    }

    private fun command(action: String): Boolean {
        val recovery = action in listOf(OverlayService.ACTION_HIDE, OverlayService.ACTION_TIMER_CANCEL,
            OverlayService.ACTION_DISMISS_WINDOWS)
        if (!recovery && !Settings.canDrawOverlays(this)) {
            Toast.makeText(this, R.string.permission_required, Toast.LENGTH_LONG).show()
            return false
        }
        return try {
            val service = Intent(this, OverlayService::class.java).setAction(action)
            if (OverlayState.foregroundRunning) startService(service) else startForegroundService(service)
            true
        } catch (error: Exception) {
            OverlayState.lastError = error.message ?: error.javaClass.simpleName
            Toast.makeText(this, OverlayState.lastError, Toast.LENGTH_LONG).show()
            false
        }
    }

    private fun openPermissionSettings() {
        try {
            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
        } catch (_: ActivityNotFoundException) {
            try {
                startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName")))
            } catch (_: ActivityNotFoundException) {
                Toast.makeText(this, R.string.permission_fallback, Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}

