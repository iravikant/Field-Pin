package com.fieldpin.data.local

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface SiteDao {
    @Query(
        """
        SELECT s.*, (SELECT COUNT(*) FROM inspections i
                     WHERE i.siteId = s.id AND i.submittedAt IS NOT NULL AND i.synced = 0) AS pendingInspections
        FROM sites s
        WHERE (:query = '' OR s.name LIKE '%' || :query || '%')
          AND (:status IS NULL OR s.status = :status)
        ORDER BY s.name COLLATE NOCASE
        """
    )
    fun observe(query: String, status: String?): Flow<List<SiteRow>>

    @Query(
        """
        SELECT s.*, (SELECT COUNT(*) FROM inspections i
                     WHERE i.siteId = s.id AND i.submittedAt IS NOT NULL AND i.synced = 0) AS pendingInspections
        FROM sites s WHERE s.id = :id
        """
    )
    fun observeById(id: String): Flow<SiteRow?>

    @Query("SELECT * FROM sites WHERE id = :id")
    suspend fun getById(id: String): SiteEntity?

    @Query("SELECT * FROM sites WHERE dirtyFields != ''")
    suspend fun getDirty(): List<SiteEntity>

    @Query("SELECT COUNT(*) FROM sites")
    suspend fun count(): Int

    @Query(
        """
        SELECT (SELECT COUNT(*) FROM sites WHERE dirtyFields != '') +
               (SELECT COUNT(*) FROM inspections WHERE submittedAt IS NOT NULL AND synced = 0)
        """
    )
    fun observePendingCount(): Flow<Int>

    @Upsert
    suspend fun upsert(site: SiteEntity)

    @Upsert
    suspend fun upsertAll(sites: List<SiteEntity>)
}

@Dao
interface InspectionDao {
    @Query("SELECT * FROM inspections WHERE id = :id")
    fun observeById(id: String): Flow<InspectionEntity?>

    @Query("SELECT * FROM inspections WHERE id = :id")
    suspend fun getById(id: String): InspectionEntity?

    @Query("SELECT * FROM inspections WHERE siteId = :siteId AND submittedAt IS NULL ORDER BY checkInAt DESC LIMIT 1")
    suspend fun getOpenDraft(siteId: String): InspectionEntity?

    @Query("SELECT * FROM inspections WHERE siteId = :siteId AND submittedAt IS NOT NULL ORDER BY submittedAt DESC")
    fun observeSubmittedForSite(siteId: String): Flow<List<InspectionEntity>>

    @Query("SELECT * FROM inspections WHERE submittedAt IS NOT NULL AND synced = 0 ORDER BY submittedAt")
    suspend fun getUnsynced(): List<InspectionEntity>

    @Query("UPDATE inspections SET synced = 1 WHERE id = :id")
    suspend fun markSynced(id: String)

    @Query("DELETE FROM inspections WHERE id = :id")
    suspend fun delete(id: String)

    @Upsert
    suspend fun upsert(inspection: InspectionEntity)
}

@Dao
interface SiteDocumentDao {
    @Query("SELECT * FROM site_documents WHERE siteId = :siteId ORDER BY addedAt DESC")
    fun observeForSite(siteId: String): Flow<List<SiteDocumentEntity>>

    @Query("SELECT * FROM site_documents WHERE id = :id")
    suspend fun getById(id: String): SiteDocumentEntity?

    @Upsert
    suspend fun upsert(document: SiteDocumentEntity)

    @Query("DELETE FROM site_documents WHERE id = :id")
    suspend fun delete(id: String)
}
