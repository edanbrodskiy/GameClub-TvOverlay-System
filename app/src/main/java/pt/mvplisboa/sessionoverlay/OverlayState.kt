package pt.mvplisboa.sessionoverlay

import android.os.SystemClock
import org.json.JSONObject

/** Process-local observations. Never persist a request to block the screen. */
object OverlayState {
    var running = false
        internal set
    var foregroundRunning = false
        internal set
    var visible = false
        internal set
    var pendingShow = false
        internal set
    var startedAt = 0L
        internal set
    var lastError: String? = null
        internal set
    var width = 0
        internal set
    var height = 0
        internal set
    var noticeVisible = false
        internal set
    var noticeThresholdSeconds: Long? = null
        internal set
    var timerState = "IDLE"
        internal set
    var sessionId: String? = null
        internal set
    var expiresAtUtcMillis: Long? = null
        internal set
    var remainingSeconds = 0L
        internal set
    var demoRunning = false
        internal set

    // The service, Activity and debug receiver all access this on the main thread.
    fun json(): String = JSONObject().apply {
        put("version", BuildConfig.VERSION_NAME)
        put("service_running", running)
        put("foreground_service", foregroundRunning)
        put("visible", visible)
        put("pending_show", pendingShow)
        put("uptime_seconds", if (running) (SystemClock.elapsedRealtime() - startedAt) / 1000 else 0)
        put("width", width)
        put("height", height)
        put("notice_visible", noticeVisible)
        put("notice_threshold_seconds", noticeThresholdSeconds ?: JSONObject.NULL)
        put("timer_state", timerState)
        put("session_id", sessionId ?: JSONObject.NULL)
        put("expires_at_utc_millis", expiresAtUtcMillis ?: JSONObject.NULL)
        put("remaining_seconds", remainingSeconds)
        put("demo_running", demoRunning)
        put("last_error", lastError ?: JSONObject.NULL)
    }.toString()
}

