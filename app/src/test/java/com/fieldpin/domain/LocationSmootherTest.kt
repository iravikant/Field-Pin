package com.fieldpin.domain

import com.fieldpin.domain.gis.GeoMath
import com.fieldpin.domain.gis.LocationSmoother
import com.fieldpin.domain.model.GeoPoint
import com.fieldpin.domain.model.LocationFix
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Random

class LocationSmootherTest {
    private val home = GeoPoint(28.6139, 77.2090)

    /** Metres east/north of [home] to a GeoPoint (good enough at this scale). */
    private fun at(eastM: Double, northM: Double) =
        GeoPoint(home.lat + northM / 110_574.0, home.lng + eastM / (111_320.0 * Math.cos(Math.toRadians(home.lat))))

    private fun fix(p: GeoPoint, accuracy: Float, tSec: Long, speed: Float? = null) =
        LocationFix(p, accuracy, tSec * 1000, speed)

    @Test fun standingStillTheDotStopsWandering() {
        val rnd = Random(7)
        val s = LocationSmoother()
        val out = ArrayList<GeoPoint>()
        val raw = ArrayList<GeoPoint>()
        for (t in 0 until 60L) {
            val p = at(rnd.nextGaussian() * 6, rnd.nextGaussian() * 6) // ~6 m of noise around the real spot
            raw += p
            s.process(fix(p, 8f, t, speed = 0f))?.let { out += it.point }
        }
        fun spread(list: List<GeoPoint>) = list.takeLast(30).map { GeoMath.distanceM(home, it) }.average()
        assertTrue("smoothed ${spread(out)} should beat raw ${spread(raw)}", spread(out) < spread(raw) * 0.6)
    }

    @Test fun walkingIsFollowedWithoutRunawayLag() {
        val s = LocationSmoother()
        var last: LocationFix? = null
        for (t in 0 until 40L) {
            last = s.process(fix(at(t * 1.4, 0.0), 5f, t, speed = 1.4f)) ?: last
        }
        val truth = at(39 * 1.4, 0.0)
        assertTrue(GeoMath.distanceM(truth, last!!.point) < 8.0)
    }

    @Test fun outOfOrderAndDuplicateReadingsAreIgnored() {
        val s = LocationSmoother()
        assertNotNull(s.process(fix(home, 5f, 10)))
        assertNull(s.process(fix(at(20.0, 0.0), 5f, 10)))
        assertNull(s.process(fix(at(20.0, 0.0), 5f, 9)))
    }

    @Test fun aVeryPoorReadingDoesNotMoveAGoodEstimate() {
        val s = LocationSmoother()
        s.process(fix(home, 5f, 0))
        assertNull(s.process(fix(at(300.0, 0.0), 200f, 1)))
    }

    @Test fun anImpossibleJumpIsRejectedThenAcceptedIfItKeepsHappening() {
        val s = LocationSmoother()
        s.process(fix(home, 5f, 0))
        val far = at(5_000.0, 0.0)
        assertNull(s.process(fix(far, 5f, 1)))
        assertNull(s.process(fix(far, 5f, 2)))
        val moved = s.process(fix(far, 5f, 3))
        assertNotNull(moved)
        assertTrue(GeoMath.distanceM(far, moved!!.point) < 1.0)
    }

    @Test fun reportedAccuracyIsNeverMoreThanHalfOptimistic() {
        val s = LocationSmoother()
        var last: LocationFix? = null
        for (t in 0 until 120L) last = s.process(fix(home, 12f, t, speed = 0f)) ?: last
        assertTrue(last!!.accuracyM >= 6f)
        assertEquals(0.0, GeoMath.distanceM(home, last.point), 0.5)
    }
}
