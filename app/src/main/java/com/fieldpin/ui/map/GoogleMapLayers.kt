package com.fieldpin.ui.map

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import com.fieldpin.R
import com.fieldpin.domain.gis.GeoMath
import com.fieldpin.domain.model.GeoPoint
import com.fieldpin.domain.model.LocationFix
import com.fieldpin.domain.model.Site
import com.fieldpin.ui.theme.Teal400
import com.fieldpin.ui.theme.color
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.Dash
import com.google.android.gms.maps.model.Gap
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.android.gms.maps.model.MapStyleOptions
import com.google.maps.android.compose.CameraPositionState
import com.google.maps.android.compose.Circle
import com.google.maps.android.compose.DragState
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.GoogleMapComposable
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.MapType
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.Polygon
import com.google.maps.android.compose.Polyline
import com.google.maps.android.compose.rememberMarkerState
import kotlin.math.max

private val DraftInvalidRed = Color(0xFFEF4444)

/** The themed Google map used by both map screens. */
@Composable
fun FieldMapView(
    cameraState: CameraPositionState,
    mapType: MapType,
    onMapClick: (GeoPoint) -> Unit,
    onMapLoaded: () -> Unit,
    modifier: Modifier = Modifier.fillMaxSize(),
    content: @Composable @GoogleMapComposable () -> Unit,
) {
    val context = LocalContext.current
    val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val style = remember(dark) {
        MapStyleOptions.loadRawResourceStyle(context, if (dark) R.raw.map_style_dark else R.raw.map_style_light)
    }
    // The custom style only applies to the vector map; satellite/hybrid keep their imagery.
    val properties = remember(mapType, style) {
        MapProperties(
            mapType = mapType,
            mapStyleOptions = if (mapType == MapType.NORMAL) style else null,
            isBuildingsEnabled = true, // 3D buildings when the camera is tilted
            minZoomPreference = 3f,
            maxZoomPreference = 21f,
        )
    }
    val uiSettings = remember {
        MapUiSettings(
            zoomControlsEnabled = false,
            myLocationButtonEnabled = false, // we draw our own dot from the fused-location flow
            mapToolbarEnabled = false,
            compassEnabled = true,
            tiltGesturesEnabled = true,
            rotationGesturesEnabled = true,
            indoorLevelPickerEnabled = false,
        )
    }
    GoogleMap(
        modifier = modifier,
        cameraPositionState = cameraState,
        properties = properties,
        uiSettings = uiSettings,
        onMapClick = { onMapClick(it.toGeoPoint()) },
        onMapLoaded = onMapLoaded,
        content = content,
    )
}

@Composable
private fun rememberPinIcons(): PinIcons {
    val context = LocalContext.current
    return remember { PinIcons(context) }
}

/**
 * Every site as a status-coloured polygon, plus (when [showPins]) a 3D pin on top of each one.
 * The selected site gets a white outline and a larger, glowing pin that sits above the others.
 */
@Composable
@GoogleMapComposable
fun SiteOverlays(
    sites: List<Site>,
    selectedId: String?,
    showPins: Boolean,
    onSiteClick: (String) -> Unit,
) {
    val icons = rememberPinIcons()
    sites.forEach { site ->
        key(site.id) {
            val selected = site.id == selectedId
            val color = site.status.color()
            val ring = remember(site.boundary) { site.boundary.map { it.toLatLng() } }
            Polygon(
                points = ring,
                clickable = showPins,
                fillColor = color.copy(alpha = if (selected) 0.42f else 0.26f),
                strokeColor = if (selected) Color.White else color,
                strokeWidth = if (selected) 7f else 4f,
                zIndex = if (selected) 2f else 1f,
                onClick = { onSiteClick(site.id) },
            )
            if (showPins) {
                val anchor = remember(site.boundary) { GeoMath.interiorPoint(site.boundary).toLatLng() }
                val icon = icons.pin(site.status, selected)
                Marker(
                    state = rememberMarkerState(key = site.id, position = anchor),
                    icon = icon.descriptor,
                    anchor = Offset(0.5f, icon.anchorY),
                    zIndex = if (selected) 4f else 3f,
                    onClick = {
                        onSiteClick(site.id)
                        true // consume: no default info window
                    },
                )
            }
        }
    }
}

