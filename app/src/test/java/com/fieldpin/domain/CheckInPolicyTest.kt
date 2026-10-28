package com.fieldpin.domain

import com.fieldpin.domain.gis.CheckInPolicy
import com.fieldpin.domain.gis.CheckInStatus
import com.fieldpin.domain.model.GeoPoint
import com.fieldpin.domain.model.LocationFix
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CheckInPolicyTest {
    private val site = listOf(
        GeoPoint(28.6000, 77.2000), GeoPoint(28.6000, 77.2010),
        GeoPoint(28.6010, 77.2010), GeoPoint(28.6010, 77.2000),
    )

    private fun fix(lat: Double, lng: Double, accuracy: Float) = LocationFix(GeoPoint(lat, lng), accuracy, 0L)

    @Test
    fun noFix_cannotCheckIn() {
        val status = CheckInPolicy.evaluate(site, null)
        assertEquals(CheckInStatus.NoLocation, status)
        assertFalse(status.canCheckIn)
    }

    @Test
    fun insideWithGoodAccuracy_canCheckIn() {
        val status = CheckInPolicy.evaluate(site, fix(28.6005, 77.2005, 8f))
        assertTrue(status is CheckInStatus.Inside)
        assertTrue(status.canCheckIn)
    }

    @Test
    fun insideWithPoorAccuracy_isRejectedAsWeakFix() {
        val status = CheckInPolicy.evaluate(site, fix(28.6005, 77.2005, CheckInPolicy.MAX_ACCURACY_M + 1))
        assertTrue(status is CheckInStatus.WeakFix)
        assertFalse(status.canCheckIn)
    }

    @Test
    fun outside_reportsDistanceToBoundary() {
        // 0.001° of latitude north of the north edge is about 110 m
        val status = CheckInPolicy.evaluate(site, fix(28.6020, 77.2005, 5f))
        assertTrue(status is CheckInStatus.Outside)
        val d = (status as CheckInStatus.Outside).distanceM
        assertTrue("distance was $d", d in 105.0..115.0)
        assertFalse(status.canCheckIn)
    }
}
