package com.fieldpin.ui.detail

import android.content.ClipData
import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fieldpin.core.Fmt
import com.fieldpin.domain.model.Site
import com.fieldpin.domain.model.SiteCategory
import com.fieldpin.domain.model.SiteStatus
import com.fieldpin.ui.common.UiEvent
import com.fieldpin.ui.common.buttonLabel
import com.fieldpin.ui.common.headline
import com.fieldpin.ui.common.isGood
import com.fieldpin.ui.components.FpCard
import com.fieldpin.ui.components.FpTextField
import com.fieldpin.ui.components.GpsOffBanner
import com.fieldpin.ui.components.KeyValueRow
import com.fieldpin.ui.components.MetricTile
import com.fieldpin.ui.components.PendingBadge
import com.fieldpin.ui.components.PermissionKind
import com.fieldpin.ui.components.PermissionPrompt
import com.fieldpin.ui.components.PolygonPreview
import com.fieldpin.ui.components.PrimaryButton
import com.fieldpin.ui.components.SectionLabel
import com.fieldpin.ui.components.SmallChip
import com.fieldpin.ui.components.StatusDot
import com.fieldpin.ui.components.StatusPill
import com.fieldpin.ui.components.rememberGpsEnabled
import com.fieldpin.ui.components.rememberPermissionController
import com.fieldpin.ui.preview.DocumentViewer
import com.fieldpin.ui.preview.ImageGalleryViewer
import com.fieldpin.ui.preview.ViewerImage
import com.fieldpin.ui.theme.StatusColors
import com.fieldpin.ui.theme.color
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

