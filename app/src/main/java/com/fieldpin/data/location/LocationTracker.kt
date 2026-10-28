package com.fieldpin.data.location

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.location.LocationManager
import android.os.Looper
import android.os.SystemClock
import androidx.core.location.LocationManagerCompat
import com.fieldpin.domain.gis.LocationSmoother
import com.fieldpin.domain.model.GeoPoint
import com.fieldpin.domain.model.LocationFix
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn

/**
 * Foreground-only location via the Fused Location Provider. Updates are requested when a
 * collector appears and removed the moment the last collector goes away (the screens collect
 * with `collectAsStateWithLifecycle`, so this stops when the screen is not visible).
 * No background location permission is declared or requested anywhere.
 */
class LocationTracker(private val context: Context) {
    private val client = LocationServices.getFusedLocationProviderClient(context)

    /** Debug-only override used by the "Simulate location" developer tool. Null = real GPS. */
    private val _simulated = MutableStateFlow<LocationFix?>(null)
    val simulated: StateFlow<LocationFix?> = _simulated

    fun simulate(point: GeoPoint?) {
        _simulated.value = point?.let { LocationFix(it, accuracyM = 5f, timeMs = System.currentTimeMillis()) }
    }

    fun isLocationEnabled(): Boolean {
        val manager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        return LocationManagerCompat.isLocationEnabled(manager)
    }

    @SuppressLint("MissingPermission") // callers only collect after the permission is granted
    private fun realUpdates(): Flow<LocationFix> = callbackFlow {
        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, UPDATE_INTERVAL_MS)
            .setMinUpdateIntervalMillis(FASTEST_INTERVAL_MS)
            .setWaitForAccurateLocation(false)
            .build()
        val callback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                // Fused can hand over a cached position first; a stale one would put the dot in the wrong place.
                result.locations
                    .filter { SystemClock.elapsedRealtimeNanos() - it.elapsedRealtimeNanos <= MAX_AGE_NS }
                    .forEach { trySend(it.toFix()) }
            }
        }
        try {
            client.requestLocationUpdates(request, callback, Looper.getMainLooper())
        } catch (e: SecurityException) {
            close(e)
        }
        awaitClose { client.removeLocationUpdates(callback) }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    fun fixes(): Flow<LocationFix> = _simulated.flatMapLatest { sim ->
        if (sim != null) {
            flowOf(sim)
        } else {
            // A fresh smoother per collector: each screen starts clean and never sees another screen's history.
            val smoother = LocationSmoother()
            realUpdates().mapNotNull { smoother.process(it) }
        }
    }

    private fun Location.toFix() = LocationFix(
        point = GeoPoint(latitude, longitude),
        accuracyM = if (hasAccuracy()) accuracy else Float.MAX_VALUE,
        timeMs = time,
        speedMps = if (hasSpeed()) speed else null,
    )

    private companion object {
        // 1 Hz like fitness apps; the smoother needs steady readings to settle quickly.
        const val UPDATE_INTERVAL_MS = 1_000L
        const val FASTEST_INTERVAL_MS = 500L
        const val MAX_AGE_NS = 10_000_000_000L
    }
}

/**
 * Per-screen helper: exposes the latest fix as a StateFlow that is only active while the screen
 * is collected AND the location permission is granted ([granted]).
 */
class LiveLocation(tracker: LocationTracker, scope: CoroutineScope) {
    val granted = MutableStateFlow(false)

    @OptIn(ExperimentalCoroutinesApi::class)
    val fix: StateFlow<LocationFix?> = granted
        .flatMapLatest { ok ->
            if (ok) tracker.fixes().catch { /* permission revoked mid-stream: just stop */ } else flowOf(null)
        }
        .stateIn(scope, SharingStarted.WhileSubscribed(stopTimeoutMillis = 1_000), null)
}
