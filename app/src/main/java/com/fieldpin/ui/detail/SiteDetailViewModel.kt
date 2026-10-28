package com.fieldpin.ui.detail

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fieldpin.data.export.GeoJsonExporter
import com.fieldpin.data.location.LiveLocation
import com.fieldpin.data.location.LocationTracker
import com.fieldpin.data.repo.DocumentRepository
import com.fieldpin.data.repo.InspectionRepository
import com.fieldpin.data.repo.SiteRepository
import com.fieldpin.domain.gis.CheckInPolicy
import com.fieldpin.domain.gis.CheckInStatus
import com.fieldpin.domain.model.Inspection
import com.fieldpin.domain.model.Site
import com.fieldpin.domain.model.SiteDocument
import com.fieldpin.domain.model.SiteCategory
import com.fieldpin.domain.model.SiteStatus
import com.fieldpin.ui.common.UiEvent
import com.fieldpin.ui.common.startAsEvent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SiteDetailViewModel(
    private val siteId: String,
    private val repo: SiteRepository,
    private val inspections: InspectionRepository,
    tracker: LocationTracker,
    private val exporter: GeoJsonExporter,
    private val documentRepo: DocumentRepository,
) : ViewModel() {
    val live = LiveLocation(tracker, viewModelScope)

    val site: StateFlow<Site?> = repo.observeSite(siteId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val history: StateFlow<List<Inspection>> = inspections.observeSubmitted(siteId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val documents: StateFlow<List<SiteDocument>> = documentRepo.observe(siteId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val checkIn: StateFlow<CheckInStatus> = combine(site, live.fix) { s, fix ->
        if (s == null) CheckInStatus.NoLocation else CheckInPolicy.evaluate(s.boundary, fix)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CheckInStatus.NoLocation)

    private val _events = Channel<UiEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    fun setStatus(status: SiteStatus) {
        viewModelScope.launch { repo.updateStatus(siteId, status) }
    }

    fun saveDetails(name: String, category: SiteCategory, description: String) {
        if (name.isBlank()) return
        viewModelScope.launch { repo.updateDetails(siteId, name, category, description) }
    }

    fun startInspection() {
        viewModelScope.launch { _events.send(inspections.startAsEvent(siteId, live.fix.value)) }
    }

    fun export(onReady: (Uri) -> Unit) {
        val current = site.value ?: return
        viewModelScope.launch {
            val uri = withContext(Dispatchers.IO) { exporter.export(current) }
            onReady(uri)
        }
    }

    fun attachDocuments(uris: List<Uri>) {
        if (uris.isEmpty()) return
        viewModelScope.launch {
            val report = documentRepo.attach(siteId, uris)
            val message = when {
                report.errors.isNotEmpty() -> report.errors.first() +
                    if (report.added > 0) " (${report.added} attached)" else ""
                report.added == 1 -> "File attached"
                else -> "${report.added} files attached"
            }
            _events.send(UiEvent.Message(message))
        }
    }

    fun removeDocument(id: String) {
        viewModelScope.launch { documentRepo.remove(id) }
    }
}