/** The user's position: accuracy circle plus a glowing dot. */
@Composable
@GoogleMapComposable
fun UserLocationOverlay(fix: LocationFix?) {
    if (fix == null) return
    val icons = rememberPinIcons()
    val position = fix.point.toLatLng()
    val state = rememberMarkerState(key = "user-location", position = position)
    LaunchedEffect(position) { state.position = position }
    Circle(
        center = position,
        radius = fix.accuracyM.coerceIn(1f, 500f).toDouble(),
        fillColor = Teal400.copy(alpha = 0.15f),
        strokeColor = Teal400.copy(alpha = 0.55f),
        strokeWidth = 2f,
        zIndex = 5f,
    )
    Marker(
        state = state,
        icon = icons.userDot(),
        anchor = Offset(0.5f, 0.5f),
        flat = true,
        zIndex = 6f,
        onClick = { true },
    )
}

/** The boundary being drawn: live polygon/line plus numbered, draggable corner handles. */
@Composable
@GoogleMapComposable
fun DraftOverlay(points: List<GeoPoint>, invalid: Boolean, onMovePoint: (Int, GeoPoint) -> Unit) {
    val icons = rememberPinIcons()
    val color = if (invalid) DraftInvalidRed else Teal400
    val ring = points.map { it.toLatLng() }
    if (ring.size >= 3) {
        Polygon(
            points = ring,
            fillColor = color.copy(alpha = 0.25f),
            strokeColor = color,
            strokeWidth = 6f,
            strokePattern = if (invalid) listOf(Dash(24f), Gap(14f)) else null,
            zIndex = 2f,
        )
    } else if (ring.size == 2) {
        Polyline(points = ring, color = color, width = 6f, zIndex = 2f)
    }
    points.forEachIndexed { index, point ->
        key(index) {
            val state = rememberMarkerState(position = point.toLatLng())
            LaunchedEffect(point) {
                if (state.dragState != DragState.DRAG) state.position = point.toLatLng()
            }
            LaunchedEffect(state.dragState) {
                if (state.dragState == DragState.END) onMovePoint(index, state.position.toGeoPoint())
            }
            Marker(
                state = state,
                icon = icons.numbered("${index + 1}", color.toArgb()),
                anchor = Offset(0.5f, 0.5f),
                draggable = true,
                zIndex = 7f,
                onClick = { true },
            )
        }
    }
}

// ------------------------------------------------------------------ camera helpers

suspend fun CameraPositionState.flyTo(target: LatLng, zoom: Float, tilt: Float, durationMs: Int = 800) {
    animate(
        CameraUpdateFactory.newCameraPosition(
            CameraPosition.Builder().target(target).zoom(zoom).tilt(tilt).bearing(position.bearing).build()
        ),
        durationMs,
    )
}

/** Tilts the camera for the 3D view (keeping the target) or flattens it again. */
suspend fun CameraPositionState.setTilt(tilted: Boolean) {
    flyTo(position.target, if (tilted) max(position.zoom, 17f) else position.zoom, if (tilted) 55f else 0f, 700)
}

suspend fun CameraPositionState.fitTo(points: List<GeoPoint>, paddingPx: Int, animated: Boolean) {
    if (points.isEmpty()) return
    val update = if (points.size == 1) {
        CameraUpdateFactory.newLatLngZoom(points[0].toLatLng(), 17f)
    } else {
        val bounds = LatLngBounds.Builder().apply { points.forEach { include(it.toLatLng()) } }.build()
        CameraUpdateFactory.newLatLngBounds(bounds, paddingPx)
    }
    if (animated) animate(update, 700) else move(update)
}
