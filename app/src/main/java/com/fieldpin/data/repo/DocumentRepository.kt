package com.fieldpin.data.repo

import android.net.Uri
import com.fieldpin.data.local.SiteDocumentDao
import com.fieldpin.data.local.SiteDocumentEntity
import com.fieldpin.domain.model.SiteDocument
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.util.UUID

data class AttachReport(val added: Int, val errors: List<String>)

class DocumentRepository(
    private val dao: SiteDocumentDao,
    private val storage: DocumentStorage,
) {
    fun observe(siteId: String): Flow<List<SiteDocument>> =
        dao.observeForSite(siteId).map { list -> list.map { it.toDomain() } }

    suspend fun attach(siteId: String, uris: List<Uri>, now: Long = System.currentTimeMillis()): AttachReport =
        withContext(Dispatchers.IO) {
            var added = 0
            val errors = mutableListOf<String>()
            uris.forEach { uri ->
                try {
                    val file = storage.import(siteId, uri)
                    dao.upsert(
                        SiteDocumentEntity(
                            id = UUID.randomUUID().toString(),
                            siteId = siteId,
                            name = file.name,
                            mimeType = file.mimeType,
                            sizeBytes = file.sizeBytes,
                            path = file.file.absolutePath,
                            addedAt = now,
                        )
                    )
                    added++
                } catch (e: DocumentImportException) {
                    errors += e.message.orEmpty()
                }
            }
            AttachReport(added, errors)
        }

    suspend fun remove(id: String) = withContext(Dispatchers.IO) {
        val doc = dao.getById(id) ?: return@withContext
        dao.delete(id)
        storage.delete(doc.path)
    }
}

private fun SiteDocumentEntity.toDomain() = SiteDocument(id, siteId, name, mimeType, sizeBytes, path, addedAt)
