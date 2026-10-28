package com.fieldpin.ui.map

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fieldpin.data.location.LiveLocation
import com.fieldpin.data.location.LocationTracker
import com.fieldpin.data.repo.InspectionRepository
import com.fieldpin.data.repo.SiteRepository
import com.fieldpin.domain.gis.CheckInPolicy
import com.fieldpin.domain.gis.CheckInStatus
import com.fieldpin.domain.gis.GeoMath
import com.fieldpin.domain.model.GeoPoint
import com.fieldpin.domain.model.Site
import com.fieldpin.ui.common.UiEvent
import com.fieldpin.ui.common.startAsEvent
import com.google.android.gms.maps.model.CameraPosition
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class MapViewModel(
    repo: SiteRepository,
    tracker: LocationTracker,
    private val inspections: InspectionRepository,
) : ViewModel() {
    data class Selected(val site: Site, val checkIn: CheckInStatus)

    val live = LiveLocation(tracker, viewModelScope)

    val sites: StateFlow<List<Site>> = repo.observeSites("", null)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val selectedId = MutableStateFlow<String?>(null)

    /** The tapped site plus whether the user may check in right now (recomputed on every GPS fix). */
    val selected: StateFlow<Selected?> = combine(sites, selectedId, live.fix) { all, id, fix ->
        all.firstOrNull { it.id == id }?.let { Selected(it, CheckInPolicy.evaluate(it.boundary, fix)) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _events = Channel<UiEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    /** Camera to restore when the user comes back to this tab. */
    var camera: CameraPosition? = null

    /** Picks the smallest polygon containing the tap (so nested sites stay selectable). */
    fun onMapTap(point: GeoPoint) {
        selectedId.value = sites.value
            .filter { GeoMath.contains(it.boundary, point) }
            .minByOrNull { it.areaSqm }
            ?.id
    }

    fun select(siteId: String) {
        selectedId.value = siteId
    }

    fun clearSelection() {
        selectedId.value = null
    }

    fun checkIn(siteId: String) {
        viewModelScope.launch { _events.send(inspections.startAsEvent(siteId, live.fix.value)) }
    }
}
