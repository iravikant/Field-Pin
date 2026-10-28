package com.fieldpin.ui.map

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.rememberScrollState
import com.fieldpin.domain.model.SiteStatus
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.ZoomOutMap
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fieldpin.BuildConfig
import com.fieldpin.core.Fmt
import com.fieldpin.ui.common.UiEvent
import com.fieldpin.ui.common.buttonLabel
import com.fieldpin.ui.common.headline
import com.fieldpin.ui.common.isGood
import com.fieldpin.ui.components.GpsOffBanner
import com.fieldpin.ui.components.MetricTile
import com.fieldpin.ui.components.PermissionKind
import com.fieldpin.ui.components.PermissionPrompt
import com.fieldpin.ui.components.PrimaryButton
import com.fieldpin.ui.components.SecondaryButton
import com.fieldpin.ui.components.SmallChip
import com.fieldpin.ui.components.StatusPill
import com.fieldpin.ui.components.rememberGpsEnabled
import com.fieldpin.ui.components.rememberPermissionController
import com.fieldpin.ui.theme.StatusColors
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.MapType
import com.google.maps.android.compose.rememberCameraPositionState
import org.koin.compose.viewmodel.koinViewModel
import kotlinx.coroutines.launch
import kotlin.math.max

private val IndiaOverview = CameraPosition.fromLatLngZoom(LatLng(22.5, 79.0), 4f)

@Composable
fun MapScreen(
    onOpenSite: (String) -> Unit,
    onOpenInspection: (String) -> Unit,
    onMessage: (String) -> Unit,
    vm: MapViewModel = koinViewModel(),
) {
    val sites by vm.sites.collectAsStateWithLifecycle()
    val visibleSites by vm.visibleSites.collectAsStateWithLifecycle()
    val filter by vm.filter.collectAsStateWithLifecycle()
    val selected by vm.selected.collectAsStateWithLifecycle()
    val fix by vm.live.fix.collectAsStateWithLifecycle()
    val permission = rememberPermissionController(PermissionKind.Location)
    val gpsOn by rememberGpsEnabled()

    val cameraState = rememberCameraPositionState { position = vm.camera ?: IndiaOverview }
    var mapType by rememberSaveable { mutableStateOf(MapType.NORMAL) }
    val basemap = rememberBasemap(mapType)
    var tilted by rememberSaveable { mutableStateOf(false) }
    var mapLoaded by remember { mutableStateOf(false) }
    var fitted by remember { mutableStateOf(vm.camera != null) }
    val paddingPx = with(LocalDensity.current) { 64.dp.roundToPx() }
    val selectedId = selected?.site?.id

    LaunchedEffect(permission.isGranted) { vm.live.granted.value = permission.isGranted }

    // First time only: show every site. Coming back to the tab restores the previous camera.
    LaunchedEffect(mapLoaded, sites.isNotEmpty()) {
        if (mapLoaded && !fitted && sites.isNotEmpty()) {
            fitted = true
            cameraState.fitTo(sites.flatMap { it.boundary }, paddingPx, animated = false)
        }
    }
    // Selecting a site flies the camera to it (tilted when 3D is on).
    LaunchedEffect(selectedId) {
        val site = sites.firstOrNull { it.id == selectedId } ?: return@LaunchedEffect
        cameraState.flyTo(site.centroid.toLatLng(), max(cameraState.position.zoom, 16.5f), if (tilted) 55f else 0f)
    }
    DisposableEffect(Unit) { onDispose { vm.camera = cameraState.position } }
    LaunchedEffect(vm) {
        vm.events.collect { event ->
            when (event) {
                is UiEvent.OpenInspection -> onOpenInspection(event.inspectionId)
                is UiEvent.Message -> onMessage(event.text)
            }
        }
    }

    Box(Modifier.fillMaxSize()) {
        FieldMapView(
            cameraState = cameraState,
            mapType = basemap.mapType,
            onMapClick = vm::onMapTap,
            onMapLoaded = { mapLoaded = true },
        ) {
            BasemapOverlay(basemap)
            SiteOverlays(sites = visibleSites, selectedId = selectedId, showPins = true, onSiteClick = vm::select)
            UserLocationOverlay(fix)
        }

        Column(Modifier.align(Alignment.TopCenter).fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (!BuildConfig.MAPS_KEY_CONFIGURED) MapsKeyBanner()
            OfflineMapStatus(basemap, Modifier.align(Alignment.CenterHorizontally))
            if (!permission.isGranted) {
                PermissionPrompt(permission, compact = true)
            } else if (!gpsOn) {
                GpsOffBanner()
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                Row(
                    Modifier.weight(1f).horizontalScroll(rememberScrollState()).padding(end = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    MapFilterChip("All", null, sites.size, filter == null) { vm.filter.value = null }
                    SiteStatus.entries.forEach { status ->
                        MapFilterChip(status.filterLabel, status, sites.count { it.status == status }, filter == status) {
                            vm.filter.value = if (filter == status) null else status
                        }
                    }
                }
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    MapFab(
                        Icons.Filled.Layers,
                        if (mapType == MapType.NORMAL) "Switch to satellite" else "Switch to map",
                        onClick = { mapType = if (mapType == MapType.NORMAL) MapType.HYBRID else MapType.NORMAL },
                        active = mapType != MapType.NORMAL,
                    )
                    val scope = rememberCoroutineScope()
                    MapTextFab(
                        "3D",
                        "Toggle 3D view",
                        onClick = {
                            tilted = !tilted
                            scope.launch { cameraState.setTilt(tilted) }
                        },
                        active = tilted,
                    )
                    MapFab(
                        Icons.Filled.ZoomOutMap,
                        "Show all sites",
                        onClick = { scope.launch { cameraState.fitTo(sites.flatMap { it.boundary }, paddingPx, animated = true) } },
                    )
                }
            }
        }

        Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth()) {
            val scope = rememberCoroutineScope()
            Column(Modifier.align(Alignment.End).padding(end = 16.dp, bottom = 12.dp)) {
                MapFab(
                    Icons.Filled.MyLocation,
                    "My location",
                    onClick = {
                        val here = fix
                        when {
                            !permission.isGranted -> permission.requestOrOpenSettings()
                            !gpsOn -> onMessage("Location is switched off. Turn it on to find yourself.")
                            here == null -> onMessage("Still waiting for a GPS fix…")
                            else -> scope.launch { cameraState.flyTo(here.point.toLatLng(), 18f, if (tilted) 55f else 0f) }
                        }
                    },
                    active = true,
                )
            }
            AnimatedVisibility(
                visible = selected != null,
                enter = slideInVertically { it } + fadeIn(),
                exit = slideOutVertically { it } + fadeOut(),
            ) {
                selected?.let { sel ->
                    SiteSheet(
                        selected = sel,
                        onCheckIn = { vm.checkIn(sel.site.id) },
                        onDetails = { onOpenSite(sel.site.id) },
                        onClose = vm::clearSelection,
                    )
                }
            }
        }
    }
}

