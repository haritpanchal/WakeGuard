package com.haritpanchal.wakeguard.wake

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.cos
import kotlin.math.sin

class WakefulnessDetectorTest {
    @Test
    fun silenceIsNotAwake() {
        val detector = WakefulnessDetector()
        val decision = feed(detector, 0L, 60_000L) { t -> still(t) }
        assertFalse(decision.awake)
        assertEquals(0, decision.validBurstCount)
    }

    @Test
    fun twitchIsNotAwake() {
        val detector = WakefulnessDetector()
        detector.add(still(0L))
        detector.add(MotionSample(100L, 6f, 0f, 9.8f))
        val decision = detector.add(MotionSample(200L, 0f, 0f, 9.8f))
        feed(detector, 300L, 5_000L) { t -> still(t) }
        assertFalse(decision.awake)
        assertEquals(0, detector.snapshot().validBurstCount)
    }

    @Test
    fun singleRolloverIsNotAwake() {
        val detector = WakefulnessDetector()
        detector.add(still(0L))
        val decision = feed(detector, 100L, 2_100L) { t ->
            val fraction = (t - 100L) / 2_000f
            rotated(t, fraction)
        }
        feed(detector, 2_200L, 12_000L) { t -> rotated(t, 1f) }
        assertFalse(decision.awake)
        assertEquals(1, detector.snapshot().validBurstCount)
        assertTrue(detector.snapshot().totalActiveMs < 8_000L)
    }

    @Test
    fun threeSeparatedBurstsAreAwake() {
        val detector = WakefulnessDetector()
        detector.add(still(0L))
        shake(detector, 1_000L, 6_000L)
        feed(detector, 6_100L, 20_000L) { t -> still(t) }
        shake(detector, 21_000L, 26_000L)
        feed(detector, 26_100L, 40_000L) { t -> still(t) }
        val decision = shake(detector, 41_000L, 46_000L)
        assertTrue(decision.awake)
        assertEquals(AwakeReason.REPEATED_BURSTS, decision.reason)
        assertTrue(decision.validBurstCount >= 3)
        assertTrue(decision.spanMs >= 40_000L)
        assertTrue(decision.totalActiveMs >= 12_000L)
    }

    @Test
    fun sustainedMovementIsAwake() {
        val detector = WakefulnessDetector()
        detector.add(still(0L))
        val decision = shake(detector, 100L, 26_500L)
        assertTrue(decision.awake)
        assertEquals(AwakeReason.SUSTAINED_BURST, decision.reason)
    }

    @Test
    fun stopIsAwakeWithoutMovement() {
        val detector = WakefulnessDetector()
        feed(detector, 0L, 10_000L) { t -> still(t) }
        val decision = detector.onStop()
        assertTrue(decision.awake)
        assertEquals(AwakeReason.STOP, decision.reason)
    }

    private fun still(timestampMs: Long) = MotionSample(timestampMs, 0f, 0f, 9.80665f)

    private fun rotated(timestampMs: Long, fraction: Float): MotionSample {
        val angle = fraction.coerceIn(0f, 1f) * (Math.PI / 2.0)
        val g = 9.80665
        return MotionSample(
            timestampMs,
            (g * sin(angle)).toFloat(),
            0f,
            (g * cos(angle)).toFloat(),
        )
    }

    private fun shake(detector: WakefulnessDetector, fromMs: Long, untilMs: Long): WakefulnessSnapshot {
        var timestamp = fromMs
        var sign = 1f
        var latest = detector.snapshot()
        while (timestamp <= untilMs) {
            latest = detector.add(MotionSample(timestamp, sign * 5f, 0f, 9.80665f))
            sign = -sign
            timestamp += 100L
        }
        return latest
    }

    private fun feed(
        detector: WakefulnessDetector,
        fromMs: Long,
        untilMs: Long,
        sample: (Long) -> MotionSample,
    ): WakefulnessSnapshot {
        var timestamp = fromMs
        var latest = detector.snapshot()
        while (timestamp <= untilMs) {
            latest = detector.add(sample(timestamp))
            timestamp += 100L
        }
        return latest
    }
}
