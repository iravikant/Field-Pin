package com.fieldpin.ui.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.fieldpin.core.Fmt
import com.fieldpin.domain.model.Inspection
import com.fieldpin.domain.model.Site
import com.fieldpin.ui.components.FileImage
import com.fieldpin.ui.components.FpCard
import com.fieldpin.ui.components.KeyValueRow
import com.fieldpin.ui.components.SectionLabel
import com.fieldpin.ui.components.SmallChip
import com.fieldpin.ui.preview.ImageGalleryViewer
import com.fieldpin.ui.preview.ViewerImage
import com.fieldpin.ui.theme.StatusColors
import com.fieldpin.ui.theme.color

/**
 * One row of the "Inspections" history: photo strip, date, tags (condition, repair flag, site category,
 * sync state) and a two-line notes preview. Tap to open [InspectionPreviewSheet].
 */
@Composable
fun InspectionHistoryCard(inspection: Inspection, site: Site, onClick: () -> Unit) {
    FpCard(onClick = onClick) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    Fmt.dateTime(inspection.submittedAt ?: inspection.checkInAt),
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.weight(1f),
                )
                SyncIcon(inspection.synced)
            }
            if (inspection.photos.isNotEmpty()) PhotoStrip(inspection.photos, onClick)
            TagRow(inspection, site)
            Text(
                inspection.notes.ifBlank { "No notes" },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** Up to three thumbnails; the last one shows "+N" when there are more photos. */
@Composable
private fun PhotoStrip(photos: List<String>, onClick: () -> Unit) {
    val shown = photos.take(3)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        shown.forEachIndexed { i, path ->
            Box(Modifier.weight(1f).aspectRatio(1f).clip(RoundedCornerShape(10.dp)).clickable(onClick = onClick)) {
                FileImage(path, Modifier.fillMaxSize(), maxSide = 360)
                val extra = photos.size - shown.size
                if (i == shown.lastIndex && extra > 0) {
                    Box(Modifier.fillMaxSize().background(Color(0x99000000)), contentAlignment = Alignment.Center) {
                        Text("+$extra", style = MaterialTheme.typography.titleMedium, color = Color.White)
                    }
                }
            }
        }
        // keep thumbnails the same size when there are fewer than three
        repeat(3 - shown.size) { Spacer(Modifier.weight(1f)) }
    }
}

@Composable
private fun TagRow(inspection: Inspection, site: Site) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        inspection.condition?.let { SmallChip(it.label, color = it.color()) }
        if (inspection.flagNeedsRepair) SmallChip("Needs repair", color = StatusColors.Repair)
        SmallChip(site.category.label)
    }
}

@Composable
private fun SyncIcon(synced: Boolean) {
    Icon(
        if (synced) Icons.Filled.CloudDone else Icons.Filled.CloudOff,
        if (synced) "Synced" else "Not synced yet",
        tint = if (synced) StatusColors.Active else StatusColors.Repair,
    )
}

/** Full preview of a past inspection: photo carousel, tags, category, notes and check-in facts. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InspectionPreviewSheet(inspection: Inspection, site: Site, onDismiss: () -> Unit) {
    var viewerIndex by remember { mutableStateOf<Int?>(null) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).navigationBarsPadding()
                .padding(start = 20.dp, end = 20.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Column {
                Text(site.name, style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    Fmt.dateTime(inspection.submittedAt ?: inspection.checkInAt),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            // Tags + category
            Column {
                SectionLabel("Tags")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
                    inspection.condition?.let { SmallChip("Condition: ${it.label}", color = it.color()) }
                    if (inspection.flagNeedsRepair) {
                        Row(
                            Modifier.clip(CircleShape).background(MaterialTheme.colorScheme.surfaceContainerHighest)
                                .padding(horizontal = 10.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(Icons.Filled.Flag, null, tint = StatusColors.Repair, modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Flagged: needs repair", style = MaterialTheme.typography.labelMedium, color = StatusColors.Repair)
                        }
                    }
                    SmallChip("Category: ${site.category.label}")
                    SmallChip(if (inspection.synced) "Synced" else "Pending sync", color = if (inspection.synced) StatusColors.Active else StatusColors.Repair)
                }
            }

            // Photos
            Column {
                SectionLabel("Photos (${inspection.photos.size})")
                if (inspection.photos.isEmpty()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.PhotoCamera, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.width(8.dp))
                        Text("No photos were taken.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                } else {
                    val pager = rememberPagerState { inspection.photos.size }
                    HorizontalPager(pager, pageSpacing = 10.dp, modifier = Modifier.fillMaxWidth()) { page ->
                        FileImage(
                            inspection.photos[page],
                            Modifier.fillMaxWidth().height(260.dp).clip(RoundedCornerShape(14.dp))
                                .clickable { viewerIndex = page },
                            maxSide = 1200,
                        )
                    }
                    if (inspection.photos.size > 1) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "${pager.currentPage + 1} / ${inspection.photos.size} · tap a photo to enlarge",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.align(Alignment.CenterHorizontally),
                        )
                    }
                }
            }

            // Notes
            Column {
                SectionLabel("Notes")
                FpCard {
                    Text(
                        inspection.notes.ifBlank { "No notes were added." },
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (inspection.notes.isBlank()) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                    )
                }
            }

            // Facts
            Column {
                SectionLabel("Check-in")
                FpCard {
                    Column {
                        KeyValueRow("Checked in", Fmt.dateTime(inspection.checkInAt))
                        inspection.submittedAt?.let { KeyValueRow("Submitted", Fmt.dateTime(it)) }
                        KeyValueRow("Location", Fmt.coordinates(inspection.checkInPoint))
                        KeyValueRow("GPS accuracy", Fmt.accuracy(inspection.checkInAccuracyM))
                    }
                }
            }
        }
    }

    viewerIndex?.let { start ->
        ImageGalleryViewer(
            images = inspection.photos.map {
                ViewerImage(it, title = site.name, subtitle = Fmt.dateTime(inspection.submittedAt ?: inspection.checkInAt))
            },
            startIndex = start,
            onClose = { viewerIndex = null },
        )
    }
}
