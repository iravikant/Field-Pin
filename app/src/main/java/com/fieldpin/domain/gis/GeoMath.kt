package com.fieldpin.domain.gis

import com.fieldpin.domain.model.GeoPoint
import kotlin.math.abs
import kotlin.math.asin
import kotlin.math.atan
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan

/**
 * Geodesy on the WGS84 ellipsoid. Nothing here touches Android, so it is unit-testable on the JVM.
 *
 * - Area: vertices are mapped to *authalic* latitudes (an equal-area sphere for WGS84) and the
 *   spherical polygon area is computed with great-circle edges. No flat x/y shoelace on degrees.
 * - Distance/perimeter: Vincenty's inverse formula on the ellipsoid (haversine as a fallback for
 *   the rare near-antipodal non-convergence).
 * - Inside test: ray casting on lat/lng, which is exact enough at site scale (no dateline sites).
 */
object GeoMath {
    private const val A = 6378137.0
    private const val F = 1 / 298.257223563
    private const val B = A * (1 - F)
    private const val E2 = F * (2 - F)
    private const val MEAN_RADIUS = 6371008.8
    private const val SQM_PER_ACRE = 4046.8564224

    fun sqmToAcres(sqm: Double): Double = sqm / SQM_PER_ACRE

    private fun rad(deg: Double) = Math.toRadians(deg)

    // ---------------------------------------------------------------- area

    private val e = sqrt(E2)

    /** q(phi) from Snyder, Map Projections - A Working Manual, eq. 3-12. */
    private fun q(phi: Double): Double {
        val s = sin(phi)
        return (1 - E2) * (s / (1 - E2 * s * s) - (1 / (2 * e)) * ln((1 - e * s) / (1 + e * s)))
    }

    private val qPole = q(Math.PI / 2)
    private val authalicRadius = A * sqrt(qPole / 2)

    private fun authalicLat(latDeg: Double): Double =
        asin((q(rad(latDeg)) / qPole).coerceIn(-1.0, 1.0))

    /** Area of the polygon on the earth's surface in m². Needs >= 3 points; otherwise 0. */
    fun areaSqm(polygon: List<GeoPoint>): Double {
        if (polygon.size < 3) return 0.0
        var total = 0.0
        var prev = polygon.last()
        var prevTan = tan((Math.PI / 2 - authalicLat(prev.lat)) / 2)
        var prevLng = rad(prev.lng)
        for (p in polygon) {
            val t = tan((Math.PI / 2 - authalicLat(p.lat)) / 2)
            val l = rad(p.lng)
            total += polarTriangleArea(prevTan, prevLng, t, l)
            prevTan = t
            prevLng = l
        }
        return abs(total) * authalicRadius * authalicRadius
    }

    private fun polarTriangleArea(tan1: Double, lng1: Double, tan2: Double, lng2: Double): Double {
        val deltaLng = lng1 - lng2
        val t = tan1 * tan2
        return 2 * atan2(t * sin(deltaLng), 1 + t * cos(deltaLng))
    }

    // ------------------------------------------------------------ distance

