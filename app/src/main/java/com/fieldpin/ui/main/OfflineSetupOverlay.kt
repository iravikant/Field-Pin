package com.fieldpin.ui.main

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fieldpin.data.local.AppPrefs
import com.fieldpin.data.map.OfflineMapManager
import com.fieldpin.ui.theme.Green400
import com.fieldpin.ui.theme.Teal400
import org.koin.compose.koinInject

/**
 * Shown once, the first time the app runs online: the map around the sites is being saved on
 * the device. Disappears by itself when done, or the officer can carry on in the background.
 */
@Composable
fun OfflineSetupOverlay() {
    val manager: OfflineMapManager = koinInject()
    val prefs: AppPrefs = koinInject()
    val progress by manager.progress.collectAsStateWithLifecycle()
    val complete by manager.complete.collectAsStateWithLifecycle()
    var dismissed by remember { mutableStateOf(prefs.offlineSetupSeen) }

    LaunchedEffect(complete) { if (complete) prefs.offlineSetupSeen = true }

    val p = progress
    if (dismissed || p == null) return

    Box(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background.copy(alpha = 0.92f))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {},
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            modifier = Modifier.padding(28.dp).fillMaxWidth(),
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surfaceContainer,
            shadowElevation = 12.dp,
        ) {
            Column(Modifier.padding(28.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    Modifier.size(72.dp).background(Brush.linearGradient(listOf(Teal400, Green400)), MaterialTheme.shapes.large),
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.Filled.CloudDownload, null, tint = Color.Black, modifier = Modifier.size(36.dp)) }
                Spacer(Modifier.height(20.dp))
                Text("Saving your map", style = MaterialTheme.typography.headlineSmall)
                Spacer(Modifier.height(8.dp))
                Text(
                    "We're storing the map around your sites on this device, so you can keep working with no signal.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(24.dp))
                LinearProgressIndicator(progress = { p.fraction }, modifier = Modifier.fillMaxWidth().height(6.dp))
                Spacer(Modifier.height(10.dp))
                Text(
                    "${(p.fraction * 100).toInt()}%  ·  ${p.done} of ${p.total} map tiles",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(12.dp))
                TextButton(onClick = {
                    prefs.offlineSetupSeen = true
                    dismissed = true
                }) { Text("Continue in the background") }
            }
        }
    }
}
