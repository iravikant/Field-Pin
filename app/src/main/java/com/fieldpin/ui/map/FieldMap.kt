package com.fieldpin.ui.map

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.view.animation.LinearInterpolator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.core.graphics.ColorUtils
import androidx.core.view.doOnLayout
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.fieldpin.R
import com.fieldpin.domain.gis.GeoMath
import com.fieldpin.domain.model.GeoPoint
import com.fieldpin.domain.model.LocationFix
import com.fieldpin.domain.model.Site
import com.fieldpin.domain.model.SiteStatus
import com.fieldpin.ui.theme.Teal400
import com.fieldpin.ui.theme.color
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.GoogleMapOptions
import com.google.android.gms.maps.MapView
import com.google.android.gms.maps.model.BitmapDescriptor
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.Circle
import com.google.android.gms.maps.model.CircleOptions
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.android.gms.maps.model.MapStyleOptions
import com.google.android.gms.maps.model.Marker
import com.google.android.gms.maps.model.MarkerOptions
import com.google.android.gms.maps.model.Polygon
import com.google.android.gms.maps.model.PolygonOptions
import com.google.android.gms.maps.model.Polyline
import com.google.android.gms.maps.model.PolylineOptions
import com.google.android.gms.maps.model.PatternItem
import com.google.android.gms.maps.model.Dash
import com.google.android.gms.maps.model.Gap

data class CameraSnapshot(val lat: Double, val lng: Double, val zoom: Double)

/**
 * Thin wrapper around a Google Maps [MapView] used by the Map and "Map a new site" screens.
 *
 * Every site is shown as its coloured boundary polygon plus a glossy 3D pin ([PinFactory]) at an
 * interior point. The Google map loads asynchronously, so every call made before it is ready is
 * queued and replayed in order.
 *
 * Taps: a pin tap calls [onSiteTap] (Map tab). If no [onSiteTap] is set (Draw screen) the pin tap is
 * forwarded to [onTap] so corners can still be dropped on top of a pin. Plain map taps call [onTap];
 * the ViewModels use their own tested point-in-polygon on it.
 */
class FieldMap(private val context: Context) {
    val view: MapView = MapView(
        context,
        GoogleMapOptions()
            .camera(CameraPosition.fromLatLngZoom(LatLng(22.5, 79.0), 4.5f)) // India, until we fit to real data
            .mapToolbarEnabled(false)
            .zoomControlsEnabled(false)
            .compassEnabled(true)
            .rotateGesturesEnabled(true)
            .tiltGesturesEnabled(true),
    ).also { it.onCreate(null) }

    var onTap: ((GeoPoint) -> Unit)? = null
    var onSiteTap: ((String) -> Unit)? = null

    private val density = context.resources.displayMetrics.density
    private var map: GoogleMap? = null
    private val queue = ArrayDeque<(GoogleMap) -> Unit>()

    private var sitePolygons: List<Polygon> = emptyList()
    private var sitePins: List<Marker> = emptyList()
    private var draftPolygons: List<Polygon> = emptyList()
    private var draftLines: List<Polyline> = emptyList()
    private var draftMarkers: List<Marker> = emptyList()
    private var accuracyCircle: Circle? = null
    private var dot: Marker? = null
    private var fixAnimator: ValueAnimator? = null
    private val pinCache = HashMap<Pair<SiteStatus, Boolean>, BitmapDescriptor>()
    private var lastCamera: CameraSnapshot? = null

    init {
        view.getMapAsync { gm ->
            gm.uiSettings.isMyLocationButtonEnabled = false
            gm.uiSettings.isIndoorLevelPickerEnabled = false
            gm.isBuildingsEnabled = true // extruded 3D buildings when zoomed in
            gm.setMinZoomPreference(3f)
            gm.setMaxZoomPreference(21f)
            gm.setOnMapClickListener { ll -> onTap?.invoke(GeoPoint(ll.latitude, ll.longitude)) }
            gm.setOnMarkerClickListener { marker ->
                val siteId = marker.tag as? String
                if (siteId != null) {
                    val tapSite = onSiteTap
                    if (tapSite != null) tapSite(siteId)
                    else onTap?.invoke(GeoPoint(marker.position.latitude, marker.position.longitude))
                }
                true // never show Google's default info window
            }
            map = gm
            while (queue.isNotEmpty()) queue.removeFirst()(gm)
        }
    }

    private fun withMap(block: (GoogleMap) -> Unit) {
        val gm = map
        if (gm != null) block(gm) else queue.addLast(block)
    }

