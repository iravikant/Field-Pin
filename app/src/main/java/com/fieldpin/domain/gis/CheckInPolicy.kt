package com.fieldpin.domain.gis

import com.fieldpin.domain.model.GeoPoint
import com.fieldpin.domain.model.LocationFix

sealed interface CheckInStatus {
    /** No GPS fix yet (permission missing, GPS off, or still acquiring). */
    data object NoLocation : CheckInStatus

    /** Inside the boundary but the fix is too imprecise to trust. */
    data class WeakFix(val accuracyM: Float) : CheckInStatus

    data class Outside(val distanceM: Double) : CheckInStatus

    data class Inside(val accuracyM: Float) : CheckInStatus

    val canCheckIn: Boolean get() = this is Inside
}

/** Single place that decides whether an officer may start an inspection. */
object CheckInPolicy {
    /** Fixes worse than this are not trusted for check-in. */
    const val MAX_ACCURACY_M = 50f

    fun evaluate(boundary: List<GeoPoint>, fix: LocationFix?): CheckInStatus {
        if (fix == null) return CheckInStatus.NoLocation
        if (!GeoMath.contains(boundary, fix.point)) {
            return CheckInStatus.Outside(GeoMath.distanceToPolygonM(boundary, fix.point))
        }
        return if (fix.accuracyM > MAX_ACCURACY_M) CheckInStatus.WeakFix(fix.accuracyM) else CheckInStatus.Inside(fix.accuracyM)
    }
}