    /** Geodesic distance in metres (Vincenty inverse, WGS84). */
    fun distanceM(a: GeoPoint, b: GeoPoint): Double {
        if (a.lat == b.lat && a.lng == b.lng) return 0.0
        val l = rad(b.lng - a.lng)
        val u1 = atan((1 - F) * tan(rad(a.lat)))
        val u2 = atan((1 - F) * tan(rad(b.lat)))
        val sinU1 = sin(u1)
        val cosU1 = cos(u1)
        val sinU2 = sin(u2)
        val cosU2 = cos(u2)

        var lambda = l
        var prevLambda: Double
        var iterations = 0
        var sinSigma: Double
        var cosSigma: Double
        var sigma: Double
        var cosSqAlpha: Double
        var cos2SigmaM: Double
        do {
            val sinL = sin(lambda)
            val cosL = cos(lambda)
            sinSigma = sqrt((cosU2 * sinL).pow(2) + (cosU1 * sinU2 - sinU1 * cosU2 * cosL).pow(2))
            if (sinSigma == 0.0) return 0.0
            cosSigma = sinU1 * sinU2 + cosU1 * cosU2 * cosL
            sigma = atan2(sinSigma, cosSigma)
            val sinAlpha = cosU1 * cosU2 * sinL / sinSigma
            cosSqAlpha = 1 - sinAlpha * sinAlpha
            cos2SigmaM = if (cosSqAlpha != 0.0) cosSigma - 2 * sinU1 * sinU2 / cosSqAlpha else 0.0
            val c = F / 16 * cosSqAlpha * (4 + F * (4 - 3 * cosSqAlpha))
            prevLambda = lambda
            lambda = l + (1 - c) * F * sinAlpha *
                (sigma + c * sinSigma * (cos2SigmaM + c * cosSigma * (-1 + 2 * cos2SigmaM * cos2SigmaM)))
            iterations++
        } while (abs(lambda - prevLambda) > 1e-12 && iterations < 100)

        if (abs(lambda - prevLambda) > 1e-9) return haversineM(a, b)

        val uSq = cosSqAlpha * (A * A - B * B) / (B * B)
        val bigA = 1 + uSq / 16384 * (4096 + uSq * (-768 + uSq * (320 - 175 * uSq)))
        val bigB = uSq / 1024 * (256 + uSq * (-128 + uSq * (74 - 47 * uSq)))
        val deltaSigma = bigB * sinSigma * (
            cos2SigmaM + bigB / 4 * (
                cosSigma * (-1 + 2 * cos2SigmaM * cos2SigmaM) -
                    bigB / 6 * cos2SigmaM * (-3 + 4 * sinSigma * sinSigma) * (-3 + 4 * cos2SigmaM * cos2SigmaM)
                )
            )
        return B * bigA * (sigma - deltaSigma)
    }

    private fun haversineM(a: GeoPoint, b: GeoPoint): Double {
        val dLat = rad(b.lat - a.lat)
        val dLng = rad(b.lng - a.lng)
        val h = sin(dLat / 2).pow(2) + cos(rad(a.lat)) * cos(rad(b.lat)) * sin(dLng / 2).pow(2)
        return 2 * MEAN_RADIUS * asin(min(1.0, sqrt(h)))
    }

    /** Length of the closed ring in metres. Needs >= 2 points; otherwise 0. */
    fun perimeterM(polygon: List<GeoPoint>): Double {
        if (polygon.size < 2) return 0.0
        var sum = 0.0
        for (i in polygon.indices) sum += distanceM(polygon[i], polygon[(i + 1) % polygon.size])
        return sum
    }

    /** Length of an open path in metres (used while the boundary is still being drawn). */
    fun pathLengthM(points: List<GeoPoint>): Double {
        var sum = 0.0
        for (i in 0 until points.size - 1) sum += distanceM(points[i], points[i + 1])
        return sum
    }

    // ----------------------------------------------------------- inside test

    /** Point-in-polygon by ray casting. Polygon is implicitly closed. */
    fun contains(polygon: List<GeoPoint>, p: GeoPoint): Boolean {
        if (polygon.size < 3) return false
        var inside = false
        var j = polygon.lastIndex
        for (i in polygon.indices) {
            val pi = polygon[i]
            val pj = polygon[j]
            val crosses = (pi.lat > p.lat) != (pj.lat > p.lat) &&
                p.lng < (pj.lng - pi.lng) * (p.lat - pi.lat) / (pj.lat - pi.lat) + pi.lng
            if (crosses) inside = !inside
            j = i
        }
        return inside
    }

    /** 0 when inside; otherwise the shortest distance in metres from [p] to the boundary. */
    fun distanceToPolygonM(polygon: List<GeoPoint>, p: GeoPoint): Double {
        if (polygon.isEmpty()) return Double.MAX_VALUE
        if (contains(polygon, p)) return 0.0
        // Local equirectangular frame centred on p: accurate to well under 1% for < 50 km.
        val phi = rad(p.lat)
        val mPerDegLat = 111132.92 - 559.82 * cos(2 * phi) + 1.175 * cos(4 * phi)
        val mPerDegLng = 111412.84 * cos(phi) - 93.5 * cos(3 * phi)
        fun x(q: GeoPoint) = (q.lng - p.lng) * mPerDegLng
        fun y(q: GeoPoint) = (q.lat - p.lat) * mPerDegLat
        var best = Double.MAX_VALUE
        for (i in polygon.indices) {
            val a = polygon[i]
            val b = polygon[(i + 1) % polygon.size]
            best = min(best, pointToSegment(x(a), y(a), x(b), y(b)))
        }
        return best
    }

