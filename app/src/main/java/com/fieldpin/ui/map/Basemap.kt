package com.fieldpin.ui.map

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.graphics.luminance
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fieldpin.BuildConfig
import com.fieldpin.core.NetworkMonitor
import com.fieldpin.data.map.CachedTileProvider
import com.fieldpin.data.map.OfflineMapManager
import com.google.maps.android.compose.GoogleMapComposable
import com.google.maps.android.compose.MapType
import com.google.maps.android.compose.TileOverlay
import org.koin.compose.koinInject

/**
 * What the map draws underneath the sites.
 * - Normal: Google's own map (or satellite), live.
 * - Saved map: when the device is offline (or no Google key is configured) Google's base map is
 *   switched off and our own cached tiles are drawn instead, so the officer still sees a map.
 */
class Basemap(
    val mapType: MapType,
    val provider: CachedTileProvider?,
    val dark: Boolean,
    /** Text the map must show when it is not Google's, e.g. the tile credit; null for Google. */
    val attribution: String?,
    val offline: Boolean,
)

@Composable
fun rememberBasemap(userMapType: MapType): Basemap {
    val manager: OfflineMapManager = koinInject()
    val network: NetworkMonitor = koinInject()
    val online by network.isOnline.collectAsStateWithLifecycle()
    val stats by manager.stats.collectAsStateWithLifecycle()
    val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f

    val onlineNow: State<Boolean> = rememberUpdatedState(online)
    val darkNow: State<Boolean> = rememberUpdatedState(dark)
    val provider = remember(manager) {
        CachedTileProvider(manager.cache, manager.fetcher, isOnline = { onlineNow.value }, isDark = { darkNow.value })
    }

    val noKey = !BuildConfig.MAPS_KEY_CONFIGURED
    val useSaved = (!online && stats.tiles > 0) || noKey
    return Basemap(
        mapType = if (useSaved) MapType.NONE else userMapType,
        provider = if (useSaved) provider else null,
        dark = dark,
        attribution = if (useSaved) "© OpenStreetMap contributors" else null,
        offline = !online && stats.tiles > 0,
    )
}

/** Draws the saved tiles; recreated when the theme flips so tiles are re-darkened. */
@Composable
@GoogleMapComposable
fun BasemapOverlay(basemap: Basemap) {
    val provider = basemap.provider ?: return
    key(basemap.dark) { TileOverlay(tileProvider = provider, zIndex = 0f) }
}
