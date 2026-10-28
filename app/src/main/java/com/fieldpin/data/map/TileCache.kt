package com.fieldpin.data.map

import android.content.Context
import java.io.File

data class CacheStats(val tiles: Int = 0, val bytes: Long = 0)

/**
 * Tiles on disk as z/x/y.png under app-private files (not the cache dir, so Android does not
 * evict the offline map when storage is low). Writes are atomic: temp file then rename.
 */
class TileCache(context: Context) {
    private val root = File(context.filesDir, "map_tiles")

    private fun file(k: TileKey) = File(root, "${k.z}/${k.x}/${k.y}.png")

    fun has(k: TileKey): Boolean = file(k).isFile

    fun read(k: TileKey): ByteArray? = file(k).takeIf { it.isFile }?.readBytes()

    fun write(k: TileKey, bytes: ByteArray) {
        val target = file(k)
        target.parentFile?.mkdirs()
        val tmp = File(target.path + ".tmp")
        tmp.writeBytes(bytes)
        if (!tmp.renameTo(target)) {
            target.writeBytes(bytes)
            tmp.delete()
        }
    }

    fun stats(): CacheStats {
        var tiles = 0
        var bytes = 0L
        root.walkTopDown().filter { it.isFile && it.extension == "png" }.forEach {
            tiles++
            bytes += it.length()
        }
        return CacheStats(tiles, bytes)
    }

    fun clear() {
        root.deleteRecursively()
    }
}
