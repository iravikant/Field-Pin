package com.fieldpin.ui.map

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Spacer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Key
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.fieldpin.ui.theme.StatusColors

/** Round map button; [active] tints it with the brand colour (e.g. satellite or 3D on). */
@Composable
fun MapFab(icon: ImageVector, description: String, onClick: () -> Unit, active: Boolean = false) {
    SmallFloatingActionButton(
        onClick = onClick,
        containerColor = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = if (active) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
    ) { Icon(icon, description) }
}

/** Shown when MAPS_API_KEY is not set, so a blank map is explained instead of mysterious. */
@Composable
fun MapsKeyBanner(modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        border = BorderStroke(1.dp, StatusColors.Repair.copy(alpha = 0.7f)),
    ) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Key, null, tint = StatusColors.Repair, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(10.dp))
            Text(
                "Google Maps key missing. Add MAPS_API_KEY to local.properties and rebuild to load map tiles.",
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}
