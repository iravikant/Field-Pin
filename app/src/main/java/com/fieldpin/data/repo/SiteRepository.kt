package com.fieldpin.data.repo

import androidx.room.withTransaction
import com.fieldpin.data.local.FieldPinDatabase
import com.fieldpin.data.local.SiteDao
import com.fieldpin.data.local.buildSiteEntity
import com.fieldpin.data.local.decodeBoundary
import com.fieldpin.data.local.toCsv
import com.fieldpin.data.local.toDomain
import com.fieldpin.data.local.toFieldSet
import com.fieldpin.data.remote.FieldPinApi
import com.fieldpin.data.remote.MockServerStore
import com.fieldpin.data.remote.OfflineException
import com.fieldpin.data.remote.SiteDto
import com.fieldpin.data.local.SiteEntity
import com.fieldpin.domain.model.GeoPoint
import com.fieldpin.domain.model.Site
import com.fieldpin.domain.model.SiteCategory
import com.fieldpin.domain.model.SiteField
import com.fieldpin.domain.model.SiteStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.UUID

class SiteRepository(
    private val db: FieldPinDatabase,
    private val dao: SiteDao,
    private val api: FieldPinApi,
    private val bundledData: MockServerStore,
) {
    fun observeSites(query: String, status: SiteStatus?): Flow<List<Site>> =
        dao.observe(query.trim(), status?.name).map { rows -> rows.map { it.toDomain() } }

    fun observeSite(id: String): Flow<Site?> = dao.observeById(id).map { it?.toDomain() }

    suspend fun getSite(id: String): Site? = dao.getById(id)?.toDomain()

    /** Creates a site offline. It is flagged `created` so the next sync pushes all its fields. */
    suspend fun createSite(
        name: String,
        category: SiteCategory,
        status: SiteStatus,
        description: String,
        boundary: List<GeoPoint>,
        now: Long = System.currentTimeMillis(),
    ): String {
        val id = UUID.randomUUID().toString()
        dao.upsert(
            buildSiteEntity(
                id = id,
                name = name.trim(),
                category = category.name,
                status = status.name,
                description = description.trim(),
                boundary = boundary,
                lastInspectionAt = null,
                version = 0,
                dirtyFields = setOf(SiteField.CREATED) + SiteField.ALL,
                editedAt = now,
            )
        )
        return id
    }

    suspend fun updateStatus(id: String, status: SiteStatus, now: Long = System.currentTimeMillis()) =
        edit(id, now) { it.copy(status = status.name) to SiteField.STATUS }

    suspend fun updateDetails(
        id: String,
        name: String,
        category: SiteCategory,
        description: String,
        now: Long = System.currentTimeMillis(),
    ) = db.withTransaction {
        val current = dao.getById(id) ?: return@withTransaction
        val changed = buildSet {
            if (current.name != name.trim()) add(SiteField.NAME)
            if (current.category != category.name) add(SiteField.CATEGORY)
            if (current.description != description.trim()) add(SiteField.DESCRIPTION)
        }
        if (changed.isEmpty()) return@withTransaction
        dao.upsert(
            current.copy(
                name = name.trim(),
                category = category.name,
                description = description.trim(),
                dirtyFields = (current.dirtyFields.toFieldSet() + changed).toCsv(),
                editedAt = now,
            )
        )
    }

    private suspend fun edit(id: String, now: Long, change: (SiteEntity) -> Pair<SiteEntity, String>) =
        db.withTransaction {
            val current = dao.getById(id) ?: return@withTransaction
            val (updated, field) = change(current)
            dao.upsert(
                updated.copy(
                    dirtyFields = (current.dirtyFields.toFieldSet() + field).toCsv(),
                    editedAt = now,
                )
            )
        }

    /**
     * First sign-in on this device: fetch sites from the server. If the device is offline the
     * bundled starter data is used so the app is usable straight away; the next sync reconciles it.
     */
    suspend fun bootstrap(token: String) {
        if (dao.count() > 0) return
        val remote = try {
            api.fetchSites(token)
        } catch (e: OfflineException) {
            bundledData.snapshot().sites
        }
        mergePulled(remote)
    }

    /** The server accepted [pushed]; fold its answer back in unless the user edited meanwhile. */
    suspend fun applyPushResult(pushed: SiteEntity, remote: SiteDto) = db.withTransaction {
        val current = dao.getById(pushed.id) ?: return@withTransaction
        val untouchedSincePush = current.editedAt == pushed.editedAt
        dao.upsert(rebase(current, remote, clearDirty = untouchedSincePush))
    }

    suspend fun mergePulled(remote: List<SiteDto>) = db.withTransaction {
        val changed = remote.mapNotNull { dto ->
            val local = dao.getById(dto.id)
            val merged = rebase(local, dto, clearDirty = false)
            merged.takeIf { it != local }
        }
        if (changed.isNotEmpty()) dao.upsertAll(changed)
    }

    fun boundaryOf(entity: SiteEntity): List<GeoPoint> = decodeBoundary(entity.boundaryJson)
}
