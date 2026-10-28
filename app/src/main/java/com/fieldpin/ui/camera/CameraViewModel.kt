package com.fieldpin.ui.camera

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fieldpin.data.camera.PhotoStamper
import com.fieldpin.data.camera.StampInfo
import com.fieldpin.data.location.LiveLocation
import com.fieldpin.data.location.LocationTracker
import com.fieldpin.data.repo.InspectionRepository
import com.fieldpin.data.repo.PhotoStorage
import com.fieldpin.domain.model.Inspection
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class CameraViewModel(
    private val inspectionId: String,
    private val repo: InspectionRepository,
    private val stamper: PhotoStamper,
    private val storage: PhotoStorage,
    tracker: LocationTracker,
) : ViewModel() {
    val live = LiveLocation(tracker, viewModelScope)

    val inspection: StateFlow<Inspection?> = repo.observe(inspectionId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    fun newPhotoFile(): File = storage.newPhotoFile(inspectionId)

    /** CameraX saved [file]; stamp it, then attach it to the inspection draft. */
    fun onCaptured(file: File, onError: (String) -> Unit) {
        viewModelScope.launch {
            _busy.value = true
            val draft = repo.get(inspectionId)
            val fix = live.fix.value
            val info = StampInfo(
                siteName = draft?.siteName ?: "Site",
                point = fix?.point ?: draft?.checkInPoint ?: com.fieldpin.domain.model.GeoPoint(0.0, 0.0),
                accuracyM = fix?.accuracyM ?: draft?.checkInAccuracyM ?: 0f,
                timeMs = System.currentTimeMillis(),
                note = if (fix == null) "Position of check-in (live GPS unavailable)" else null,
            )
            try {
                withContext(Dispatchers.Default) { stamper.stamp(file, info) }
                repo.addPhoto(inspectionId, file.absolutePath)
            } catch (e: Exception) {
                file.delete()
                onError(e.message ?: "Could not save the photo")
            } finally {
                _busy.value = false
            }
        }
    }
}