    /** Distance from the origin to segment (ax,ay)-(bx,by). */
    private fun pointToSegment(ax: Double, ay: Double, bx: Double, by: Double): Double {
        val dx = bx - ax
        val dy = by - ay
        val len2 = dx * dx + dy * dy
        val t = if (len2 == 0.0) 0.0 else (-(ax * dx + ay * dy) / len2).coerceIn(0.0, 1.0)
        val cx = ax + t * dx
        val cy = ay + t * dy
        return sqrt(cx * cx + cy * cy)
    }

    // ------------------------------------------------------------- helpers

    /** Area-weighted centroid (planar on lat/lng, fine at site scale); mean of vertices if degenerate. */
    fun centroid(polygon: List<GeoPoint>): GeoPoint {
        if (polygon.isEmpty()) return GeoPoint(0.0, 0.0)
        var a2 = 0.0
        var cx = 0.0
        var cy = 0.0
        for (i in polygon.indices) {
            val p = polygon[i]
            val q = polygon[(i + 1) % polygon.size]
            val cross = p.lng * q.lat - q.lng * p.lat
            a2 += cross
            cx += (p.lng + q.lng) * cross
            cy += (p.lat + q.lat) * cross
        }
        if (abs(a2) < 1e-18) {
            return GeoPoint(polygon.map { it.lat }.average(), polygon.map { it.lng }.average())
        }
        return GeoPoint(cy / (3 * a2), cx / (3 * a2))
    }

    /** A point guaranteed to lie inside the polygon (centroid when it does, else a vertex-pair midpoint). */
    fun interiorPoint(polygon: List<GeoPoint>): GeoPoint {
        val c = centroid(polygon)
        if (contains(polygon, c)) return c
        for (i in polygon.indices) for (j in i + 2 until polygon.size) {
            val m = GeoPoint((polygon[i].lat + polygon[j].lat) / 2, (polygon[i].lng + polygon[j].lng) / 2)
            if (contains(polygon, m)) return m
        }
        return c
    }

    /** True when any two non-adjacent edges of the closed ring cross. */
    fun hasSelfIntersection(polygon: List<GeoPoint>): Boolean {
        val n = polygon.size
        if (n < 4) return false
        for (i in 0 until n) {
            val a1 = polygon[i]
            val a2 = polygon[(i + 1) % n]
            for (j in i + 1 until n) {
                val adjacent = j == i + 1 || (i == 0 && j == n - 1)
                if (adjacent) continue
                if (segmentsIntersect(a1, a2, polygon[j], polygon[(j + 1) % n])) return true
            }
        }
        return false
    }

    private fun orient(a: GeoPoint, b: GeoPoint, c: GeoPoint): Double =
        (b.lng - a.lng) * (c.lat - a.lat) - (b.lat - a.lat) * (c.lng - a.lng)

    private fun onSegment(a: GeoPoint, b: GeoPoint, c: GeoPoint): Boolean =
        c.lng <= max(a.lng, b.lng) && c.lng >= min(a.lng, b.lng) &&
            c.lat <= max(a.lat, b.lat) && c.lat >= min(a.lat, b.lat)

    private fun segmentsIntersect(p1: GeoPoint, p2: GeoPoint, p3: GeoPoint, p4: GeoPoint): Boolean {
        val o1 = orient(p1, p2, p3)
        val o2 = orient(p1, p2, p4)
        val o3 = orient(p3, p4, p1)
        val o4 = orient(p3, p4, p2)
        if (((o1 > 0 && o2 < 0) || (o1 < 0 && o2 > 0)) && ((o3 > 0 && o4 < 0) || (o3 < 0 && o4 > 0))) return true
        if (o1 == 0.0 && onSegment(p1, p2, p3)) return true
        if (o2 == 0.0 && onSegment(p1, p2, p4)) return true
        if (o3 == 0.0 && onSegment(p3, p4, p1)) return true
        if (o4 == 0.0 && onSegment(p3, p4, p2)) return true
        return false
    }
}
