package com.fieldpin.data

import com.fieldpin.data.map.OfflineAreaPlanner
import com.fieldpin.data.map.TileMath
import com.fieldpin.domain.model.GeoPoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OfflineAreaPlannerTest {
    private val lodhi = listOf(
        GeoPoint(28.5900, 77.2170), GeoPoint(28.5900, 77.2225), GeoPoint(28.5960, 77.2225), GeoPoint(28.5960, 77.2170),
    )

    @Test
    fun tileMath_matchesKnownSlippyMapIndices() {
        // New Delhi (28.6139 N, 77.2090 E), checked against the OSM slippy-map formula
        assertEquals(2926, TileMath.tileX(77.2090, 12))
        assertEquals(1707, TileMath.tileY(28.6139, 12))
        assertEquals(93646, TileMath.tileX(77.2090, 17))
        assertEquals(54655, TileMath.tileY(28.6139, 17))
        assertEquals(0, TileMath.tileX(77.2090, 0))
        assertEquals(0, TileMath.tileY(28.6139, 0))
    }

    @Test
    fun tilesInBox_northHasSmallerY_andBoxIsCovered() {
        val tiles = TileMath.tilesInBox(28.59, 77.217, 28.596, 77.2225, 15)
        assertTrue(tiles.isNotEmpty())
        val pointTile = TileMath.tileX(77.220, 15) to TileMath.tileY(28.593, 15)
        assertTrue(tiles.any { it.x == pointTile.first && it.y == pointTile.second })
    }

    @Test
    fun plan_isEmptyWithoutSites() {
        assertTrue(OfflineAreaPlanner.plan(emptyList()).isEmpty())
        assertTrue(OfflineAreaPlanner.plan(listOf(emptyList())).isEmpty())
    }

    @Test
    fun plan_coversOverviewAndDetailZooms_withoutDuplicates() {
        val plan = OfflineAreaPlanner.plan(listOf(lodhi))
        assertEquals(plan.size, plan.toSet().size)
        assertEquals(OfflineAreaPlanner.REGION_ZOOMS.first, plan.minOf { it.z })
        assertEquals(OfflineAreaPlanner.DETAIL_ZOOMS.last, plan.maxOf { it.z })
        // the site centre must be covered at the finest zoom
        val z = OfflineAreaPlanner.DETAIL_ZOOMS.last
        assertTrue(plan.any { it.z == z && it.x == TileMath.tileX(77.2197, z) && it.y == TileMath.tileY(28.593, z) })
    }

    @Test
    fun plan_isOrderedCoarseToFine_andCapped() {
        val many = (0 until 400).map { i ->
            val lat = 20.0 + i * 0.05
            listOf(GeoPoint(lat, 70.0 + i * 0.05), GeoPoint(lat + 0.01, 70.0 + i * 0.05), GeoPoint(lat + 0.01, 70.01 + i * 0.05))
        }
        val plan = OfflineAreaPlanner.plan(many)
        assertTrue(plan.size <= OfflineAreaPlanner.MAX_TILES)
        assertEquals(plan.sortedBy { it.z }, plan)
    }
}
