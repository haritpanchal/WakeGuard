package com.haritpanchal.wakeguard.wake

import kotlin.math.acos
import kotlin.math.sqrt

data class MotionSample(
    val timestampMs: Long,
    val x: Float,
    val y: Float,
    val z: Float,
)

data class WakefulnessConfig(
    val gravityAlpha: Float = 0.9f,
    val linearThresholdMps2: Float = 0.8f,
    val angleRateDegPerSec: Float = 30f,
    val burstGapMs: Long = 1_500L,
    val minBurstMs: Long = 600L,
    val repeatedBurstCount: Int = 3,
    val repeatedSpanMs: Long = 40_000L,
    val repeatedActiveMs: Long = 12_000L,
    val sustainedBurstMs: Long = 25_000L,
)

enum class AwakeReason {
    STOP,
    SUSTAINED_BURST,
    REPEATED_BURSTS,
}

data class WakefulnessSnapshot(
    val awake: Boolean,
    val reason: AwakeReason?,
    val validBurstCount: Int,
    val totalActiveMs: Long,
    val spanMs: Long,
)

/**
 * Conservative motion heuristic. A single short burst, including a rollover,
 * is not treated as awake. Raw samples are not stored.
 */
class WakefulnessDetector(
    private val config: WakefulnessConfig = WakefulnessConfig(),
) {
    private var gravityX = 0f
    private var gravityY = 0f
    private var gravityZ = 0f
    private var hasGravity = false
    private var lastTimestampMs = 0L

    private var burstOpen = false
    private var burstStartMs = 0L
    private var burstLastActiveMs = 0L

    private val closedBursts = mutableListOf<Burst>()
    private var stopRequested = false

    fun add(sample: MotionSample): WakefulnessSnapshot {
        if (stopRequested) {
            return snapshot()
        }
        if (!hasGravity) {
            gravityX = sample.x
            gravityY = sample.y
            gravityZ = sample.z
            hasGravity = true
            lastTimestampMs = sample.timestampMs
            return snapshot()
        }

        val linear = linearMagnitude(sample)
        val turning = turningRateDegPerSec(sample)
        val active = linear > config.linearThresholdMps2 || turning > config.angleRateDegPerSec
        updateGravity(sample)

        if (active) {
            if (!burstOpen) {
                openBurst(sample.timestampMs)
            } else if (sample.timestampMs - burstLastActiveMs > config.burstGapMs) {
                closeBurst()
                openBurst(sample.timestampMs)
            } else {
                burstLastActiveMs = sample.timestampMs
            }
        } else if (burstOpen && sample.timestampMs - burstLastActiveMs > config.burstGapMs) {
            closeBurst()
        }
        lastTimestampMs = sample.timestampMs
        return snapshot()
    }

    fun onStop(): WakefulnessSnapshot {
        stopRequested = true
        return snapshot()
    }

    fun snapshot(): WakefulnessSnapshot {
        val bursts = burstsForDecision()
        val reason = when {
            stopRequested -> AwakeReason.STOP
            bursts.any { it.durationMs >= config.sustainedBurstMs } -> AwakeReason.SUSTAINED_BURST
            repeatedActivity(bursts) -> AwakeReason.REPEATED_BURSTS
            else -> null
        }
        val span = if (bursts.size >= 2) {
            bursts.last().endMs - bursts.first().startMs
        } else {
            bursts.firstOrNull()?.durationMs ?: 0L
        }
        return WakefulnessSnapshot(
            awake = reason != null,
            reason = reason,
            validBurstCount = bursts.size,
            totalActiveMs = bursts.sumOf { it.durationMs },
            spanMs = span,
        )
    }

    private fun repeatedActivity(bursts: List<Burst>): Boolean {
        if (bursts.size < config.repeatedBurstCount) {
            return false
        }
        val span = bursts.last().endMs - bursts.first().startMs
        val active = bursts.sumOf { it.durationMs }
        return span >= config.repeatedSpanMs && active >= config.repeatedActiveMs
    }

    private fun burstsForDecision(): List<Burst> {
        val open = openBurstOrNull()
        if (open == null) {
            return closedBursts.toList()
        }
        return closedBursts + open
    }

    private fun openBurstOrNull(): Burst? {
        if (!burstOpen) {
            return null
        }
        val duration = burstLastActiveMs - burstStartMs
        if (duration < config.minBurstMs) {
            return null
        }
        return Burst(burstStartMs, burstLastActiveMs)
    }

    private fun openBurst(timestampMs: Long) {
        burstOpen = true
        burstStartMs = timestampMs
        burstLastActiveMs = timestampMs
    }

    private fun closeBurst() {
        openBurstOrNull()?.let { closedBursts.add(it) }
        burstOpen = false
    }

    private fun updateGravity(sample: MotionSample) {
        val keep = config.gravityAlpha
        val take = 1f - keep
        gravityX = keep * gravityX + take * sample.x
        gravityY = keep * gravityY + take * sample.y
        gravityZ = keep * gravityZ + take * sample.z
    }

    private fun linearMagnitude(sample: MotionSample): Float {
        val dx = sample.x - gravityX
        val dy = sample.y - gravityY
        val dz = sample.z - gravityZ
        return sqrt(dx * dx + dy * dy + dz * dz)
    }

    private fun turningRateDegPerSec(sample: MotionSample): Float {
        val previous = sqrt(gravityX * gravityX + gravityY * gravityY + gravityZ * gravityZ)
        val current = sqrt(sample.x * sample.x + sample.y * sample.y + sample.z * sample.z)
        if (previous < 1f || current < 1f) {
            return 0f
        }
        val dot = (gravityX * sample.x + gravityY * sample.y + gravityZ * sample.z) / (previous * current)
        val clamped = dot.coerceIn(-1f, 1f)
        val dtMs = sample.timestampMs - lastTimestampMs
        if (dtMs <= 0L) {
            return 0f
        }
        val degrees = Math.toDegrees(acos(clamped.toDouble())).toFloat()
        return degrees / (dtMs / 1000f)
    }

    private data class Burst(val startMs: Long, val endMs: Long) {
        val durationMs: Long get() = endMs - startMs
    }
}