@Composable
fun SiteDetailScreen(
    siteId: String,
    onBack: () -> Unit,
    onOpenInspection: (String) -> Unit,
    vm: SiteDetailViewModel = koinViewModel(parameters = { parametersOf(siteId) }),
) {
    val context = LocalContext.current
    val site by vm.site.collectAsStateWithLifecycle()
    val history by vm.history.collectAsStateWithLifecycle()
    val documents by vm.documents.collectAsStateWithLifecycle()
    val checkIn by vm.checkIn.collectAsStateWithLifecycle()
    val permission = rememberPermissionController(PermissionKind.Location)
    val gpsOn by rememberGpsEnabled()
    var editing by remember { mutableStateOf(false) }
    var previewId by remember { mutableStateOf<String?>(null) }
    var galleryStart by remember { mutableStateOf<Int?>(null) }
    var openDocumentId by remember { mutableStateOf<String?>(null) }

    // Files are copied into the app, so no long-lived permission on the picked URIs is needed.
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        vm.attachDocuments(uris)
    }
    val addFiles: () -> Unit = { picker.launch(arrayOf("*/*")) }

    LaunchedEffect(permission.isGranted) { vm.live.granted.value = permission.isGranted }
    LaunchedEffect(vm) {
        vm.events.collect { event ->
            when (event) {
                is UiEvent.OpenInspection -> onOpenInspection(event.inspectionId)
                is UiEvent.Message -> Toast.makeText(context, event.text, Toast.LENGTH_LONG).show()
            }
        }
    }

    val current: Site? = site
    val photos: List<ViewerImage> = remember(history, current?.name) {
        history.flatMap { inspection ->
            inspection.photos.map { path ->
                ViewerImage(
                    path = path,
                    title = current?.name,
                    subtitle = Fmt.dateTime(inspection.submittedAt ?: inspection.checkInAt),
                )
            }
        }
    }

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        Row(Modifier.padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
            Text(
                current?.name ?: "Site",
                style = MaterialTheme.typography.titleLarge,
                maxLines = 1,
                modifier = Modifier.weight(1f),
            )
            if (current != null) {
                IconButton(onClick = {
                    vm.export { uri ->
                        val send = Intent(Intent.ACTION_SEND).apply {
                            type = "application/geo+json"
                            putExtra(Intent.EXTRA_STREAM, uri)
                            clipData = ClipData.newRawUri("GeoJSON", uri)
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        context.startActivity(Intent.createChooser(send, "Export ${current.name} as GeoJSON"))
                    }
                }) { Icon(Icons.Filled.Share, "Export GeoJSON") }
                IconButton(onClick = { editing = true }) { Icon(Icons.Filled.Edit, "Edit details") }
            }
        }

        if (current == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("Site not found") }
            return@Column
        }

        LazyColumn(
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.fillMaxSize().navigationBarsPadding(),
        ) {
            // Hero: outline, status, category and size at a glance
            item {
                FpCard(modifier = Modifier.fillMaxWidth(), contentPadding = 0.dp) {
                    Box(Modifier.fillMaxWidth().height(210.dp)) {
                        PolygonPreview(current.boundary, current.status.color(), Modifier.fillMaxSize())
                        Row(
                            Modifier.align(Alignment.TopStart).padding(12.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            StatusPill(current.status)
                            if (current.hasPendingSync) PendingBadge()
                        }
                        SmallChip(current.category.label, modifier = Modifier.align(Alignment.TopEnd).padding(12.dp))
                        Column(Modifier.align(Alignment.BottomStart).padding(14.dp)) {
                            Text(Fmt.area(current.areaSqm), style = MaterialTheme.typography.headlineSmall)
                            Text(
                                Fmt.acres(current.areaSqm),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    MetricTile("Perimeter", Fmt.length(current.perimeterM), Modifier.weight(1f))
                    MetricTile("Corners", current.boundary.size.toString(), Modifier.weight(1f))
                    MetricTile("Inspections", history.size.toString(), Modifier.weight(1f))
                }
            }

            // Geofenced check-in
            item {
                FpCard(modifier = Modifier.fillMaxWidth()) {
                    Column {
                        Text(
                            checkIn.headline(),
                            style = MaterialTheme.typography.titleSmall,
                            color = if (checkIn.isGood()) StatusColors.Active else MaterialTheme.colorScheme.onSurface,
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "Check-in is only possible while you are inside the site boundary.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(12.dp))
                        if (!permission.isGranted) {
                            PermissionPrompt(permission, compact = true)
                            Spacer(Modifier.height(10.dp))
                        } else if (!gpsOn) {
                            GpsOffBanner()
                            Spacer(Modifier.height(10.dp))
                        }
                        PrimaryButton(
                            checkIn.buttonLabel(),
                            onClick = vm::startInspection,
                            enabled = checkIn.canCheckIn,
                            icon = Icons.Filled.PlayArrow,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }

            item {
                Column {
                    SectionLabel("Status")
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        SiteStatus.entries.forEach { status ->
                            FilterChip(
                                selected = current.status == status,
                                onClick = { vm.setStatus(status) },
                                label = { Text(status.label) },
                                leadingIcon = { StatusDot(status, 8.dp) },
                                shape = MaterialTheme.shapes.extraLarge,
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = status.color().copy(alpha = 0.2f),
                                    selectedLabelColor = MaterialTheme.colorScheme.onSurface,
                                ),
                            )
                        }
                    }
                }
            }

            item {
                Column {
                    SectionLabel("Details")
                    FpCard(modifier = Modifier.fillMaxWidth()) {
                        Column {
                            KeyValueRow("Category", current.category.label)
                            KeyValueRow("Centre", Fmt.coordinates(current.centroid))
                            KeyValueRow("Last inspection", current.lastInspectionAt?.let { Fmt.dateTime(it) } ?: "Never")
                            KeyValueRow("Sync", if (current.hasPendingSync) "Changes waiting to sync" else "Up to date")
                            if (current.description.isNotBlank()) {
                                Spacer(Modifier.height(6.dp))
                                Text(
                                    current.description,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }

            // Photos taken on this site's inspections, previewable full screen
            item {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    SectionHeader("Photos", photos.size)
                    SitePhotoStrip(photos, onOpen = { galleryStart = it })
                }
            }

            // Attached files: pick, list, preview
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SectionHeader("Documents", documents.size, actionLabel = "Add", onAction = addFiles)
                    if (documents.isEmpty()) {
                        EmptyDocuments(addFiles)
                    } else {
                        documents.forEach { document ->
                            DocumentRow(document, onClick = { openDocumentId = document.id })
                        }
                    }
                }
            }

            item { SectionHeader("Inspections", history.size) }
            if (history.isEmpty()) {
                item {
                    Text(
                        "No inspections submitted yet.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                items(history, key = { it.id }) { inspection ->
                    InspectionHistoryCard(inspection, current, onClick = { previewId = inspection.id })
                }
            }
        }
    }

    history.firstOrNull { it.id == previewId }?.let { inspection ->
        current?.let { InspectionPreviewSheet(inspection, it, onDismiss = { previewId = null }) }
    }

    galleryStart?.let { start ->
        ImageGalleryViewer(photos, start, onClose = { galleryStart = null })
    }

    documents.firstOrNull { it.id == openDocumentId }?.let { document ->
        DocumentViewer(
            document = document,
            onClose = { openDocumentId = null },
            onDelete = {
                openDocumentId = null
                vm.removeDocument(document.id)
            },
        )
    }

    if (editing && current != null) {
        EditDetailsDialog(
            site = current,
            onDismiss = { editing = false },
            onSave = { name, category, description ->
                editing = false
                vm.saveDetails(name, category, description)
            },
        )
    }
}

@Composable
private fun EditDetailsDialog(site: Site, onDismiss: () -> Unit, onSave: (String, SiteCategory, String) -> Unit) {
    var name by remember { mutableStateOf(site.name) }
    var category by remember { mutableStateOf(site.category) }
    var description by remember { mutableStateOf(site.description) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        title = { Text("Edit site") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                FpTextField(name, { name = it }, "Name", error = if (name.isBlank()) "Name is required" else null)
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
                FpTextField(description, { description = it }, "Notes", singleLine = false, minLines = 3)
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(name, category, description) }, enabled = name.isNotBlank()) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
