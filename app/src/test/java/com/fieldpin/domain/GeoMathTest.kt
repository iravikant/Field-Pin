package com.fieldpin.domain

import com.fieldpin.domain.gis.GeoMath
import com.fieldpin.domain.model.GeoPoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GeoMathTest {
    // Metres per degree at 28.6139°N (WGS84 series), used only to *build* test squares.
    private val delhi = GeoPoint(28.6139, 77.2090)
    private val mPerDegLat = 110829.4028
    private val mPerDegLng = 97798.8555

    /** A square with [sideM] metre sides whose south-west corner is [origin]. */
    private fun square(origin: GeoPoint = delhi, sideM: Double = 100.0): List<GeoPoint> {
        val dLat = sideM / mPerDegLat
        val dLng = sideM / mPerDegLng
        return listOf(
            origin,
            GeoPoint(origin.lat, origin.lng + dLng),
            GeoPoint(origin.lat + dLat, origin.lng + dLng),
            GeoPoint(origin.lat + dLat, origin.lng),
        )
    }

    private fun assertRelative(expected: Double, actual: Double, tolerance: Double) {
        assertEquals("expected $expected but was $actual", 0.0, (actual - expected) / expected, tolerance)
    }

    // ------------------------------------------------------------------ area

    @Test
    fun area_ofSmallBoxAtEquator_matchesEllipsoidalValue() {
        // 0.001° x 0.001° at the equator: 111.3195 m x 110.5743 m on WGS84.
        val box = listOf(GeoPoint(0.0, 0.0), GeoPoint(0.0, 0.001), GeoPoint(0.001, 0.001), GeoPoint(0.001, 0.0))
        assertRelative(111.31949 * 110.57427, GeoMath.areaSqm(box), 0.002)
    }

    @Test
    fun area_of100mSquare_isAbout10000sqm() {
        assertRelative(10_000.0, GeoMath.areaSqm(square()), 0.002)
    }

    @Test
    fun area_doesNotDependOnWindingOrStartPoint() {
        val ring = square()
        val expected = GeoMath.areaSqm(ring)
        assertEquals(expected, GeoMath.areaSqm(ring.reversed()), 1e-6)
        assertEquals(expected, GeoMath.areaSqm(ring.drop(2) + ring.take(2)), 1e-6)
    }

    @Test
    fun area_isNotAFlatDegreeShoelace() {
        // A flat x/y shoelace on degrees would give the same number everywhere. On the ellipsoid a
        // 0.001° box at 60°N is ~0.5051 of the equatorial one (cos 60° = 0.5, corrected for the
        // ellipsoid's radii of curvature), which a sphere or a flat model would not reproduce.
        fun box(lat: Double) = listOf(GeoPoint(lat, 0.0), GeoPoint(lat, 0.001), GeoPoint(lat + 0.001, 0.001), GeoPoint(lat + 0.001, 0.0))
        val equator = GeoMath.areaSqm(box(0.0))
        val north60 = GeoMath.areaSqm(box(60.0))
        assertRelative(0.50505, north60 / equator, 0.002)
    }

    @Test
    fun area_ofFewerThanThreePoints_isZero() {
        assertEquals(0.0, GeoMath.areaSqm(emptyList()), 0.0)
        assertEquals(0.0, GeoMath.areaSqm(listOf(delhi, GeoPoint(28.62, 77.21))), 0.0)
    }

    @Test
    fun acres_conversion() {
        assertEquals(1.0, GeoMath.sqmToAcres(4046.8564224), 1e-9)
    }

    // ------------------------------------------------------ distance/perimeter

    @Test
    fun distance_oneDegreeOfLongitudeAtEquator() {
        assertEquals(111_319.49, GeoMath.distanceM(GeoPoint(0.0, 0.0), GeoPoint(0.0, 1.0)), 1.0)
    }

    @Test
    fun distance_oneDegreeOfLatitudeAtEquator() {
        assertEquals(110_574.39, GeoMath.distanceM(GeoPoint(0.0, 0.0), GeoPoint(1.0, 0.0)), 1.0)
    }

    @Test
    fun distance_isZeroForSamePoint_andSymmetric() {
        assertEquals(0.0, GeoMath.distanceM(delhi, delhi), 0.0)
        val other = GeoPoint(19.0760, 72.8777)
        assertEquals(GeoMath.distanceM(delhi, other), GeoMath.distanceM(other, delhi), 1e-6)
    }

    @Test
    fun perimeter_of100mSquare_isAbout400m() {
        assertRelative(400.0, GeoMath.perimeterM(square()), 0.005)
    }

    @Test
    fun perimeter_closesTheRing() {
        // Triangle: the closing edge must be counted.
        val a = GeoPoint(0.0, 0.0)
        val b = GeoPoint(0.0, 0.001)
        val c = GeoPoint(0.001, 0.0)
        val open = GeoMath.distanceM(a, b) + GeoMath.distanceM(b, c)
        assertTrue(GeoMath.perimeterM(listOf(a, b, c)) > open)
        assertEquals(open + GeoMath.distanceM(c, a), GeoMath.perimeterM(listOf(a, b, c)), 1e-9)
    }

    // ------------------------------------------------------- point in polygon

    @Test
    fun contains_insideAndOutsideOfSquare() {
        val sq = square()
        val inside = GeoPoint(delhi.lat + 50 / mPerDegLat, delhi.lng + 50 / mPerDegLng)
        val outside = GeoPoint(delhi.lat + 150 / mPerDegLat, delhi.lng + 50 / mPerDegLng)
        assertTrue(GeoMath.contains(sq, inside))
        assertFalse(GeoMath.contains(sq, outside))
        assertFalse(GeoMath.contains(sq, GeoPoint(delhi.lat - 0.01, delhi.lng - 0.01)))
    }

    @Test
    fun contains_respectsConcaveNotch() {
        // "C" shape opening to the east; the notch is outside even though it is inside the bounding box.
        val u = 0.001
        val c = listOf(
            GeoPoint(0 * u, 0 * u), GeoPoint(0 * u, 3 * u), GeoPoint(1 * u, 3 * u), GeoPoint(1 * u, 1 * u),
            GeoPoint(2 * u, 1 * u), GeoPoint(2 * u, 3 * u), GeoPoint(3 * u, 3 * u), GeoPoint(3 * u, 0 * u),
        )
        // rows are latitude, columns longitude: the notch is lat 1u..2u, lng 1u..3u
        assertFalse(GeoMath.contains(c, GeoPoint(1.5 * u, 2 * u)))
        assertTrue(GeoMath.contains(c, GeoPoint(0.5 * u, 2 * u)))
        assertTrue(GeoMath.contains(c, GeoPoint(1.5 * u, 0.5 * u)))
    }

    @Test
    fun contains_needsAtLeastThreePoints() {
        assertFalse(GeoMath.contains(listOf(delhi, GeoPoint(28.7, 77.3)), delhi))
    }

    @Test
    fun distanceToPolygon_isZeroInside_andMetresOutside() {
        val sq = square()
        val inside = GeoPoint(delhi.lat + 50 / mPerDegLat, delhi.lng + 50 / mPerDegLng)
        assertEquals(0.0, GeoMath.distanceToPolygonM(sq, inside), 0.0)
        val fiftyEastOfEdge = GeoPoint(delhi.lat + 50 / mPerDegLat, delhi.lng + 150 / mPerDegLng)
        assertEquals(50.0, GeoMath.distanceToPolygonM(sq, fiftyEastOfEdge), 1.0)
    }

    @Test
    fun distanceToPolygon_measuresToCornerWhenBeyondAnEdge() {
        val sq = square()
        // 30 m east and 40 m north of the north-east corner => 50 m (3-4-5)
        val corner = sq[2]
        val p = GeoPoint(corner.lat + 40 / mPerDegLat, corner.lng + 30 / mPerDegLng)
        assertEquals(50.0, GeoMath.distanceToPolygonM(sq, p), 1.0)
    }

    // -------------------------------------------------------------- helpers

    @Test
    fun centroid_ofSquare_isItsCentre() {
        val sq = square()
        val c = GeoMath.centroid(sq)
        assertEquals(delhi.lat + 50 / mPerDegLat, c.lat, 1e-7)
        assertEquals(delhi.lng + 50 / mPerDegLng, c.lng, 1e-7)
    }

    @Test
    fun interiorPoint_isInsideEvenWhenCentroidIsNot() {
        val u = 0.001
        val c = listOf(
            GeoPoint(0 * u, 0 * u), GeoPoint(0 * u, 3 * u), GeoPoint(1 * u, 3 * u), GeoPoint(1 * u, 1 * u),
            GeoPoint(2 * u, 1 * u), GeoPoint(2 * u, 3 * u), GeoPoint(3 * u, 3 * u), GeoPoint(3 * u, 0 * u),
        )
        assertTrue(GeoMath.contains(c, GeoMath.interiorPoint(c)))
    }

    @Test
    fun selfIntersection_detectsBowTie_andAcceptsSimplePolygons() {
        val sq = square()
        assertFalse(GeoMath.hasSelfIntersection(sq))
        val bowTie = listOf(sq[0], sq[2], sq[1], sq[3]) // swaps two corners: edges cross
        assertTrue(GeoMath.hasSelfIntersection(bowTie))
        assertFalse(GeoMath.hasSelfIntersection(sq.take(3))) // triangles cannot self-intersect
    }
}
