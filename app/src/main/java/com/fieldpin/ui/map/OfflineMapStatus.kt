package com.fieldpin.ui.map

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fieldpin.data.map.OfflineMapManager
import com.fieldpin.ui.theme.StatusColors
import org.koin.compose.koinInject
import java.util.Locale

fun formatMegabytes(bytes: Long): String = String.format(Locale.ENGLISH, "%.1f MB", bytes / (1024.0 * 1024.0))

/** One small pill on the map: saving progress, "offline map in use" (with the tile credit) or "saved". */
@Composable
fun OfflineMapStatus(basemap: Basemap, modifier: Modifier = Modifier) {
    val manager: OfflineMapManager = koinInject()
    val progress by manager.progress.collectAsStateWithLifecycle()
    val stats by manager.stats.collectAsStateWithLifecycle()
    val complete by manager.complete.collectAsStateWithLifecycle()
    val p = progress

    when {
        p != null -> StatusPill(
            Icons.Filled.CloudDownload, MaterialTheme.colorScheme.primary,
            "Saving offline map · ${(p.fraction * 100).toInt()}%", modifier, progress = p.fraction,
        )
        basemap.attribution != null -> StatusPill(
            if (basemap.offline) Icons.Filled.CloudOff else Icons.Filled.CloudDone,
            if (basemap.offline) StatusColors.Repair else MaterialTheme.colorScheme.primary,
            (if (basemap.offline) "Offline map" else "Saved map") + " · ${basemap.attribution}", modifier,
        )
        complete && stats.tiles > 0 -> StatusPill(
            Icons.Filled.CloudDone, StatusColors.Active,
            "Offline map saved · ${formatMegabytes(stats.bytes)}", modifier,
        )
    }
}

@Composable
private fun StatusPill(
    icon: ImageVector,
    tint: androidx.compose.ui.graphics.Color,
    text: String,
    modifier: Modifier,
    progress: Float? = null,
) {
    Surface(
        modifier = modifier,
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.94f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        shadowElevation = 4.dp,
    ) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, null, tint = tint, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(8.dp))
                Text(text, style = MaterialTheme.typography.labelMedium, maxLines = 1)
            }
            if (progress != null) {
                Spacer(Modifier.height(6.dp))
                LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth().height(3.dp))
            }
        }
    }
}
