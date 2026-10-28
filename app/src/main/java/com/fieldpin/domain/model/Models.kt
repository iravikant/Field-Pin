package com.fieldpin.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class GeoPoint(val lat: Double, val lng: Double)

/** A single GPS reading. [accuracyM] is the 68% horizontal radius reported by the provider. */
data class LocationFix(val point: GeoPoint, val accuracyM: Float, val timeMs: Long, val speedMps: Float? = null)

enum class SiteStatus(val label: String, val filterLabel: String) {
    ACTIVE("Active", "Active"),
    NEEDS_REPAIR("Needs repair", "Repair"),
    CLOSED("Closed", "Closed");

    companion object {
        fun parse(value: String?): SiteStatus = entries.firstOrNull { it.name == value } ?: ACTIVE
    }
}

enum class SiteCategory(val label: String) {
    PARK("Park"),
    WATER_TANK("Water tank"),
    SOLAR_SITE("Solar site"),
    SUBSTATION("Substation"),
    BUILDING("Building"),
    OTHER("Other");

    companion object {
        fun parse(value: String?): SiteCategory = entries.firstOrNull { it.name == value } ?: OTHER
    }
}

enum class Condition(val label: String) {
    GOOD("Good"),
    FAIR("Fair"),
    POOR("Poor");

    companion object {
        fun parse(value: String?): Condition? = entries.firstOrNull { it.name == value }
    }
}

/** Names of the site fields that are synced individually (used for field-level conflict merge). */
object SiteField {
    const val CREATED = "created"
    const val NAME = "name"
    const val CATEGORY = "category"
    const val STATUS = "status"
    const val DESCRIPTION = "description"
    const val LAST_INSPECTION = "lastInspectionAt"
    const val BOUNDARY = "boundary"

    /** Fields that can be sent in an update (create sends all of them). */
    val ALL = listOf(NAME, CATEGORY, STATUS, DESCRIPTION, LAST_INSPECTION, BOUNDARY)
}

data class Site(
    val id: String,
    val name: String,
    val category: SiteCategory,
    val status: SiteStatus,
    val description: String,
    val boundary: List<GeoPoint>,
    val areaSqm: Double,
    val perimeterM: Double,
    val centroid: GeoPoint,
    val lastInspectionAt: Long?,
    /** Server version this copy is based on (0 = never synced). */
    val version: Int,
    /** Fields edited locally and not yet accepted by the server. */
    val dirtyFields: Set<String>,
    val editedAt: Long,
    /** Submitted inspections of this site that are not synced yet. */
    val pendingInspections: Int = 0,
) {
    val hasPendingSync: Boolean get() = dirtyFields.isNotEmpty() || pendingInspections > 0
}

data class Inspection(
    val id: String,
    val siteId: String,
    val siteName: String,
    val checkInAt: Long,
    val checkInPoint: GeoPoint,
    val checkInAccuracyM: Float,
    val condition: Condition?,
    val notes: String,
    /** Absolute paths of stamped JPEGs in app-private storage. */
    val photos: List<String>,
    val flagNeedsRepair: Boolean,
    val submittedAt: Long?,
    val synced: Boolean,
) {
    val isDraft: Boolean get() = submittedAt == null
}

data class UserSession(val email: String, val name: String, val token: String)

enum class ThemeMode(val label: String) { SYSTEM("System"), DARK("Dark"), LIGHT("Light") }

data class SyncConflict(
    val siteId: String,
    val siteName: String,
    val field: String,
    val yours: String?,
    val theirs: String?,
    val yoursWon: Boolean,
)

data class SyncReport(
    val pushedSites: Int,
    val pushedInspections: Int,
    val pulledSites: Int,
    val conflicts: List<SyncConflict>,
    val finishedAt: Long,
)


enum class DocumentKind {
    IMAGE, PDF, TEXT, OTHER;

    companion object {
        private val textExt = setOf("txt", "md", "csv", "json", "xml", "log", "geojson", "kml", "yaml", "yml")

        fun of(mimeType: String, name: String): DocumentKind {
            val ext = name.substringAfterLast('.', "").lowercase()
            return when {
                mimeType.startsWith("image/") -> IMAGE
                mimeType == "application/pdf" || ext == "pdf" -> PDF
                mimeType.startsWith("text/") || mimeType == "application/json" || ext in textExt -> TEXT
                else -> OTHER
            }
        }
    }
}

/** A file attached to a site (kept on this device only, never synced). */
data class SiteDocument(
    val id: String,
    val siteId: String,
    val name: String,
    val mimeType: String,
    val sizeBytes: Long,
    val path: String,
    val addedAt: Long,
) {
    val kind: DocumentKind get() = DocumentKind.of(mimeType, name)
}
