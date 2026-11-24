package pt.mvplisboa.sessionoverlay

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Shell-only bridge, excluded from release. Does not open an Activity or change input. */
class DebugControlReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = when (intent.getStringExtra("command")) {
            "START" -> OverlayService.ACTION_START
            "SHOW" -> OverlayService.ACTION_SHOW
            "SHOW_DELAYED" -> OverlayService.ACTION_SHOW_DELAYED
            "HIDE" -> OverlayService.ACTION_HIDE
            "STOP" -> OverlayService.ACTION_STOP
            "STATUS" -> null
            "TIMER_START" -> OverlayService.ACTION_TIMER_START
            "TIMER_EXTEND" -> OverlayService.ACTION_TIMER_EXTEND
            "TIMER_CANCEL" -> OverlayService.ACTION_TIMER_CANCEL
            "NOTICE" -> OverlayService.ACTION_NOTICE
            "DEMO" -> OverlayService.ACTION_DEMO
            else -> {
                resultCode = 1
                resultData = "Unknown command"
                return
            }
        }
        try {
            if (action == OverlayService.ACTION_STOP) {
                context.stopService(Intent(context, OverlayService::class.java))
            } else if (action != null && !(action == OverlayService.ACTION_HIDE && !OverlayState.running)) {
                val service = Intent(context, OverlayService::class.java).setAction(action)
                for (key in listOf("duration_seconds", "extend_seconds", "threshold_seconds")) {
                    if (intent.hasExtra(key)) service.putExtra(key, intent.getLongExtra(key, 0))
                }
                intent.getStringExtra("session_id")?.let { service.putExtra("session_id", it) }
                if (OverlayState.foregroundRunning) context.startService(service)
                else context.startForegroundService(service)
            }
            resultCode = 0
            // Commands are queued onto the service; STATUS observes the completed operation later.
            resultData = OverlayState.json()
        } catch (error: Exception) {
            resultCode = 1
            resultData = error.message ?: error.javaClass.simpleName
        }
    }
}

