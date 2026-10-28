package com.fieldpin.ui.camera

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.util.Size
import android.view.OrientationEventListener
import android.view.Surface as ViewSurface
import android.widget.Toast
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.FlipCameraAndroid
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fieldpin.core.Fmt
import com.fieldpin.ui.components.PermissionKind
import com.fieldpin.ui.components.PermissionPrompt
import com.fieldpin.ui.components.rememberPermissionController
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

@Composable
fun CameraScreen(
    inspectionId: String,
    onDone: () -> Unit,
    vm: CameraViewModel = koinViewModel(parameters = { parametersOf(inspectionId) }),
) {
    val context = LocalContext.current
    val permission = rememberPermissionController(PermissionKind.Camera)

    // Location is only *read* here (it was granted at check-in); we never prompt from the camera.
    LaunchedEffect(Unit) {
        vm.live.granted.value =
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        if (permission.isGranted) {
            CameraContent(vm, onDone)
        } else {
            Column(
                Modifier.fillMaxSize().statusBarsPadding().padding(24.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                PermissionPrompt(permission)
                Spacer(Modifier.size(12.dp))
                TextButton(onClick = onDone) { Text("Not now") }
            }
        }
    }
}

@Composable
private fun CameraContent(vm: CameraViewModel, onDone: () -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val inspection by vm.inspection.collectAsStateWithLifecycle()
    val fix by vm.live.fix.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()

    var lens by remember { mutableIntStateOf(CameraSelector.LENS_FACING_BACK) }
    var flashOn by remember { mutableStateOf(false) }
    var camera by remember { mutableStateOf<Camera?>(null) }

    val previewView = remember { PreviewView(context).apply { scaleType = PreviewView.ScaleType.FIT_CENTER } }
    val resolution = remember {
        ResolutionSelector.Builder()
            .setAspectRatioStrategy(AspectRatioStrategy.RATIO_4_3_FALLBACK_AUTO_STRATEGY)
            .setResolutionStrategy(ResolutionStrategy(Size(3264, 2448), ResolutionStrategy.FALLBACK_RULE_CLOSEST_LOWER_THEN_HIGHER))
            .build()
    }
    val imageCapture = remember {
        ImageCapture.Builder()
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
            .setResolutionSelector(resolution)
            .build()
    }

    LaunchedEffect(lens) {
        try {
            val provider = context.awaitCameraProvider()
            val preview = Preview.Builder()
                .setResolutionSelector(
                    ResolutionSelector.Builder().setAspectRatioStrategy(AspectRatioStrategy.RATIO_4_3_FALLBACK_AUTO_STRATEGY).build()
                )
                .build()
                .also { it.surfaceProvider = previewView.surfaceProvider }
            val selector = CameraSelector.Builder().requireLensFacing(lens).build()
            provider.unbindAll()
            camera = provider.bindToLifecycle(lifecycleOwner, selector, preview, imageCapture)
        } catch (e: Exception) {
            Toast.makeText(context, "Camera unavailable: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }
    LaunchedEffect(flashOn) {
        imageCapture.flashMode = if (flashOn) ImageCapture.FLASH_MODE_ON else ImageCapture.FLASH_MODE_OFF
    }
    // Keep the saved JPEG upright however the phone is held (the activity itself is portrait-locked).
    DisposableEffect(Unit) {
        val listener = object : OrientationEventListener(context) {
            override fun onOrientationChanged(orientation: Int) {
                if (orientation == OrientationEventListener.ORIENTATION_UNKNOWN) return
                imageCapture.targetRotation = when (orientation) {
                    in 45..134 -> ViewSurface.ROTATION_270
                    in 135..224 -> ViewSurface.ROTATION_180
                    in 225..314 -> ViewSurface.ROTATION_90
                    else -> ViewSurface.ROTATION_0
                }
            }
        }
        listener.enable()
        onDispose { listener.disable() }
    }

    val now by produceState(System.currentTimeMillis()) {
        while (true) {
            delay(1_000)
            value = System.currentTimeMillis()
        }
    }
    val hasFlash = camera?.cameraInfo?.hasFlashUnit() == true
    val photos = inspection?.photos.orEmpty()

    fun capture() {
        if (busy) return
        val file = vm.newPhotoFile()
        imageCapture.takePicture(
            ImageCapture.OutputFileOptions.Builder(file).build(),
            ContextCompat.getMainExecutor(context),
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                    vm.onCaptured(file) { Toast.makeText(context, it, Toast.LENGTH_LONG).show() }
                }

                override fun onError(exception: ImageCaptureException) {
                    file.delete()
                    Toast.makeText(context, "Capture failed: ${exception.message}", Toast.LENGTH_LONG).show()
                }
            },
        )
    }

    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onDone) { Icon(Icons.Filled.Close, "Close camera", tint = Color.White) }
            Column(Modifier.weight(1f)) {
                Text(inspection?.siteName ?: "Camera", color = Color.White, style = MaterialTheme.typography.titleMedium, maxLines = 1)
                Text("${photos.size} photo(s) saved", color = Color.White.copy(alpha = 0.7f), style = MaterialTheme.typography.bodySmall)
            }
            TextButton(onClick = onDone) { Text("Done", color = MaterialTheme.colorScheme.primary) }
        }

        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            Box(Modifier.fillMaxWidth().aspectRatio(3f / 4f).clip(MaterialTheme.shapes.medium)) {
                AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())
                // Live preview of what will be burned into the saved photo.
                Column(
                    Modifier
                        .align(Alignment.BottomStart)
                        .padding(12.dp)
                        .clip(MaterialTheme.shapes.small)
                        .background(Color.Black.copy(alpha = 0.6f))
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                ) {
                    Text(inspection?.siteName.orEmpty(), color = Color.White, style = MaterialTheme.typography.labelLarge, maxLines = 1)
                    val f = fix
                    val point = f?.point ?: inspection?.checkInPoint
                    if (point != null) {
                        Text(
                            Fmt.coordinates(point) + "  " + Fmt.accuracy(f?.accuracyM ?: inspection?.checkInAccuracyM ?: 0f),
                            color = Color.White.copy(alpha = 0.9f),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    Text(Fmt.stampDateTime(now), color = Color.White.copy(alpha = 0.9f), style = MaterialTheme.typography.bodySmall)
                }
                if (busy) {
                    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.5f)), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }

        Row(
            Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 20.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ControlButton(onClick = { lens = if (lens == CameraSelector.LENS_FACING_BACK) CameraSelector.LENS_FACING_FRONT else CameraSelector.LENS_FACING_BACK }) {
                Icon(Icons.Filled.FlipCameraAndroid, "Flip camera", tint = Color.White)
            }
            Box(
                Modifier
                    .size(78.dp)
                    .border(4.dp, Color.White, CircleShape)
                    .padding(6.dp)
                    .clip(CircleShape)
                    .background(if (busy) Color.Gray else Color.White)
                    .clickable(enabled = !busy, onClick = ::capture),
            )
            ControlButton(onClick = { flashOn = !flashOn }, enabled = hasFlash) {
                Icon(
                    if (flashOn) Icons.Filled.FlashOn else Icons.Filled.FlashOff,
                    "Toggle flash",
                    tint = if (hasFlash) Color.White else Color.Gray,
                )
            }
        }

    }
}

@Composable
private fun ControlButton(onClick: () -> Unit, enabled: Boolean = true, content: @Composable () -> Unit) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = CircleShape,
        color = Color.White.copy(alpha = 0.14f),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.2f)),
        modifier = Modifier.size(52.dp),
    ) {
        Box(contentAlignment = Alignment.Center) { content() }
    }
}

private suspend fun Context.awaitCameraProvider(): ProcessCameraProvider = suspendCancellableCoroutine { cont ->
    val future = ProcessCameraProvider.getInstance(this)
    future.addListener(
        {
            try {
                cont.resume(future.get())
            } catch (e: Exception) {
                cont.resumeWithException(e)
            }
        },
        ContextCompat.getMainExecutor(this),
    )
}
