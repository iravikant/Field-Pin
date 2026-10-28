package com.fieldpin.ui.common

import com.fieldpin.core.Fmt
import com.fieldpin.data.repo.InspectionRepository
import com.fieldpin.data.repo.StartResult
import com.fieldpin.domain.gis.CheckInPolicy
import com.fieldpin.domain.gis.CheckInStatus
import com.fieldpin.domain.model.LocationFix
import com.fieldpin.domain.model.SiteField

/** One-line explanation of the geofence state, shared by the map sheet, detail and toasts. */
fun CheckInStatus.headline(): String = when (this) {
    CheckInStatus.NoLocation -> "Waiting for your location…"
    is CheckInStatus.WeakFix ->
        "GPS accuracy is too low (${Fmt.accuracy(accuracyM)}). Needs ${CheckInPolicy.MAX_ACCURACY_M.toInt()} m or better"
    is CheckInStatus.Outside -> "${Fmt.distance(distanceM)} away from the boundary"
    is CheckInStatus.Inside -> "You're inside · GPS ${Fmt.accuracy(accuracyM)}"
}

fun CheckInStatus.buttonLabel(): String = when (this) {
    is CheckInStatus.Inside -> "Check in & start inspection"
    is CheckInStatus.Outside -> "Go inside to check in"
    is CheckInStatus.WeakFix -> "Waiting for a better GPS fix"
    CheckInStatus.NoLocation -> "Waiting for location"
}

fun CheckInStatus.isGood(): Boolean = this is CheckInStatus.Inside

/** Events a screen's ViewModel sends to its composable. */
sealed interface UiEvent {
    data class OpenInspection(val inspectionId: String) : UiEvent
    data class Message(val text: String) : UiEvent
}

fun fieldLabel(field: String): String = when (field) {
    SiteField.NAME -> "name"
    SiteField.CATEGORY -> "category"
    SiteField.STATUS -> "status"
    SiteField.DESCRIPTION -> "description"
    SiteField.LAST_INSPECTION -> "last inspection date"
    SiteField.BOUNDARY -> "boundary"
    else -> field
}


/** Starts (or resumes) an inspection and converts the outcome to a UI event. Shared by map and detail. */
suspend fun InspectionRepository.startAsEvent(siteId: String, fix: LocationFix?): UiEvent =
    when (val result = startInspection(siteId, fix)) {
        is StartResult.Started -> UiEvent.OpenInspection(result.inspectionId)
        is StartResult.Denied -> UiEvent.Message(result.status.headline())
    }
