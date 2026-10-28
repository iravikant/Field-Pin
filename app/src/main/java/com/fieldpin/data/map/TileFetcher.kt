package com.fieldpin.data.map

import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/**
 * Downloads one tile from an XYZ template such as `https://host/{z}/{x}/{y}.png`. The template
 * is build configuration (BuildConfig.TILE_URL_TEMPLATE), never a literal in code. A descriptive
 * User-Agent is sent because public tile servers require one.
 */
class TileFetcher(private val template: String, private val userAgent: String) {
    /** Returns the PNG bytes, or null if the server has no such tile. Throws on network failure. */
    @Throws(IOException::class)
    fun fetch(k: TileKey): ByteArray? {
        val url = URL(template.replace("{z}", k.z.toString()).replace("{x}", k.x.toString()).replace("{y}", k.y.toString()))
        val connection = url.openConnection() as HttpURLConnection
        try {
            connection.setRequestProperty("User-Agent", userAgent)
            connection.connectTimeout = TIMEOUT_MS
            connection.readTimeout = TIMEOUT_MS
            if (connection.responseCode != HttpURLConnection.HTTP_OK) return null
            return connection.inputStream.use { it.readBytes() }
        } finally {
            connection.disconnect()
        }
    }

    private companion object {
        const val TIMEOUT_MS = 8_000
    }
}
