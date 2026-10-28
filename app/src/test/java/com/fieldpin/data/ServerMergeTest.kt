package com.fieldpin.data

import com.fieldpin.data.remote.ServerMerge
import com.fieldpin.data.remote.SiteChangeDto
import com.fieldpin.data.remote.SiteDto
import com.fieldpin.domain.model.GeoPoint
import com.fieldpin.domain.model.SiteField
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/** The "same site edited on two phones while offline" scenarios from the README. */
class ServerMergeTest {
    private val square = listOf(GeoPoint(0.0, 0.0), GeoPoint(0.0, 0.001), GeoPoint(0.001, 0.001), GeoPoint(0.001, 0.0))

    private fun created(): SiteDto = ServerMerge.apply(
        null,
        SiteChangeDto(
            siteId = "s1", baseVersion = 0, isCreate = true, editedAt = 1_000, fields = SiteField.ALL.toSet(),
            name = "Lake View Park", category = "PARK", status = "ACTIVE", description = "", boundary = square,
        ),
    ).site

    private fun statusChange(base: Int, at: Long, status: String) = SiteChangeDto(
        siteId = "s1", baseVersion = base, isCreate = false, editedAt = at,
        fields = setOf(SiteField.STATUS), status = status,
    )

    @Test
    fun create_startsAtVersion1() {
        val site = created()
        assertEquals(1, site.version)
        assertEquals("Lake View Park", site.name)
    }

    @Test
    fun create_isIdempotentWhenTheFirstAckWasLost() {
        val site = created()
        val again = ServerMerge.apply(site, SiteChangeDto("s1", 0, true, 5_000, SiteField.ALL.toSet(), name = "Other"))
        assertSame(site, again.site)
    }

    @Test
    fun editFromCurrentVersion_isAcceptedWithoutConflict() {
        val result = ServerMerge.apply(created(), statusChange(base = 1, at = 2_000, status = "CLOSED"))
        assertEquals("CLOSED", result.site.status)
        assertEquals(2, result.site.version)
        assertTrue(result.conflicts.isEmpty())
    }

    @Test
    fun sameFieldOnTwoPhones_newerEditWins() {
        val afterPhoneA = ServerMerge.apply(created(), statusChange(1, 2_000, "CLOSED")).site
        val phoneB = ServerMerge.apply(afterPhoneA, statusChange(base = 1, at = 3_000, status = "NEEDS_REPAIR"))
        assertEquals("NEEDS_REPAIR", phoneB.site.status)
        assertEquals(3, phoneB.site.version)
        assertEquals(1, phoneB.conflicts.size)
        assertTrue(phoneB.conflicts.single().yoursWon)
    }

    @Test
    fun sameFieldOnTwoPhones_olderEditLosesAndServerIsUntouched() {
        val afterPhoneA = ServerMerge.apply(created(), statusChange(1, 2_000, "CLOSED")).site
        val phoneB = ServerMerge.apply(afterPhoneA, statusChange(base = 1, at = 1_500, status = "NEEDS_REPAIR"))
        assertSame(afterPhoneA, phoneB.site)
        assertEquals("CLOSED", phoneB.site.status)
        assertEquals(false, phoneB.conflicts.single().yoursWon)
    }

    @Test
    fun differentFieldsOnTwoPhones_areBothKept() {
        val afterPhoneA = ServerMerge.apply(created(), statusChange(1, 2_000, "CLOSED")).site
        val rename = SiteChangeDto("s1", 1, false, 2_500, setOf(SiteField.NAME), name = "Lake View Park (East)")
        val phoneB = ServerMerge.apply(afterPhoneA, rename)
        assertEquals("CLOSED", phoneB.site.status)
        assertEquals("Lake View Park (East)", phoneB.site.name)
        assertTrue(phoneB.conflicts.isEmpty())
        assertEquals(3, phoneB.site.version)
    }

    @Test
    fun lastInspectionDate_alwaysKeepsTheLaterDate() {
        val a = SiteChangeDto("s1", 1, false, 2_000, setOf(SiteField.LAST_INSPECTION), lastInspectionAt = 9_000)
        val afterA = ServerMerge.apply(created(), a).site
        // phone B inspected earlier but synced later, with a *newer* edit time
        val b = SiteChangeDto("s1", 1, false, 5_000, setOf(SiteField.LAST_INSPECTION), lastInspectionAt = 4_000)
        val result = ServerMerge.apply(afterA, b)
        assertEquals(9_000L, result.site.lastInspectionAt)
    }
}
