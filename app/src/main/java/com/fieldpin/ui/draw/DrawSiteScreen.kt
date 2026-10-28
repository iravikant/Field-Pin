package com.fieldpin.ui.draw

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.GpsFixed
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fieldpin.core.Fmt
import com.fieldpin.domain.model.SiteCategory
import com.fieldpin.domain.model.SiteStatus
import com.fieldpin.ui.components.FpTextField
import com.fieldpin.ui.components.GpsOffBanner
import com.fieldpin.ui.components.MetricTile
import com.fieldpin.ui.components.PermissionKind
import com.fieldpin.ui.components.PrimaryButton
import com.fieldpin.ui.components.SecondaryButton
import com.fieldpin.ui.components.rememberGpsEnabled
import com.fieldpin.ui.components.rememberPermissionController
import com.fieldpin.ui.components.StatusDot
import com.fieldpin.BuildConfig
import com.fieldpin.ui.map.DraftOverlay
import com.fieldpin.ui.map.FieldMapView
import com.fieldpin.ui.map.MapFab
import com.fieldpin.ui.map.MapsKeyBanner
import com.fieldpin.ui.map.SiteOverlays
import com.fieldpin.ui.map.UserLocationOverlay
import com.fieldpin.ui.map.fitTo
import com.fieldpin.ui.map.flyTo
import com.fieldpin.ui.map.toLatLng
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.MapType
import com.google.maps.android.compose.rememberCameraPositionState
import com.fieldpin.ui.theme.StatusColors
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun DrawSiteScreen(onBack: () -> Unit, vm: DrawSiteViewModel = koinViewModel()) {
    val context = LocalContext.current
    val sites by vm.sites.collectAsStateWithLifecycle()
    val points by vm.points.collectAsStateWithLifecycle()
    val metrics by vm.metrics.collectAsStateWithLifecycle()
    val fix by vm.live.fix.collectAsStateWithLifecycle()
    val permission = rememberPermissionController(PermissionKind.Location)
    val gpsOn by rememberGpsEnabled()
    var showSave by remember { mutableStateOf(false) }

    val cameraState = rememberCameraPositionState { position = CameraPosition.fromLatLngZoom(LatLng(22.5, 79.0), 4f) }
    // Satellite imagery is the best canvas for tracing a boundary.
    var mapType by rememberSaveable { mutableStateOf(MapType.HYBRID) }
    var mapLoaded by remember { mutableStateOf(false) }
    var positioned by remember { mutableStateOf(false) }
    val paddingPx = with(LocalDensity.current) { 64.dp.roundToPx() }

    LaunchedEffect(permission.isGranted) { vm.live.granted.value = permission.isGranted }
    // Start on the user when their position is known, otherwise on the existing sites.
    LaunchedEffect(mapLoaded, fix != null, sites.isNotEmpty()) {
        if (!mapLoaded || positioned) return@LaunchedEffect
        val here = fix
        if (here != null) {
            positioned = true
            cameraState.flyTo(here.point.toLatLng(), 18f, 0f)
        } else if (sites.isNotEmpty()) {
            cameraState.fitTo(sites.flatMap { it.boundary }, paddingPx, animated = false)
        }
    }

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        FieldMapView(
            cameraState = cameraState,
            mapType = mapType,
            onMapClick = vm::addPoint,
            onMapLoaded = { mapLoaded = true },
        ) {
            SiteOverlays(sites = sites, selectedId = null, showPins = false, onSiteClick = {})
            DraftOverlay(points = points, invalid = metrics.selfIntersects, onMovePoint = vm::movePoint)
            UserLocationOverlay(fix)
        }

        Column(Modifier.statusBarsPadding().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (!BuildConfig.MAPS_KEY_CONFIGURED) MapsKeyBanner()
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surfaceContainerHigh, shadowElevation = 4.dp) {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
                }
                Spacer(Modifier.width(10.dp))
                Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surfaceContainerHigh, shadowElevation = 4.dp) {
                    Row(Modifier.padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.TouchApp, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.height(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(
                            if (points.isEmpty()) "Tap the map to drop corners" else "Drag a corner to adjust",
                            style = MaterialTheme.typography.labelLarge,
                        )
                    }
                }
                Spacer(Modifier.weight(1f))
                MapFab(
                    Icons.Filled.Layers,
                    if (mapType == MapType.NORMAL) "Switch to satellite" else "Switch to map",
                    onClick = { mapType = if (mapType == MapType.NORMAL) MapType.HYBRID else MapType.NORMAL },
                    active = mapType != MapType.NORMAL,
                )
            }
            if (permission.isGranted && !gpsOn) GpsOffBanner()
        }

        Surface(
            modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth(),
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
            color = MaterialTheme.colorScheme.surfaceContainer,
            shadowElevation = 12.dp,
        ) {
            Column(Modifier.navigationBarsPadding().padding(20.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "${metrics.pointCount} ${if (metrics.pointCount == 1) "point" else "points"}",
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.weight(1f),
                    )
                    val f = fix
                    if (f != null) {
                        StatusDot(if (f.accuracyM <= DrawSiteViewModel.MAX_GPS_ACCURACY_M) SiteStatus.ACTIVE else SiteStatus.NEEDS_REPAIR, 8.dp)
                        Spacer(Modifier.width(6.dp))
                        Text(
                            "GPS ${Fmt.accuracy(f.accuracyM)}",
                            style = MaterialTheme.typography.labelMedium,
                            color = if (f.accuracyM <= DrawSiteViewModel.MAX_GPS_ACCURACY_M) StatusColors.Active else StatusColors.Repair,
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    MetricTile(
                        "Area",
                        if (metrics.pointCount >= 3) Fmt.area(metrics.areaSqm) else "—",
                        Modifier.weight(1.2f),
                    )
                    MetricTile(
                        "Acres",
                        if (metrics.pointCount >= 3) Fmt.acres(metrics.areaSqm) else "—",
                        Modifier.weight(1f),
                    )
                    MetricTile(
                        "Perimeter",
                        if (metrics.pointCount >= 2) Fmt.length(metrics.perimeterM) else "—",
                        Modifier.weight(1f),
                    )
                }
                val problem = metrics.problem
                Spacer(Modifier.height(10.dp))
                Text(
                    problem ?: "Looks good. Save to store it on this device.",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (problem != null && metrics.selfIntersects) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(14.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    SecondaryButton("Undo", vm::undo, Modifier.weight(1f), enabled = points.isNotEmpty(), icon = Icons.AutoMirrored.Filled.Undo)
                    SecondaryButton("Clear", vm::clear, Modifier.weight(1f), enabled = points.isNotEmpty(), icon = Icons.Filled.DeleteOutline)
                    SecondaryButton(
                        "GPS point",
                        onClick = {
                            when {
                                !permission.isGranted -> permission.requestOrOpenSettings()
                                !gpsOn -> Toast.makeText(context, "Turn on location first", Toast.LENGTH_SHORT).show()
                                else -> vm.addGpsPoint()?.let { Toast.makeText(context, it, Toast.LENGTH_LONG).show() }
                            }
                        },
                        modifier = Modifier.weight(1.3f),
                        icon = Icons.Filled.GpsFixed,
                    )
                }
                Spacer(Modifier.height(10.dp))
                PrimaryButton("Save site", onClick = { showSave = true }, modifier = Modifier.fillMaxWidth(), enabled = problem == null)
            }
        }
    }

    if (showSave) {
        SaveSiteDialog(
            onDismiss = { showSave = false },
            onSave = { name, category, status, description ->
                showSave = false
                vm.save(name, category, status, description) {
                    Toast.makeText(context, "Site saved. It will sync when you're online.", Toast.LENGTH_SHORT).show()
                    onBack()
                }
            },
        )
    }
}

@Composable
private fun SaveSiteDialog(
    onDismiss: () -> Unit,
    onSave: (String, SiteCategory, SiteStatus, String) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var category by remember { mutableStateOf(SiteCategory.PARK) }
    var status by remember { mutableStateOf(SiteStatus.ACTIVE) }
    var showError by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        title = { Text("Save site") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                FpTextField(
                    value = name,
                    onValueChange = { name = it; showError = false },
                    label = "Site name",
                    error = if (showError) "Give the site a name" else null,
                )
                Text("Category", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SiteCategory.entries.forEach { c ->
                        FilterChip(
                            selected = category == c,
                            onClick = { category = c },
                            label = { Text(c.label) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            ),
                        )
                    }
                }
                Text("Status", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SiteStatus.entries.forEach { s ->
                        FilterChip(
                            selected = status == s,
                            onClick = { status = s },
                            label = { Text(s.label) },
                            leadingIcon = { StatusDot(s, 8.dp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            ),
                        )
                    }
                }
                FpTextField(description, { description = it }, "Notes (optional)", singleLine = false, minLines = 2)
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (name.isBlank()) showError = true else onSave(name, category, status, description)
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
