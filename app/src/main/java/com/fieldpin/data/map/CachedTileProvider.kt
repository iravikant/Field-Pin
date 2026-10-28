package com.fieldpin.data.map

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.Rect
import com.google.android.gms.maps.model.Tile
import com.google.android.gms.maps.model.TileProvider
import java.io.ByteArrayOutputStream
import java.io.IOException

/**
 * Serves map tiles to a Google Map TileOverlay: disk first, network second (when online), and a
 * scaled-up parent tile as a last resort so zooming in offline never shows a hole. Every tile
 * fetched while online is stored, so the pack also grows as the officer browses.
 * Called by the Maps SDK on background threads.
 */
class CachedTileProvider(
    private val cache: TileCache,
    private val fetcher: TileFetcher,
    private val isOnline: () -> Boolean,
    private val isDark: () -> Boolean,
) : TileProvider {

    override fun getTile(x: Int, y: Int, zoom: Int): Tile? {
        val key = TileKey(zoom, x, y)
        val png = loadNative(key) ?: overzoom(key) ?: return TileProvider.NO_TILE
        return Tile(TILE_SIZE, TILE_SIZE, if (isDark()) darken(png) else png)
    }

    private fun loadNative(key: TileKey): ByteArray? {
        if (key.z > MAX_NATIVE_ZOOM) return null
        cache.read(key)?.let { return it }
        if (!isOnline()) return null
        return try {
            fetcher.fetch(key)?.also { cache.write(key, it) }
        } catch (e: IOException) {
            null
        }
    }

    /** Crops the matching quarter/sixteenth of a saved lower-zoom tile and scales it to 256 px. */
    private fun overzoom(key: TileKey): ByteArray? {
        for (d in 1..MAX_OVERZOOM_STEPS) {
            val parentZ = key.z - d
            if (parentZ < 0) return null
            val ax = key.x shr d
            val ay = key.y shr d
            val parent = cache.read(TileKey(parentZ, ax, ay)) ?: continue
            val bitmap = BitmapFactory.decodeByteArray(parent, 0, parent.size) ?: continue
            val sub = TILE_SIZE shr d
            val sx = (key.x - (ax shl d)) * sub
            val sy = (key.y - (ay shl d)) * sub
            val out = Bitmap.createBitmap(TILE_SIZE, TILE_SIZE, Bitmap.Config.ARGB_8888)
            Canvas(out).drawBitmap(
                bitmap, Rect(sx, sy, sx + sub, sy + sub), Rect(0, 0, TILE_SIZE, TILE_SIZE),
                Paint(Paint.FILTER_BITMAP_FLAG),
            )
            bitmap.recycle()
            return out.toPng().also { out.recycle() }
        }
        return null
    }

    /** Inverts the bright map into a dark one and mutes the colours, to match the app's dark theme. */
    private fun darken(png: ByteArray): ByteArray {
        val src = BitmapFactory.decodeByteArray(png, 0, png.size) ?: return png
        val out = Bitmap.createBitmap(src.width, src.height, Bitmap.Config.ARGB_8888)
        val paint = Paint().apply { colorFilter = DARK_FILTER }
        Canvas(out).drawBitmap(src, 0f, 0f, paint)
        src.recycle()
        return out.toPng().also { out.recycle() }
    }

    private fun Bitmap.toPng(): ByteArray =
        ByteArrayOutputStream().also { compress(Bitmap.CompressFormat.PNG, 100, it) }.toByteArray()

    private companion object {
        const val TILE_SIZE = 256
        const val MAX_NATIVE_ZOOM = 19
        const val MAX_OVERZOOM_STEPS = 5

        val DARK_FILTER = ColorMatrixColorFilter(
            ColorMatrix(
                floatArrayOf(
                    -1f, 0f, 0f, 0f, 255f,
                    0f, -1f, 0f, 0f, 255f,
                    0f, 0f, -1f, 0f, 255f,
                    0f, 0f, 0f, 1f, 0f,
                )
            ).apply { postConcat(ColorMatrix().apply { setSaturation(0.25f) }) }
        )
    }
}
