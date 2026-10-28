package com.fieldpin.data.map

import com.fieldpin.domain.model.GeoPoint
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.tan

/** A slippy-map tile (Web Mercator, 256 px, origin top-left), the format every XYZ tile server uses. */
data class TileKey(val z: Int, val x: Int, val y: Int)

object TileMath {
    fun tileX(lng: Double, z: Int): Int {
        val n = 1 shl z
        return floor((lng + 180.0) / 360.0 * n).toInt().coerceIn(0, n - 1)
    }

    fun tileY(lat: Double, z: Int): Int {
        val n = 1 shl z
        val rad = Math.toRadians(lat.coerceIn(-85.0511, 85.0511))
        return floor((1 - ln(tan(rad) + 1 / cos(rad)) / PI) / 2 * n).toInt().coerceIn(0, n - 1)
    }

    /** All tiles at [z] that intersect the box. */
    fun tilesInBox(minLat: Double, minLng: Double, maxLat: Double, maxLng: Double, z: Int): List<TileKey> {
        val x0 = tileX(minLng, z)
        val x1 = tileX(maxLng, z)
        val y0 = tileY(maxLat, z) // north has the smaller y
        val y1 = tileY(minLat, z)
        return buildList {
            for (x in x0..x1) for (y in y0..y1) add(TileKey(z, x, y))
        }
    }
}

/**
 * Decides which tiles make up the offline pack: a coarse regional overview around all sites, plus
 * detailed tiles hugging each site. Sized to stay small (a few hundred tiles, ~6 MB for 24 sites)
 * so the one-off download is polite to the tile server.
 */
object OfflineAreaPlanner {
    val REGION_ZOOMS = 10..13
    val DETAIL_ZOOMS = 14..17
    const val REGION_PADDING_DEG = 0.03
    const val SITE_PADDING_DEG = 0.0012 // about 130 m
    const val MAX_TILES = 2500

    fun plan(boundaries: List<List<GeoPoint>>): List<TileKey> {
        val sites = boundaries.filter { it.isNotEmpty() }
        if (sites.isEmpty()) return emptyList()
        val all = sites.flatten()
        val tiles = LinkedHashSet<TileKey>()
        for (z in REGION_ZOOMS) {
            tiles += TileMath.tilesInBox(
                all.minOf { it.lat } - REGION_PADDING_DEG, all.minOf { it.lng } - REGION_PADDING_DEG,
                all.maxOf { it.lat } + REGION_PADDING_DEG, all.maxOf { it.lng } + REGION_PADDING_DEG, z,
            )
        }
        for (site in sites) for (z in DETAIL_ZOOMS) {
            tiles += TileMath.tilesInBox(
                site.minOf { it.lat } - SITE_PADDING_DEG, site.minOf { it.lng } - SITE_PADDING_DEG,
                site.maxOf { it.lat } + SITE_PADDING_DEG, site.maxOf { it.lng } + SITE_PADDING_DEG, z,
            )
        }
        // Overview first, so if we ever hit the cap it is the finest detail that is dropped.
        return tiles.sortedBy { it.z }.take(MAX_TILES)
    }
}
