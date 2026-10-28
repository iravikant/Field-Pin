package com.fieldpin.domain.gis

import com.fieldpin.domain.model.GeoPoint
import com.fieldpin.domain.model.LocationFix
import kotlin.math.max
import kotlin.math.sqrt

/**
 * Turns the raw, jittery GPS stream into a steady "current position", the way fitness apps do.
 *
 * - A Kalman filter weighs every reading by its reported accuracy, so a poor reading barely moves the dot.
 * - How much the position is allowed to move between readings depends on speed: standing still the dot
 *   locks in and stops wandering, walking it follows quickly.
 * - Bad readings are dropped: out-of-order timestamps, much worse accuracy than the current estimate, and
 *   jumps no person could make. If the jump keeps repeating (you really did move, e.g. out of a tunnel)
 *   the filter restarts from the new place.
 *
 * Pure Kotlin so it can be unit tested. One instance per stream; not thread-safe.
 */
class LocationSmoother {
    private var lat = 0.0
    private var lng = 0.0
    /** Variance of the estimate in m²; negative = no estimate yet. */
    private var variance = -1.0
    private var lastTimeMs = 0L
    private var speedMps = 0f
    private var rejectedInARow = 0

    /** Returns the smoothed fix, or null when [fix] was rejected (keep showing the previous one). */
    fun process(fix: LocationFix): LocationFix? {
        val acc = fix.accuracyM.coerceIn(MIN_ACCURACY_M, MAX_ACCURACY_M)

        if (variance < 0) return start(fix, acc)

        val dtS = (fix.timeMs - lastTimeMs) / 1000.0
        if (dtS <= 0.0) return null // duplicate or out of order
        if (dtS > RESTART_AFTER_S) return start(fix, acc) // long gap: the old estimate is meaningless

        val jumpM = GeoMath.distanceM(GeoPoint(lat, lng), fix.point)
        val impliedSpeed = jumpM / dtS
        val estimateAcc = sqrt(variance)
        val worseThanWeKnow = acc > MAX_USABLE_ACCURACY_M && acc > estimateAcc
        val impossibleJump = impliedSpeed > MAX_SPEED_MPS && jumpM > 2 * (acc + estimateAcc)
        if (worseThanWeKnow || impossibleJump) {
            rejectedInARow++
            if (rejectedInARow < RESTART_AFTER_REJECTS || worseThanWeKnow) return null
            return start(fix, acc)
        }
        rejectedInARow = 0

        val measuredSpeed = fix.speedMps ?: (jumpM / dtS).toFloat().coerceAtMost(MAX_SPEED_MPS.toFloat())
        speedMps = SPEED_BLEND * measuredSpeed + (1 - SPEED_BLEND) * speedMps
        val q = max(speedMps, MIN_SPEED_MPS) * PROCESS_GAIN
        variance += dtS * q * q

        val k = variance / (variance + acc * acc)
        lat += k * (fix.point.lat - lat)
        lng += k * (fix.point.lng - lng)
        variance *= (1 - k)
        lastTimeMs = fix.timeMs
        return output(fix, acc)
    }

    fun reset() {
        variance = -1.0
        speedMps = 0f
        rejectedInARow = 0
    }

    private fun start(fix: LocationFix, acc: Float): LocationFix {
        lat = fix.point.lat
        lng = fix.point.lng
        variance = acc.toDouble() * acc
        lastTimeMs = fix.timeMs
        speedMps = 0f
        rejectedInARow = 0
        return output(fix, acc)
    }

    /**
     * Reported accuracy is never better than half of what the chip said: GPS errors are correlated, so
     * averaging does not shrink them as fast as the maths suggests, and check-in must not be fooled.
     */
    private fun output(fix: LocationFix, acc: Float) = LocationFix(
        point = GeoPoint(lat, lng),
        accuracyM = max(sqrt(variance).toFloat(), acc * 0.5f),
        timeMs = fix.timeMs,
        speedMps = fix.speedMps,
    )

    companion object {
        const val MIN_ACCURACY_M = 1f
        const val MAX_ACCURACY_M = 500f
        /** Readings worse than this are ignored once there is a better estimate. */
        const val MAX_USABLE_ACCURACY_M = 60f
        const val MAX_SPEED_MPS = 60.0
        const val MIN_SPEED_MPS = 0.5f
        const val PROCESS_GAIN = 2f
        const val SPEED_BLEND = 0.3f
        const val RESTART_AFTER_S = 15.0
        const val RESTART_AFTER_REJECTS = 3
    }
}
