package com.fieldpin.data.repo

import com.fieldpin.data.local.SiteEntity
import com.fieldpin.data.local.buildSiteEntity
import com.fieldpin.data.local.decodeBoundary
import com.fieldpin.data.local.toFieldSet
import com.fieldpin.data.remote.SiteChangeDto
import com.fieldpin.data.remote.SiteDto
import com.fieldpin.domain.model.SiteField

/**
 * Rebases a local row onto the server's version of the same site.
 *
 * - [clearDirty] true (the server just accepted our push, nothing was edited meanwhile): the
 *   server copy replaces ours entirely and the row becomes clean.
 * - Otherwise fields the officer still has pending are kept, everything else takes the server value,
 *   and `version` moves to the server's so the next push is not seen as a stale edit.
 */
fun rebase(local: SiteEntity?, remote: SiteDto, clearDirty: Boolean): SiteEntity {
    val dirty = if (local == null || clearDirty) emptySet() else local.dirtyFields.toFieldSet()
    val keepLocal = local != null && dirty.isNotEmpty()
    fun <T> pick(field: String, serverValue: T, localValue: T?): T =
        if (keepLocal && field in dirty && localValue != null) localValue else serverValue

    return buildSiteEntity(
        id = remote.id,
        name = pick(SiteField.NAME, remote.name, local?.name),
        category = pick(SiteField.CATEGORY, remote.category, local?.category),
        status = pick(SiteField.STATUS, remote.status, local?.status),
        description = pick(SiteField.DESCRIPTION, remote.description, local?.description),
        boundary = if (keepLocal && SiteField.BOUNDARY in dirty) decodeBoundary(local!!.boundaryJson) else remote.boundary,
        lastInspectionAt = pick(SiteField.LAST_INSPECTION, remote.lastInspectionAt, local?.lastInspectionAt),
        version = remote.version,
        dirtyFields = dirty,
        editedAt = local?.editedAt ?: remote.fieldEditedAt.values.maxOrNull() ?: 0L,
    )
}

/** Builds the update the server should receive for a dirty row. */
fun SiteEntity.toChange(): SiteChangeDto {
    val dirty = dirtyFields.toFieldSet()
    val isCreate = SiteField.CREATED in dirty
    val fields = if (isCreate) SiteField.ALL.toSet() else dirty - SiteField.CREATED
    return SiteChangeDto(
        siteId = id,
        baseVersion = version,
        isCreate = isCreate,
        editedAt = editedAt,
        fields = fields,
        name = name,
        category = category,
        status = status,
        description = description,
        lastInspectionAt = lastInspectionAt,
        boundary = if (SiteField.BOUNDARY in fields) decodeBoundary(boundaryJson) else null,
    )
}