    // ----------------------------------------------------------- appearance

    fun setDark(dark: Boolean) = withMap { gm ->
        val style = MapStyleOptions.loadRawResourceStyle(context, if (dark) R.raw.map_style_dark else R.raw.map_style_light)
        gm.setMapStyle(style)
    }

    fun setSatellite(on: Boolean) = withMap { gm ->
        gm.mapType = if (on) GoogleMap.MAP_TYPE_HYBRID else GoogleMap.MAP_TYPE_NORMAL
    }

    // -------------------------------------------------------------- overlays

    private fun pinIcon(status: SiteStatus, selected: Boolean): BitmapDescriptor =
        pinCache.getOrPut(status to selected) {
            BitmapDescriptorFactory.fromBitmap(PinFactory.create(density, status.color().toArgb(), selected))
        }

    fun showSites(sites: List<Site>, selectedId: String?) = withMap { gm ->
        sitePolygons.forEach { it.remove() }
        sitePins.forEach { it.remove() }
        // The selected site is drawn last so its outline is on top.
        val ordered = sites.sortedBy { it.id == selectedId }
        sitePolygons = ordered.map { site ->
            val selected = site.id == selectedId
            val argb = site.status.color().toArgb()
            gm.addPolygon(
                PolygonOptions()
                    .addAll(site.boundary.map { LatLng(it.lat, it.lng) })
                    .fillColor(ColorUtils.setAlphaComponent(argb, if (selected) 120 else 70))
                    .strokeColor(if (selected) Color.WHITE else argb)
                    .strokeWidth((if (selected) 5f else 3f) * density / 2f)
                    .zIndex(if (selected) 2f else 1f)
            )
        }
        sitePins = ordered.mapNotNull { site ->
            if (site.boundary.size < 3) return@mapNotNull null
            val selected = site.id == selectedId
            val at = GeoMath.interiorPoint(site.boundary)
            gm.addMarker(
                MarkerOptions()
                    .position(LatLng(at.lat, at.lng))
                    .icon(pinIcon(site.status, selected))
                    .anchor(0.5f, PinFactory.ANCHOR_Y)
                    .zIndex(if (selected) 10f else 5f)
            )?.also { it.tag = site.id }
        }
    }

    /** The boundary being drawn on the "Map a new site" screen. */
    fun showDraft(points: List<GeoPoint>, invalid: Boolean) = withMap { gm ->
        draftPolygons.forEach { it.remove() }
        draftLines.forEach { it.remove() }
        draftMarkers.forEach { it.remove() }
        val argb = if (invalid) Color.rgb(239, 68, 68) else Teal400.toArgb()
        val ring = points.map { LatLng(it.lat, it.lng) }
        val dashes: List<PatternItem>? = if (invalid) listOf(Dash(20f), Gap(14f)) else null
        draftPolygons = if (ring.size >= 3) {
            listOf(
                gm.addPolygon(
                    PolygonOptions().addAll(ring)
                        .fillColor(ColorUtils.setAlphaComponent(argb, 70))
                        .strokeColor(argb)
                        .strokeWidth(2.5f * density)
                        .strokePattern(dashes)
                        .zIndex(3f)
                )
            )
        } else emptyList()
        draftLines = if (ring.size == 2) {
            listOf(gm.addPolyline(PolylineOptions().addAll(ring).color(argb).width(2.5f * density).zIndex(3f)))
        } else emptyList()
        draftMarkers = ring.mapIndexedNotNull { i, p ->
            gm.addMarker(
                MarkerOptions().position(p)
                    .icon(BitmapDescriptorFactory.fromBitmap(numberedPoint("${i + 1}", argb)))
                    .anchor(0.5f, 0.5f)
                    .zIndex(20f)
            )
        }
    }

