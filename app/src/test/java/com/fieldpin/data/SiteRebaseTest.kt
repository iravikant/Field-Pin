package com.fieldpin.data

import com.fieldpin.data.local.buildSiteEntity
import com.fieldpin.data.remote.SiteDto
import com.fieldpin.data.repo.rebase
import com.fieldpin.data.repo.toChange
import com.fieldpin.domain.model.GeoPoint
import com.fieldpin.domain.model.SiteField
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SiteRebaseTest {
    private val square = listOf(GeoPoint(0.0, 0.0), GeoPoint(0.0, 0.001), GeoPoint(0.001, 0.001), GeoPoint(0.001, 0.0))

    private fun server(name: String = "Server name", status: String = "ACTIVE", version: Int = 5) = SiteDto(
        id = "s1", name = name, category = "PARK", status = status, boundary = square, version = version,
    )

    private fun local(dirty: Set<String>, status: String = "CLOSED") = buildSiteEntity(
        id = "s1", name = "Local name", category = "PARK", status = status, description = "", boundary = square,
        lastInspectionAt = null, version = 2, dirtyFields = dirty, editedAt = 777,
    )

    @Test
    fun unknownLocally_takesServerCopy() {
        val row = rebase(null, server(), clearDirty = false)
        assertEquals("Server name", row.name)
        assertEquals(5, row.version)
        assertEquals("", row.dirtyFields)
    }

    @Test
    fun pendingFieldsStay_otherFieldsFollowTheServer() {
        val row = rebase(local(setOf(SiteField.STATUS)), server(status = "ACTIVE"), clearDirty = false)
        assertEquals("CLOSED", row.status) // still our pending edit
        assertEquals("Server name", row.name) // not edited locally, so the server wins
        assertEquals(5, row.version) // rebased, so the next push is not seen as stale
        assertEquals(SiteField.STATUS, row.dirtyFields)
    }

    @Test
    fun acceptedPush_replacesRowAndClearsDirty() {
        val row = rebase(local(setOf(SiteField.STATUS)), server(status = "CLOSED"), clearDirty = true)
        assertEquals("", row.dirtyFields)
        assertEquals("Server name", row.name)
    }

    @Test
    fun createdSite_pushesAllFields_updatePushesOnlyDirtyOnes() {
        val created = local(setOf(SiteField.CREATED) + SiteField.ALL).toChange()
        assertTrue(created.isCreate)
        assertEquals(SiteField.ALL.toSet(), created.fields)

        val update = local(setOf(SiteField.STATUS)).toChange()
        assertFalse(update.isCreate)
        assertEquals(setOf(SiteField.STATUS), update.fields)
        assertEquals(2, update.baseVersion)
    }
}
