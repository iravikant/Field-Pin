package com.fieldpin.ui.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.fieldpin.core.Fmt
import com.fieldpin.domain.model.DocumentKind
import com.fieldpin.domain.model.SiteDocument
import com.fieldpin.ui.components.FileImage
import com.fieldpin.ui.components.FpCard
import com.fieldpin.ui.preview.ViewerImage
import com.fieldpin.ui.preview.formatBytes
import com.fieldpin.ui.preview.icon
import com.fieldpin.ui.preview.label

/** Section title with a count and an optional action on the right ("Add"). */
@Composable
fun SectionHeader(title: String, count: Int? = null, actionLabel: String? = null, onAction: (() -> Unit)? = null) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            if (count != null) "${title.uppercase()} · $count" else title.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        if (actionLabel != null && onAction != null) {
            TextButton(onClick = onAction) {
                Icon(Icons.Filled.Add, null, Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text(actionLabel)
            }
        }
    }
}

/** Every photo taken on this site's inspections, newest first. Tap a thumbnail to open the viewer. */
@Composable
fun SitePhotoStrip(photos: List<ViewerImage>, onOpen: (Int) -> Unit) {
    if (photos.isEmpty()) {
        FpCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.PhotoLibrary, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.width(12.dp))
                Text(
                    "Photos from inspections show up here.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        return
    }
    val shown = photos.take(MAX_THUMBS)
    val extra = photos.size - shown.size
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        itemsIndexed(shown, key = { _, item -> item.path }) { index, item ->
            Box(
                Modifier
                    .size(104.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .clickable { onOpen(index) },
            ) {
                FileImage(item.path, Modifier.fillMaxSize(), maxSide = 360)
                if (index == shown.lastIndex && extra > 0) {
                    Box(Modifier.fillMaxSize().background(Color(0x99000000)), contentAlignment = Alignment.Center) {
                        Text("+$extra", style = MaterialTheme.typography.titleMedium, color = Color.White)
                    }
                }
            }
        }
    }
}

private const val MAX_THUMBS = 12

/** A document: thumbnail for images, kind icon otherwise, name, "PDF · 1.2 MB · 12 Oct". */
@Composable
fun DocumentRow(document: SiteDocument, onClick: () -> Unit) {
    FpCard(onClick = onClick, contentPadding = 12.dp, modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                if (document.kind == DocumentKind.IMAGE) {
                    FileImage(document.path, Modifier.fillMaxSize(), maxSide = 200)
                } else {
                    Icon(document.kind.icon(), null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(document.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    "${document.kind.label()} · ${formatBytes(document.sizeBytes)} · ${Fmt.date(document.addedAt)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun EmptyDocuments(onAdd: () -> Unit) {
    FpCard(onClick = onAdd) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.AttachFile, null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(12.dp))
            Column {
                Text("Attach a file", style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(2.dp))
                Text(
                    "Permits, drawings, reports or extra photos. Kept on this device.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
