package com.fieldpin.data.remote

import com.fieldpin.domain.model.SiteField

/**
 * Server-side merge of one client change into the stored site. Pure function: no I/O, no clock.
 *
 * Policy (field-level, so two phones editing *different* fields never clobber each other):
 *  - A field the server did not change since the client's `baseVersion` is accepted.
 *  - A field changed on the server meanwhile is a conflict: the newer device edit wins
 *    (last-writer-wins on `editedAt`), except `lastInspectionAt`, where the later date always wins.
 *  - Every conflict is reported back so the app can tell the officer what happened.
 */
object ServerMerge {
    data class Result(val site: SiteDto, val conflicts: List<ConflictDto>)

    fun apply(existing: SiteDto?, change: SiteChangeDto): Result {
        if (existing == null) {
            require(change.isCreate) { "Unknown site ${change.siteId}" }
            return Result(create(change), emptyList())
        }
        // A create arriving for an id we already have means the first response was lost: keep it.
        if (change.isCreate) return Result(existing, emptyList())

        val applied = mutableListOf<String>()
        val conflicts = mutableListOf<ConflictDto>()
        val current: SiteDto = existing
        var site: SiteDto = current
        for (field in change.fields) {
            val yours = change.valueOf(field)
            val theirs = existing.valueOf(field)
            val changedOnServer = (existing.fieldVersions[field] ?: 0) > change.baseVersion
            if (!changedOnServer || yours == theirs) {
                site = withField(site, change, field)
                applied += field
                continue
            }
            val yoursWins = if (field == SiteField.LAST_INSPECTION) {
                (change.lastInspectionAt ?: 0L) >= (existing.lastInspectionAt ?: 0L)
            } else {
                change.editedAt >= (existing.fieldEditedAt[field] ?: 0L)
            }
            conflicts += ConflictDto(field, yours, theirs, yoursWins)
            if (yoursWins) {
                site = withField(site, change, field)
                applied += field
            }
        }
        if (applied.isEmpty()) return Result(existing, conflicts)

        val newVersion = existing.version + 1
        return Result(
            site.copy(
                version = newVersion,
                fieldVersions = existing.fieldVersions + applied.associateWith { newVersion },
                fieldEditedAt = existing.fieldEditedAt + applied.associateWith { change.editedAt },
            ),
            conflicts,
        )
    }

    private fun create(change: SiteChangeDto) = SiteDto(
        id = change.siteId,
        name = change.name.orEmpty(),
        category = change.category ?: "OTHER",
        status = change.status ?: "ACTIVE",
        description = change.description.orEmpty(),
        boundary = change.boundary.orEmpty(),
        lastInspectionAt = change.lastInspectionAt,
        version = 1,
        fieldVersions = SiteField.ALL.associateWith { 1 },
        fieldEditedAt = SiteField.ALL.associateWith { change.editedAt },
    )

    private fun withField(site: SiteDto, change: SiteChangeDto, field: String): SiteDto = when (field) {
        SiteField.NAME -> site.copy(name = change.name ?: site.name)
        SiteField.CATEGORY -> site.copy(category = change.category ?: site.category)
        SiteField.STATUS -> site.copy(status = change.status ?: site.status)
        SiteField.DESCRIPTION -> site.copy(description = change.description ?: site.description)
        SiteField.LAST_INSPECTION -> site.copy(lastInspectionAt = change.lastInspectionAt ?: site.lastInspectionAt)
        SiteField.BOUNDARY -> site.copy(boundary = change.boundary ?: site.boundary)
        else -> site
    }

    private fun SiteChangeDto.valueOf(field: String): String? = when (field) {
        SiteField.NAME -> name
        SiteField.CATEGORY -> category
        SiteField.STATUS -> status
        SiteField.DESCRIPTION -> description
        SiteField.LAST_INSPECTION -> lastInspectionAt?.toString()
        SiteField.BOUNDARY -> boundary?.size?.toString()
        else -> null
    }

    private fun SiteDto.valueOf(field: String): String? = when (field) {
        SiteField.NAME -> name
        SiteField.CATEGORY -> category
        SiteField.STATUS -> status
        SiteField.DESCRIPTION -> description
        SiteField.LAST_INSPECTION -> lastInspectionAt?.toString()
        SiteField.BOUNDARY -> boundary.size.toString()
        else -> null
    }
}
