package pt.mvplisboa.sessionoverlay

/** Pure deadline logic. Wall time is read once at confirmation; ticks use monotonic time. */
class SessionTimerEngine {
    data class Tick(val remainingMillis: Long, val warningSeconds: Long?, val justExpired: Boolean)

    var sessionId: String? = null
        private set
    var expiresAtUtcMillis: Long? = null
        private set
    var state = "IDLE"
        private set
    private var deadlineElapsedMillis = 0L
    private val pendingWarnings = mutableSetOf<Long>()

    /** Identical session confirmations do not reset time or emit duplicate warnings. */
    fun confirm(id: String, expiresAtUtc: Long, utcNow: Long, elapsedNow: Long): Boolean {
        require(id.isNotBlank()) { "Session ID is required" }
        if (id == sessionId && expiresAtUtc == expiresAtUtcMillis) return false
        val remaining = Math.subtractExact(expiresAtUtc, utcNow).coerceAtLeast(0)
        require(remaining <= MAX_DURATION_MILLIS) { "Session may not exceed 24 hours" }
        sessionId = id
        expiresAtUtcMillis = expiresAtUtc
        deadlineElapsedMillis = Math.addExact(elapsedNow, remaining)
        state = "ACTIVE"
        pendingWarnings.clear()
        pendingWarnings.addAll(WARNING_SECONDS.filter { it * 1000 <= remaining })
        return true
    }

    fun poll(elapsedNow: Long): Tick {
        if (state != "ACTIVE") return Tick(0, null, false)
        val remaining = (deadlineElapsedMillis - elapsedNow).coerceAtLeast(0)
        if (remaining == 0L) {
            state = "EXPIRED"
            pendingWarnings.clear()
            return Tick(0, null, true)
        }
        // If the main thread was delayed, show only the most urgent crossed threshold.
        val crossed = pendingWarnings.filter { remaining <= it * 1000 }
        pendingWarnings.removeAll(crossed.toSet())
        return Tick(remaining, crossed.minOrNull(), false)
    }

    fun cancel() {
        sessionId = null
        expiresAtUtcMillis = null
        deadlineElapsedMillis = 0
        state = "IDLE"
        pendingWarnings.clear()
    }

    companion object {
        const val MAX_DURATION_MILLIS = 86_400_000L
        val WARNING_SECONDS = listOf(900L, 300L, 60L, 30L)
    }
}
