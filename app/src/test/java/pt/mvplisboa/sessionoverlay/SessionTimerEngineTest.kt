package pt.mvplisboa.sessionoverlay

import org.junit.Assert.*
import org.junit.Test

class SessionTimerEngineTest {
    private val utc = 1_800_000_000_000L
    private val elapsed = 10_000L

    private fun start(seconds: Long = 1200): SessionTimerEngine = SessionTimerEngine().apply {
        confirm("session-a", utc + seconds * 1000, utc, elapsed)
    }

    @Test fun `warnings fire once at 15 5 1 minutes and 30 seconds`() {
        val timer = start()
        assertNull(timer.poll(elapsed + 299_999).warningSeconds)
        for ((offset, warning) in listOf(300_000L to 900L, 900_000L to 300L, 1_140_000L to 60L, 1_170_000L to 30L)) {
            assertEquals(warning, timer.poll(elapsed + offset).warningSeconds)
            assertNull(timer.poll(elapsed + offset + 1).warningSeconds)
        }
        assertTrue(timer.poll(elapsed + 1_200_000).justExpired)
        assertFalse(timer.poll(elapsed + 1_200_001).justExpired)
    }

    @Test fun `a delayed tick skips obsolete notices and emits the urgent one`() {
        val timer = start()
        assertEquals(60L, timer.poll(elapsed + 1_145_000).warningSeconds)
        assertNull(timer.poll(elapsed + 1_145_001).warningSeconds)
    }

    @Test fun `short sessions do not emit historical fifteen minute warnings`() {
        val timer = start(90)
        assertNull(timer.poll(elapsed).warningSeconds)
        assertEquals(60L, timer.poll(elapsed + 30_000).warningSeconds)
        assertEquals(30L, timer.poll(elapsed + 60_000).warningSeconds)
    }

    @Test fun `duplicate confirmation does not restart countdown or repeat notices`() {
        val timer = start()
        assertEquals(900L, timer.poll(elapsed + 300_000).warningSeconds)
        assertFalse(timer.confirm("session-a", utc + 1_200_000, utc + 310_000, elapsed + 310_000))
        val tick = timer.poll(elapsed + 310_000)
        assertEquals(890_000L, tick.remainingMillis)
        assertNull(tick.warningSeconds)
    }

    @Test fun `extension invalidates the old expiration and enables future thresholds`() {
        val timer = start(60)
        assertEquals(60L, timer.poll(elapsed).warningSeconds)
        timer.confirm("session-a", utc + 660_000, utc + 59_000, elapsed + 59_000)
        assertFalse(timer.poll(elapsed + 60_000).justExpired)
        assertEquals(600_000L, timer.poll(elapsed + 60_000).remainingMillis)
        assertEquals(300L, timer.poll(elapsed + 360_000).warningSeconds)
    }

    @Test fun `confirmed extension reactivates an expired session`() {
        val timer = start(1)
        assertTrue(timer.poll(elapsed + 1000).justExpired)
        timer.confirm("session-a", utc + 301_000, utc + 1000, elapsed + 1000)
        assertEquals("ACTIVE", timer.state)
        assertEquals(300L, timer.poll(elapsed + 1000).warningSeconds)
    }

    @Test fun `cancel prevents any stale expiration`() {
        val timer = start(1)
        timer.cancel()
        assertEquals("IDLE", timer.state)
        assertFalse(timer.poll(elapsed + 10_000).justExpired)
        assertNull(timer.poll(elapsed + 10_000).warningSeconds)
    }

    @Test fun `expiration takes priority over any missed warnings`() {
        val timer = start()
        val tick = timer.poll(elapsed + 1_500_000)
        assertTrue(tick.justExpired)
        assertNull(tick.warningSeconds)
        assertEquals(0L, tick.remainingMillis)
    }

    @Test fun `an already expired confirmed session expires immediately`() {
        val timer = SessionTimerEngine()
        timer.confirm("expired", utc - 1000, utc, elapsed)
        assertTrue(timer.poll(elapsed).justExpired)
    }

    @Test fun `remaining time is based on monotonic time rather than device wall clock`() {
        val timer = start(1200)
        // No new UTC value is accepted by poll, even if Android wall time changes.
        assertEquals(1_198_000L, timer.poll(elapsed + 2000).remainingMillis)
    }

    @Test(expected = IllegalArgumentException::class) fun `unreasonable session durations are rejected`() {
        start(86_401)
    }
}
