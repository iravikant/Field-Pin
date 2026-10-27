package com.fieldpin.data.remote

import com.fieldpin.domain.model.GeoPoint
import kotlinx.serialization.Serializable

@Serializable
data class AuthResponse(val token: String, val name: String, val email: String)

@Serializable
data class SiteDto(
    val id: String,
    val name: String,
    val category: String,
    val status: String,
    val description: String = "",
    val boundary: List<GeoPoint>,
    val lastInspectionAt: Long? = null,
    /** Bumped by the server on every accepted change. */
    val version: Int = 1,
    /** Server version at which each field last changed (drives conflict detection). */
    val fieldVersions: Map<String, Int> = emptyMap(),
    /** Device time of the edit that last changed each field (drives last-writer-wins). */
    val fieldEditedAt: Map<String, Long> = emptyMap(),
)

/** Only the names listed in [fields] are meaningful; the rest of the properties are ignored. */
@Serializable
data class SiteChangeDto(
    val siteId: String,
    /** The server version the client's copy was based on. */
    val baseVersion: Int,
    val isCreate: Boolean,
    val editedAt: Long,
    val fields: Set<String>,
    val name: String? = null,
    val category: String? = null,
    val status: String? = null,
    val description: String? = null,
    val lastInspectionAt: Long? = null,
    val boundary: List<GeoPoint>? = null,
)

@Serializable
data class ConflictDto(val field: String, val yours: String?, val theirs: String?, val yoursWon: Boolean)

@Serializable
data class SitePushResponse(val site: SiteDto, val conflicts: List<ConflictDto> = emptyList())

@Serializable
data class InspectionDto(
    val id: String,
    val siteId: String,
    val checkInAt: Long,
    val checkInLat: Double,
    val checkInLng: Double,
    val checkInAccuracyM: Float,
    val condition: String?,
    val notes: String,
    /** The mock server stores file names only; a real backend would receive the JPEG uploads. */
    val photoFileNames: List<String>,
    val submittedAt: Long,
)

@Serializable
data class UserDto(val email: String, val name: String, val passwordSha256: String)

@Serializable
data class ServerState(
    val users: List<UserDto> = emptyList(),
    val sites: List<SiteDto> = emptyList(),
    val inspections: List<InspectionDto> = emptyList(),
)
