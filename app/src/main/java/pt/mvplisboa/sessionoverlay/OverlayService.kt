package pt.mvplisboa.sessionoverlay

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.app.ActivityManager
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log

class OverlayService : Service() {
    private lateinit var overlay: OverlayWindow
    private lateinit var warning: WarningWindow
    private lateinit var foregroundNotification: Notification
    private val timer = SessionTimerEngine()
    private val main = Handler(Looper.getMainLooper())
    private val demoTasks = mutableListOf<Runnable>()
    private val timerTick = Runnable { tickTimer() }
    private val delayedShow = Runnable {
        OverlayState.pendingShow = false
        execute(ACTION_SHOW) { overlay.show() }
    }

    override fun onCreate() {
        super.onCreate()
        overlay = OverlayWindow(this)
        warning = WarningWindow(this)
        val notifications = getSystemService(NotificationManager::class.java)
        notifications.createNotificationChannel(
            NotificationChannel(CHANNEL, getString(R.string.notification_channel), NotificationManager.IMPORTANCE_LOW),
        )
        val setup = PendingIntent.getActivity(
            this, 0, Intent(this, SetupActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        foregroundNotification = Notification.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_overlay)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(getString(R.string.notification_text))
            .setContentIntent(setup)
            .setOngoing(true)
            .setCategory(Notification.CATEGORY_SERVICE)
            .build()
        promoteForeground()
        OverlayState.running = true
        OverlayState.visible = false
        OverlayState.pendingShow = false
        OverlayState.startedAt = SystemClock.elapsedRealtime()
        OverlayState.noticeVisible = false
        OverlayState.noticeThresholdSeconds = null
        publishTimerState()
        OverlayState.demoRunning = false
        Log.i(OverlayWindow.TAG, "service_started version=${BuildConfig.VERSION_NAME} visible=false")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // A visible administrator can retry promotion when TCL rejected a background launch.
        if (!OverlayState.foregroundRunning) promoteForeground()
        val action = intent?.action ?: ACTION_START
        when (action) {
            ACTION_SHOW -> {
                cancelPlans()
                execute(action) { warning.hide(); overlay.show() }
            }
            ACTION_SHOW_DELAYED -> {
                cancelPlans()
                execute(action) { warning.hide() }
                OverlayState.pendingShow = true
                main.postDelayed(delayedShow, 10_000)
                Log.i(OverlayWindow.TAG, "show_scheduled delay_seconds=10")
            }
            ACTION_HIDE -> {
                cancelPlans()
                execute(action) { warning.hide(); overlay.hide() }
            }
            ACTION_DISMISS_WINDOWS -> {
                cancelPendingShow()
                cancelDemo()
                execute(action) { warning.hide(); overlay.hide() }
            }
            ACTION_TIMER_START -> execute(action) {
                val seconds = intent!!.getLongExtra("duration_seconds", 1200)
                require(seconds in 1..86_400) { "Duration must be 1 to 86400 seconds" }
                cancelPlans()
                warning.hide()
                overlay.hide()
                val now = System.currentTimeMillis()
                timer.confirm(intent.getStringExtra("session_id") ?: "local-test-$now",
                    now + seconds * 1000, now, SystemClock.elapsedRealtime())
                tickTimer()
            }
            ACTION_TIMER_EXTEND -> execute(action) {
                val seconds = intent!!.getLongExtra("extend_seconds", 300)
                require(seconds in 1..86_400) { "Extension must be 1 to 86400 seconds" }
                val id = checkNotNull(timer.sessionId) { "No session to extend" }
                val remaining = timer.poll(SystemClock.elapsedRealtime()).remainingMillis
                val duration = remaining + seconds * 1000
                require(duration <= SessionTimerEngine.MAX_DURATION_MILLIS) { "Session may not exceed 24 hours" }
                main.removeCallbacks(timerTick)
                warning.hide()
                overlay.hide()
                val now = System.currentTimeMillis()
                timer.confirm(id, now + duration, now, SystemClock.elapsedRealtime())
                tickTimer()
            }
            ACTION_TIMER_CANCEL -> {
                cancelPlans()
                execute(action) { warning.hide(); overlay.hide() }
            }
            ACTION_NOTICE -> execute(action) {
                val threshold = intent!!.getLongExtra("threshold_seconds", 900)
                require(threshold in SessionTimerEngine.WARNING_SECONDS) { "Use 900, 300, 60 or 30 seconds" }
                cancelPlans()
                overlay.hide()
                warning.show(threshold, threshold, preview = true)
            }
            ACTION_DEMO -> execute(action) { startDemo() }
            ACTION_START -> Log.i(OverlayWindow.TAG, "service_ready visible=${OverlayState.visible}")
            ACTION_STOP -> {
                cancelPlans()
                execute(action) { warning.hide(); overlay.hide() }
                stopSelf()
            }
            else -> Log.w(OverlayWindow.TAG, "unknown_action")
        }
        // A system restart starts hidden. No intent or persisted flag can restore a block.
        return START_STICKY
    }

    override fun onDestroy() {
        cancelPlans()
        execute("destroy") { warning.hide(); overlay.hide() }
        OverlayState.running = false
        OverlayState.foregroundRunning = false
        stopForeground(STOP_FOREGROUND_REMOVE)
        Log.i(OverlayWindow.TAG, "service_stopped")
        super.onDestroy()
    }

    override fun onBind(intent: Intent?) = null

    @Suppress("DEPRECATION")
    private fun promoteForeground() {
        startForeground(1001, foregroundNotification)
        // TCL can silently reject startForeground. Check Android's observed service state.
        OverlayState.foregroundRunning = getSystemService(ActivityManager::class.java)
            .getRunningServices(20).any { it.service.className == javaClass.name && it.foreground }
        OverlayState.lastError = if (OverlayState.foregroundRunning) null
            else "Foreground service not confirmed; launch from TCL administrator screen."
    }

    private fun cancelPendingShow() {
        main.removeCallbacks(delayedShow)
        OverlayState.pendingShow = false
    }

    private fun tickTimer() {
        val tick = timer.poll(SystemClock.elapsedRealtime())
        publishTimerState(tick.remainingMillis)
        if (tick.justExpired) {
            execute("timer_expired") { warning.hide(); overlay.show() }
        } else if (tick.warningSeconds != null) {
            execute("timer_warning") {
                warning.show(tick.warningSeconds, (tick.remainingMillis + 999) / 1000)
            }
        }
        if (timer.state == "ACTIVE") main.postDelayed(timerTick, 250)
    }

    private fun publishTimerState(remainingMillis: Long = 0) {
        OverlayState.timerState = timer.state
        OverlayState.sessionId = timer.sessionId
        OverlayState.expiresAtUtcMillis = timer.expiresAtUtcMillis
        OverlayState.remainingSeconds = (remainingMillis + 999) / 1000
    }

    private fun cancelPlans() {
        cancelPendingShow()
        cancelDemo()
        main.removeCallbacks(timerTick)
        timer.cancel()
        publishTimerState()
    }

    private fun cancelDemo() {
        demoTasks.forEach { main.removeCallbacks(it) }
        demoTasks.clear()
        if (OverlayState.demoRunning) publishTimerState()
        OverlayState.demoRunning = false
    }

    private fun startDemo() {
        cancelPlans()
        warning.hide()
        overlay.hide()
        OverlayState.demoRunning = true
        OverlayState.timerState = "DEMO"
        SessionTimerEngine.WARNING_SECONDS.forEachIndexed { index, seconds ->
            demoAt(index * 12_000L) {
                OverlayState.remainingSeconds = seconds
                warning.show(seconds, seconds, preview = true)
            }
        }
        demoAt(48_000) {
            OverlayState.remainingSeconds = 0
            OverlayState.timerState = "DEMO_EXPIRED"
            warning.hide()
            overlay.show()
        }
        demoAt(53_000) {
            warning.hide()
            overlay.hide()
            OverlayState.demoRunning = false
            publishTimerState()
            demoTasks.clear()
        }
    }

    private fun demoAt(delay: Long, action: () -> Unit) {
        val task = Runnable {
            if (OverlayState.demoRunning) execute("demo_step", action)
        }
        demoTasks.add(task)
        main.postDelayed(task, delay)
    }

    private inline fun execute(action: String, operation: () -> Unit) {
        try {
            OverlayState.lastError = null
            operation()
            if (!OverlayState.foregroundRunning) {
                OverlayState.lastError = "Foreground service not confirmed; check TCL APP_AUTO_START permission."
            }
            Log.i(OverlayWindow.TAG, "action=$action visible=${OverlayState.visible}")
        } catch (error: Exception) {
            OverlayState.lastError = error.message ?: error.javaClass.simpleName
            Log.e(OverlayWindow.TAG, "action=$action failed visible=${OverlayState.visible}", error)
        }
    }

    companion object {
        private const val CHANNEL = "mvp_overlay_service"
        private const val PREFIX = "pt.mvplisboa.sessionoverlay."
        const val ACTION_START = PREFIX + "START"
        const val ACTION_SHOW = PREFIX + "SHOW"
        const val ACTION_SHOW_DELAYED = PREFIX + "SHOW_DELAYED"
        const val ACTION_HIDE = PREFIX + "HIDE"
        const val ACTION_STOP = PREFIX + "STOP"
        const val ACTION_DISMISS_WINDOWS = PREFIX + "DISMISS_WINDOWS"
        const val ACTION_TIMER_START = PREFIX + "TIMER_START"
        const val ACTION_TIMER_EXTEND = PREFIX + "TIMER_EXTEND"
        const val ACTION_TIMER_CANCEL = PREFIX + "TIMER_CANCEL"
        const val ACTION_NOTICE = PREFIX + "NOTICE"
        const val ACTION_DEMO = PREFIX + "DEMO"
    }
}