/** Bottom sheet shown when a polygon is tapped: name, area, distance, and the geofenced check-in. */
@Composable
private fun SiteSheet(
    selected: MapViewModel.Selected,
    onCheckIn: () -> Unit,
    onDetails: () -> Unit,
    onClose: () -> Unit,
) {
    val site = selected.site
    val canCheckIn = selected.checkIn.canCheckIn
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        shadowElevation = 12.dp,
    ) {
        Column(Modifier.padding(start = 20.dp, end = 20.dp, bottom = 20.dp, top = 10.dp)) {
            Box(
                Modifier.align(Alignment.CenterHorizontally).width(40.dp).height(4.dp)
                    .clip(CircleShape).background(MaterialTheme.colorScheme.outline)
            )
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(site.name, style = MaterialTheme.typography.titleLarge, maxLines = 1)
                    Spacer(Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        SmallChip(site.category.label)
                        StatusPill(site.status)
                    }
                }
                IconButton(onClick = onClose) { Icon(Icons.Filled.Close, "Close") }
            }
            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                MetricTile("Area", Fmt.area(site.areaSqm), Modifier.weight(1f))
                MetricTile("Perimeter", Fmt.length(site.perimeterM), Modifier.weight(1f))
                MetricTile(
                    "From you",
                    when (val c = selected.checkIn) {
                        is com.fieldpin.domain.gis.CheckInStatus.Outside -> Fmt.distance(c.distanceM)
                        is com.fieldpin.domain.gis.CheckInStatus.NoLocation -> "—"
                        else -> "Inside"
                    },
                    Modifier.weight(1f),
                    valueColor = if (selected.checkIn.isGood()) StatusColors.Active else MaterialTheme.colorScheme.onSurface,
                )
            }
            Spacer(Modifier.height(10.dp))
            Text(
                selected.checkIn.headline(),
                style = MaterialTheme.typography.bodyMedium,
                color = if (selected.checkIn.isGood()) StatusColors.Active else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SecondaryButton("Details", onDetails, Modifier.weight(0.8f))
                PrimaryButton(selected.checkIn.buttonLabel(), onCheckIn, Modifier.weight(1.6f), enabled = canCheckIn)
            }
        }
    }
}
