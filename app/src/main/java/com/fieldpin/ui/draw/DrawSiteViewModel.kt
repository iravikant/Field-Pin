package com.fieldpin.ui.draw

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fieldpin.core.Fmt
import com.fieldpin.data.location.LiveLocation
import com.fieldpin.data.location.LocationTracker
import com.fieldpin.data.repo.SiteRepository
import com.fieldpin.domain.gis.GeoMath
import com.fieldpin.domain.model.GeoPoint
import com.fieldpin.domain.model.Site
import com.fieldpin.domain.model.SiteCategory
import com.fieldpin.domain.model.SiteStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class DrawSiteViewModel(
    private val repo: SiteRepository,
    tracker: LocationTracker,
) : ViewModel() {
    data class Metrics(
        val pointCount: Int = 0,
        val areaSqm: Double = 0.0,
        val perimeterM: Double = 0.0,
        val selfIntersects: Boolean = false,
    ) {
        /** Why the boundary cannot be saved yet, or null when it is valid. */
        val problem: String?
            get() = when {
                pointCount < MIN_POINTS -> "Add at least $MIN_POINTS corners (${MIN_POINTS - pointCount} more)"
                selfIntersects -> "The boundary crosses itself. Undo the last corner."
                areaSqm < MIN_AREA_SQM -> "The boundary is too small to be a site"
                else -> null
            }
    }

    val live = LiveLocation(tracker, viewModelScope)

    /** Existing sites are shown faintly for context. */
    val sites: StateFlow<List<Site>> = repo.observeSites("", null)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _points = MutableStateFlow<List<GeoPoint>>(emptyList())
    val points: StateFlow<List<GeoPoint>> = _points.asStateFlow()

    val metrics: StateFlow<Metrics> = _points
        .map { pts ->
            val closed = pts.size >= MIN_POINTS
            Metrics(
                pointCount = pts.size,
                areaSqm = if (closed) GeoMath.areaSqm(pts) else 0.0,
                perimeterM = if (closed) GeoMath.perimeterM(pts) else GeoMath.pathLengthM(pts),
                selfIntersects = GeoMath.hasSelfIntersection(pts),
            )
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, Metrics())

    fun addPoint(p: GeoPoint) {
        _points.update { current ->
            val last = current.lastOrNull()
            if (last != null && GeoMath.distanceM(last, p) < MIN_POINT_SPACING_M) current else current + p
        }
    }

    /** Adds the current GPS position as a corner. Returns an error message, or null on success. */
    fun addGpsPoint(): String? {
        val fix = live.fix.value ?: return "Still waiting for a GPS fix…"
        if (fix.accuracyM > MAX_GPS_ACCURACY_M) {
            return "GPS accuracy ${Fmt.accuracy(fix.accuracyM)} is worse than ${MAX_GPS_ACCURACY_M.toInt()} m. " +
                "Move to open sky and try again."
        }
        addPoint(fix.point)
        return null
    }

    /** A corner was dragged on the map. */
    fun movePoint(index: Int, p: GeoPoint) {
        _points.update { current ->
            if (index !in current.indices || current[index] == p) current
            else current.toMutableList().also { it[index] = p }
        }
    }

    fun undo() = _points.update { it.dropLast(1) }

    fun clear() {
        _points.value = emptyList()
    }

    fun save(name: String, category: SiteCategory, status: SiteStatus, description: String, onSaved: () -> Unit) {
        val boundary = _points.value
        if (metrics.value.problem != null || name.isBlank()) return
        viewModelScope.launch {
            repo.createSite(name, category, status, description, boundary)
            onSaved()
        }
    }

    companion object {
        const val MIN_POINTS = 3
        const val MIN_AREA_SQM = 1.0
        const val MIN_POINT_SPACING_M = 0.5

        /** GPS readings worse than this are ignored when adding a corner from the device position. */
        const val MAX_GPS_ACCURACY_M = 20f
    }
}
