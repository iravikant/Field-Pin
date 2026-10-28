package com.fieldpin.data.repo

import androidx.room.withTransaction
import com.fieldpin.data.local.FieldPinDatabase
import com.fieldpin.data.local.InspectionDao
import com.fieldpin.data.local.InspectionEntity
import com.fieldpin.data.local.SiteDao
import com.fieldpin.data.local.decodeBoundary
import com.fieldpin.data.local.decodePhotos
import com.fieldpin.data.local.encodePhotos
import com.fieldpin.data.local.toCsv
import com.fieldpin.data.local.toDomain
import com.fieldpin.data.local.toFieldSet
import com.fieldpin.domain.gis.CheckInPolicy
import com.fieldpin.domain.gis.CheckInStatus
import com.fieldpin.domain.model.Condition
import com.fieldpin.domain.model.Inspection
import com.fieldpin.domain.model.LocationFix
import com.fieldpin.domain.model.SiteField
import com.fieldpin.domain.model.SiteStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.io.File
import java.util.UUID

sealed interface StartResult {
    data class Started(val inspectionId: String) : StartResult
    data class Denied(val status: CheckInStatus) : StartResult
}

class InspectionRepository(
    private val db: FieldPinDatabase,
    private val dao: InspectionDao,
    private val siteDao: SiteDao,
    private val photos: PhotoStorage,
) {
    fun observe(id: String): Flow<Inspection?> = dao.observeById(id).map { it?.toDomain() }

    suspend fun get(id: String): Inspection? = dao.getById(id)?.toDomain()

    fun observeSubmitted(siteId: String): Flow<List<Inspection>> =
        dao.observeSubmittedForSite(siteId).map { list -> list.map { it.toDomain() } }

    /**
     * Re-checks the geofence against the *stored* boundary, then creates the check-in (or resumes
     * an unfinished draft of the same site). The UI's "inside" state is never trusted on its own.
     */
    suspend fun startInspection(siteId: String, fix: LocationFix?, now: Long = System.currentTimeMillis()): StartResult =
        db.withTransaction {
            val site = siteDao.getById(siteId)
                ?: return@withTransaction StartResult.Denied(CheckInStatus.NoLocation)
            val status = CheckInPolicy.evaluate(decodeBoundary(site.boundaryJson), fix)
            if (!status.canCheckIn || fix == null) return@withTransaction StartResult.Denied(status)

            dao.getOpenDraft(siteId)?.let { return@withTransaction StartResult.Started(it.id) }
            val id = UUID.randomUUID().toString()
            dao.upsert(
                InspectionEntity(
                    id = id,
                    siteId = siteId,
                    siteName = site.name,
                    checkInAt = now,
                    checkInLat = fix.point.lat,
                    checkInLng = fix.point.lng,
                    checkInAccuracyM = fix.accuracyM,
                    condition = null,
                    notes = "",
                    photosJson = encodePhotos(emptyList()),
                    flagNeedsRepair = false,
                    submittedAt = null,
                    synced = false,
                )
            )
            StartResult.Started(id)
        }

    private suspend fun modify(id: String, change: (InspectionEntity) -> InspectionEntity) = db.withTransaction {
        val current = dao.getById(id) ?: return@withTransaction
        if (current.submittedAt != null) return@withTransaction // submitted inspections are immutable
        dao.upsert(change(current))
    }

    suspend fun setCondition(id: String, condition: Condition) =
        modify(id) { it.copy(condition = condition.name, flagNeedsRepair = condition == Condition.POOR) }

    suspend fun setFlagNeedsRepair(id: String, flag: Boolean) = modify(id) { it.copy(flagNeedsRepair = flag) }

    suspend fun setNotes(id: String, notes: String) = modify(id) { it.copy(notes = notes) }

    suspend fun addPhoto(id: String, path: String) =
        modify(id) { it.copy(photosJson = encodePhotos(decodePhotos(it.photosJson) + path)) }

    suspend fun removePhoto(id: String, path: String) {
        modify(id) { it.copy(photosJson = encodePhotos(decodePhotos(it.photosJson) - path)) }
        File(path).delete()
    }

    suspend fun discardDraft(id: String) {
        val current = dao.getById(id) ?: return
        if (current.submittedAt == null) {
            dao.delete(id)
            photos.deleteInspection(id)
        }
    }

    /** Marks the draft submitted and queues it (and the site's new state) for sync, atomically. */
    suspend fun submit(id: String, notes: String, now: Long = System.currentTimeMillis()): Boolean =
        db.withTransaction {
            val current = dao.getById(id) ?: return@withTransaction false
            if (current.submittedAt != null || current.condition == null) return@withTransaction false
            dao.upsert(current.copy(notes = notes, submittedAt = now, synced = false))

            val site = siteDao.getById(current.siteId) ?: return@withTransaction true
            var dirty = site.dirtyFields.toFieldSet() + SiteField.LAST_INSPECTION
            var status = site.status
            if (current.flagNeedsRepair && status == SiteStatus.ACTIVE.name) {
                status = SiteStatus.NEEDS_REPAIR.name
                dirty = dirty + SiteField.STATUS
            }
            siteDao.upsert(
                site.copy(
                    lastInspectionAt = maxOf(site.lastInspectionAt ?: 0L, current.checkInAt),
                    status = status,
                    dirtyFields = dirty.toCsv(),
                    editedAt = now,
                )
            )
            true
        }
}