    fun showFix(fix: LocationFix?) = withMap { gm ->
        if (fix == null) {
            fixAnimator?.cancel()
            dot?.isVisible = false
            accuracyCircle?.isVisible = false
        } else {
            val here = LatLng(fix.point.lat, fix.point.lng)
            val radius = fix.accuracyM.coerceIn(1f, 500f).toDouble()
            val circle = accuracyCircle ?: gm.addCircle(
                CircleOptions().center(here).radius(radius)
                    .fillColor(ColorUtils.setAlphaComponent(Teal400.toArgb(), 40))
                    .strokeColor(ColorUtils.setAlphaComponent(Teal400.toArgb(), 140))
                    .strokeWidth(2f).zIndex(4f)
            ).also { accuracyCircle = it }

            val marker = dot ?: gm.addMarker(
                MarkerOptions().position(here)
                    .icon(BitmapDescriptorFactory.fromBitmap(locationDot()))
                    .anchor(0.5f, 0.5f).flat(true).zIndex(30f)
            )?.also { dot = it }

            // Glide from the previous position instead of teleporting, like a workout app's live dot.
            val wasVisible = circle.isVisible && marker?.isVisible == true
            val from = marker?.position ?: here
            val fromRadius = circle.radius
            fixAnimator?.cancel()
            if (!wasVisible || marker == null) {
                circle.center = here
                circle.radius = radius
                marker?.position = here
            } else {
                fixAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
                    duration = 900L
                    interpolator = LinearInterpolator()
                    addUpdateListener { a ->
                        val t = a.animatedFraction
                        val p = LatLng(
                            from.latitude + (here.latitude - from.latitude) * t,
                            from.longitude + (here.longitude - from.longitude) * t,
                        )
                        marker.position = p
                        circle.center = p
                        circle.radius = fromRadius + (radius - fromRadius) * t
                    }
                    start()
                }
            }
            circle.isVisible = true
            marker?.isVisible = true
        }
    }

    // ---------------------------------------------------------------- camera

    fun fitTo(points: List<GeoPoint>) {
        if (points.isEmpty()) return
        withMap { gm ->
            view.doOnLayout {
                if (points.size == 1) {
                    gm.moveCamera(CameraUpdateFactory.newLatLngZoom(LatLng(points[0].lat, points[0].lng), 17f))
                } else {
                    val b = LatLngBounds.Builder()
                    points.forEach { b.include(LatLng(it.lat, it.lng)) }
                    gm.moveCamera(CameraUpdateFactory.newLatLngBounds(b.build(), (56 * density).toInt()))
                }
            }
        }
    }

    fun animateTo(point: GeoPoint, zoom: Double = 18.0) = withMap { gm ->
        gm.animateCamera(CameraUpdateFactory.newLatLngZoom(LatLng(point.lat, point.lng), zoom.toFloat()), 600, null)
    }

    fun snapshot(): CameraSnapshot {
        val pos = map?.cameraPosition ?: return lastCamera ?: CameraSnapshot(22.5, 79.0, 4.5)
        return CameraSnapshot(pos.target.latitude, pos.target.longitude, pos.zoom.toDouble())
            .also { lastCamera = it }
    }

    fun restore(s: CameraSnapshot) = withMap { gm ->
        gm.moveCamera(CameraUpdateFactory.newLatLngZoom(LatLng(s.lat, s.lng), s.zoom.toFloat()))
    }

    // ---------------------------------------------------------------- drawing

    private fun locationDot(): Bitmap {
        val size = (22 * density).toInt()
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        p.color = Color.WHITE
        c.drawCircle(size / 2f, size / 2f, size / 2f, p)
        p.color = Teal400.toArgb()
        c.drawCircle(size / 2f, size / 2f, size / 2f - 3 * density, p)
        return bmp
    }

    private fun numberedPoint(label: String, argb: Int): Bitmap {
        val size = (28 * density).toInt()
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.color = Color.WHITE
        canvas.drawCircle(size / 2f, size / 2f, size / 2f, paint)
        paint.color = argb
        canvas.drawCircle(size / 2f, size / 2f, size / 2f - 2.5f * density, paint)
        paint.color = Color.BLACK
        paint.textAlign = Paint.Align.CENTER
        paint.isFakeBoldText = true
        paint.textSize = 12 * density
        canvas.drawText(label, size / 2f, size / 2f - (paint.descent() + paint.ascent()) / 2f, paint)
        return bmp
    }
}

@Composable
fun rememberFieldMap(): FieldMap {
    val context = LocalContext.current
    val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val map = remember { FieldMap(context) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle

    DisposableEffect(lifecycle, map) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> map.view.onStart()
                Lifecycle.Event.ON_RESUME -> map.view.onResume()
                Lifecycle.Event.ON_PAUSE -> map.view.onPause()
                Lifecycle.Event.ON_STOP -> map.view.onStop()
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
            // Walk the view down in order; the lifecycle may not have delivered pause/stop yet.
            if (lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) map.view.onPause()
            if (lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) map.view.onStop()
            map.view.onDestroy()
        }
    }
    LaunchedEffect(dark, map) { map.setDark(dark) }
    return map
}
