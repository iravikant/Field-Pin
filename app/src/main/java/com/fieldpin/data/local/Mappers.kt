package com.fieldpin.data.local

import com.fieldpin.core.AppJson
import com.fieldpin.domain.gis.GeoMath
import com.fieldpin.domain.model.Condition
import com.fieldpin.domain.model.GeoPoint
import com.fieldpin.domain.model.Inspection
import com.fieldpin.domain.model.Site
import com.fieldpin.domain.model.SiteCategory
import com.fieldpin.domain.model.SiteStatus
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer

private val pointListSerializer = ListSerializer(GeoPoint.serializer())
private val stringListSerializer = ListSerializer(String.serializer())

fun encodeBoundary(points: List<GeoPoint>): String =
    AppJson.json.encodeToString(pointListSerializer, points)

fun decodeBoundary(json: String): List<GeoPoint> =
    runCatching { AppJson.json.decodeFromString(pointListSerializer, json) }.getOrDefault(emptyList())

fun String.toFieldSet(): Set<String> = split(',').filter { it.isNotBlank() }.toSet()

fun Set<String>.toCsv(): String = joinToString(",")

fun SiteEntity.toDomain(pendingInspections: Int = 0): Site = Site(
    id = id,
    name = name,
    category = SiteCategory.parse(category),
    status = SiteStatus.parse(status),
    description = description,
    boundary = decodeBoundary(boundaryJson),
    areaSqm = areaSqm,
    perimeterM = perimeterM,
    centroid = GeoPoint(centroidLat, centroidLng),
    lastInspectionAt = lastInspectionAt,
    version = version,
    dirtyFields = dirtyFields.toFieldSet(),
    editedAt = editedAt,
    pendingInspections = pendingInspections,
)

fun SiteRow.toDomain(): Site = site.toDomain(pendingInspections)

/** Builds an entity with derived geometry (area, perimeter, centroid) computed from the boundary. */
fun buildSiteEntity(
    id: String,
    name: String,
    category: String,
    status: String,
    description: String,
    boundary: List<GeoPoint>,
    lastInspectionAt: Long?,
    version: Int,
    dirtyFields: Set<String>,
    editedAt: Long,
): SiteEntity {
    val centroid = GeoMath.centroid(boundary)
    return SiteEntity(
        id = id,
        name = name,
        category = category,
        status = status,
        description = description,
        boundaryJson = encodeBoundary(boundary),
        areaSqm = GeoMath.areaSqm(boundary),
        perimeterM = GeoMath.perimeterM(boundary),
        centroidLat = centroid.lat,
        centroidLng = centroid.lng,
        lastInspectionAt = lastInspectionAt,
        version = version,
        dirtyFields = dirtyFields.toCsv(),
        editedAt = editedAt,
    )
}

fun decodePhotos(json: String): List<String> =
    runCatching { AppJson.json.decodeFromString(stringListSerializer, json) }.getOrDefault(emptyList())

fun encodePhotos(paths: List<String>): String = AppJson.json.encodeToString(stringListSerializer, paths)

fun InspectionEntity.toDomain(): Inspection = Inspection(
    id = id,
    siteId = siteId,
    siteName = siteName,
    checkInAt = checkInAt,
    checkInPoint = GeoPoint(checkInLat, checkInLng),
    checkInAccuracyM = checkInAccuracyM,
    condition = Condition.parse(condition),
    notes = notes,
    photos = decodePhotos(photosJson),
    flagNeedsRepair = flagNeedsRepair,
    submittedAt = submittedAt,
    synced = synced,
)
