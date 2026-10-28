package com.fieldpin.ui.components

import android.Manifest
import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.location.LocationManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.LocationOff
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.fieldpin.data.local.AppPrefs
import com.fieldpin.data.location.LocationTracker
import org.koin.compose.koinInject

enum class PermissionStatus { Granted, NotRequested, Denied, PermanentlyDenied }

enum class PermissionKind(
    val key: String,
    val permissions: List<String>,
    val required: String,
    val icon: ImageVector,
    val title: String,
    val why: String,
    val allowLabel: String,
) {
    Location(
        key = "location",
        permissions = listOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION),
        required = Manifest.permission.ACCESS_FINE_LOCATION,
        icon = Icons.Filled.LocationOn,
        title = "Precise location",
        why = "FieldPin uses your precise location while the app is open to show you on the map and to " +
            "confirm you are inside a site before you check in. It never tracks you in the background.",
        allowLabel = "Allow location",
    ),
    Camera(
        key = "camera",
        permissions = listOf(Manifest.permission.CAMERA),
        required = Manifest.permission.CAMERA,
        icon = Icons.Filled.CameraAlt,
        title = "Camera",
        why = "The camera is used to take inspection photos. Each photo is stamped with the site name, " +
            "coordinates, GPS accuracy and time as evidence.",
        allowLabel = "Allow camera",
    ),
}

class PermissionController(
    val kind: PermissionKind,
    val status: PermissionStatus,
    private val launch: () -> Unit,
    private val settings: () -> Unit,
) {
    val isGranted: Boolean get() = status == PermissionStatus.Granted

    /** Asks the system, or sends the user to Settings once Android will no longer show the dialog. */
    fun requestOrOpenSettings() {
        if (status == PermissionStatus.PermanentlyDenied) settings() else launch()
    }
}

tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

fun Context.openAppSettings() {
    startActivity(
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    )
}

/**
 * Runtime-permission state that distinguishes "never asked", "denied" and "denied with Don't ask
 * again". The request is only launched when the user triggers a feature that needs it.
 */
@Composable
fun rememberPermissionController(kind: PermissionKind): PermissionController {
    val context = LocalContext.current
    val prefs: AppPrefs = koinInject()

    fun compute(): PermissionStatus {
        if (ContextCompat.checkSelfPermission(context, kind.required) == PackageManager.PERMISSION_GRANTED) {
            return PermissionStatus.Granted
        }
        if (!prefs.wasPermissionAsked(kind.key)) return PermissionStatus.NotRequested
        val activity = context.findActivity()
        val canAskAgain = activity != null && ActivityCompat.shouldShowRequestPermissionRationale(activity, kind.required)
        return if (canAskAgain) PermissionStatus.Denied else PermissionStatus.PermanentlyDenied
    }

    var status by remember { mutableStateOf(compute()) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        prefs.markPermissionAsked(kind.key)
        status = compute()
    }
    // The user may come back from system Settings with the permission changed.
    LifecycleResumeEffect(kind) {
        status = compute()
        onPauseOrDispose { }
    }
    return remember(status) {
        PermissionController(
            kind = kind,
            status = status,
            launch = { launcher.launch(kind.permissions.toTypedArray()) },
            settings = { context.openAppSettings() },
        )
    }
}

/** Live "is the device's location switch on" state; updates when the user toggles GPS. */
@Composable
fun rememberGpsEnabled(): State<Boolean> {
    val context = LocalContext.current
    val tracker: LocationTracker = koinInject()
    val enabled = remember { mutableStateOf(tracker.isLocationEnabled()) }
    DisposableEffect(context) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context?, intent: Intent?) {
                enabled.value = tracker.isLocationEnabled()
            }
        }
        ContextCompat.registerReceiver(
            context,
            receiver,
            IntentFilter(LocationManager.PROVIDERS_CHANGED_ACTION),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        enabled.value = tracker.isLocationEnabled()
        onDispose { context.unregisterReceiver(receiver) }
    }
    return enabled
}

/** Rationale + action. Full card for blocking screens, a slim banner when [compact]. */
@Composable
fun PermissionPrompt(controller: PermissionController, modifier: Modifier = Modifier, compact: Boolean = false) {
    if (controller.isGranted) return
    val kind = controller.kind
    val (message, action) = when (controller.status) {
        PermissionStatus.PermanentlyDenied ->
            "${kind.title} is turned off for FieldPin. Open Settings → Permissions to enable it." to "Open Settings"
        PermissionStatus.Denied ->
            "Without ${kind.title.lowercase()} this feature can't work. ${kind.why}" to "Try again"
        else -> kind.why to kind.allowLabel
    }
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
    ) {
        if (compact) {
            Row(Modifier.padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(kind.icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(10.dp))
                Text(
                    if (controller.status == PermissionStatus.NotRequested) "Enable ${kind.title.lowercase()} to see where you are"
                    else message.substringBefore(". ").trimEnd('.') + ".",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = controller::requestOrOpenSettings) { Text(action) }
            }
        } else {
            Column(Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.size(56.dp)) {
                    Icon(kind.icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(14.dp))
                }
                Spacer(Modifier.height(14.dp))
                Text(kind.title, style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(6.dp))
                Text(message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
                Spacer(Modifier.height(16.dp))
                Button(onClick = controller::requestOrOpenSettings) { Text(action) }
            }
        }
    }
}

/** Shown when the permission is fine but the device's location switch is off. */
@Composable
fun GpsOffBanner(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.35f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.5f)),
    ) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.LocationOff, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(10.dp))
            Text("Location is switched off on this device.", style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
            TextButton(onClick = {
                context.startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }) { Text("Turn on") }
        }
    }
}
