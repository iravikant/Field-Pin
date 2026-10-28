package com.fieldpin.ui.inspection

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fieldpin.core.Fmt
import com.fieldpin.domain.model.Condition
import com.fieldpin.domain.model.Inspection
import com.fieldpin.ui.components.FileImage
import com.fieldpin.ui.components.FpTextField
import com.fieldpin.ui.components.PrimaryButton
import com.fieldpin.ui.components.SectionLabel
import com.fieldpin.ui.theme.StatusColors
import com.fieldpin.ui.theme.color
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

@Composable
fun InspectionScreen(
    inspectionId: String,
    onBack: () -> Unit,
    onOpenCamera: (String) -> Unit,
    vm: InspectionViewModel = koinViewModel(parameters = { parametersOf(inspectionId) }),
) {
    val context = LocalContext.current
    val inspection by vm.inspection.collectAsStateWithLifecycle()
    val notes by vm.notes.collectAsStateWithLifecycle()
    var preview by remember { mutableStateOf<String?>(null) }
    var confirmDiscard by remember { mutableStateOf(false) }
    val current: Inspection? = inspection

    Column(Modifier.fillMaxSize().statusBarsPadding().imePadding()) {
        Row(Modifier.padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back (draft is saved)") }
            Text(current?.siteName ?: "Inspection", style = MaterialTheme.typography.titleLarge, maxLines = 1, modifier = Modifier.weight(1f))
        }

        if (current == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("Inspection not found") }
            return@Column
        }

        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            // Check-in record
            Surface(
                shape = MaterialTheme.shapes.medium,
                color = StatusColors.Active.copy(alpha = 0.14f),
                border = BorderStroke(1.dp, StatusColors.Active.copy(alpha = 0.6f)),
            ) {
                Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.CheckCircle, null, tint = StatusColors.Active)
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text("Checked in at ${Fmt.time(current.checkInAt)}", style = MaterialTheme.typography.titleSmall)
                        Text(
                            "${Fmt.coordinates(current.checkInPoint)} · ${Fmt.accuracy(current.checkInAccuracyM)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            if (!current.isDraft) {
                Text(
                    "This inspection was submitted on ${Fmt.dateTime(current.submittedAt ?: current.checkInAt)} and can't be edited.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            // Condition
            Column {
                SectionLabel("Condition")
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Condition.entries.forEach { condition ->
                        val selected = current.condition == condition
                        Surface(
                            onClick = { vm.setCondition(condition) },
                            enabled = current.isDraft,
                            shape = MaterialTheme.shapes.medium,
                            color = if (selected) condition.color().copy(alpha = 0.2f) else MaterialTheme.colorScheme.surfaceContainer,
                            border = BorderStroke(if (selected) 2.dp else 1.dp, if (selected) condition.color() else MaterialTheme.colorScheme.outline),
                            modifier = Modifier.weight(1f).height(52.dp),
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(condition.label, style = MaterialTheme.typography.labelLarge)
                            }
                        }
                    }
                }
                if (current.condition == Condition.POOR && current.isDraft) {
                    Spacer(Modifier.height(10.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Flag site as “Needs repair”", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                        Switch(checked = current.flagNeedsRepair, onCheckedChange = vm::setFlagNeedsRepair)
                    }
                }
            }

            // Photos
            Column {
                SectionLabel("Photos (${current.photos.size})")
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    current.photos.forEach { path ->
                        FileImage(
                            path,
                            Modifier.size(96.dp).clip(MaterialTheme.shapes.medium).clickable { preview = path },
                        )
                    }
                    if (current.isDraft) {
                        Surface(
                            onClick = { onOpenCamera(current.id) },
                            shape = MaterialTheme.shapes.medium,
                            color = MaterialTheme.colorScheme.surfaceContainer,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary),
                            modifier = Modifier.size(96.dp),
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                                Icon(Icons.Filled.AddAPhoto, "Take photo", tint = MaterialTheme.colorScheme.primary)
                                Spacer(Modifier.height(4.dp))
                                Text("Add", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                }
                if (current.photos.isEmpty()) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Photos are stamped with site, coordinates, GPS accuracy and time.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            // Notes
            if (current.isDraft) {
                FpTextField(notes, { vm.notes.value = it }, "Notes", singleLine = false, minLines = 3, placeholder = "e.g. Fence broken on east side")
            } else if (current.notes.isNotBlank()) {
                Column {
                    SectionLabel("Notes")
                    Text(current.notes)
                }
            }
            Spacer(Modifier.height(4.dp))
        }

        if (current.isDraft) {
            Column(Modifier.navigationBarsPadding().padding(horizontal = 20.dp, vertical = 12.dp)) {
                PrimaryButton(
                    "Submit inspection",
                    onClick = {
                        vm.submit {
                            Toast.makeText(context, "Inspection saved. It will sync when you're online.", Toast.LENGTH_SHORT).show()
                            onBack()
                        }
                    },
                    enabled = current.condition != null,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (current.condition == null) {
                    Text(
                        "Choose a condition to submit.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 6.dp).align(Alignment.CenterHorizontally),
                    )
                }
                TextButton(onClick = { confirmDiscard = true }, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                    Text("Discard draft", color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }

    preview?.let { path ->
        Dialog(onDismissRequest = { preview = null }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
            Box(Modifier.fillMaxSize().background(Color.Black)) {
                FileImage(path, Modifier.fillMaxSize(), maxSide = 1600, contentScale = ContentScale.Fit)
                Row(Modifier.statusBarsPadding().padding(12.dp).align(Alignment.TopEnd)) {
                    if (current?.isDraft == true) {
                        Surface(shape = CircleShape, color = Color.Black.copy(alpha = 0.6f)) {
                            IconButton(onClick = { vm.removePhoto(path); preview = null }) {
                                Icon(Icons.Filled.Delete, "Delete photo", tint = Color.White)
                            }
                        }
                        Spacer(Modifier.width(8.dp))
                    }
                    Surface(shape = CircleShape, color = Color.Black.copy(alpha = 0.6f)) {
                        IconButton(onClick = { preview = null }) { Icon(Icons.Filled.Close, "Close", tint = Color.White) }
                    }
                }
            }
        }
    }

    if (confirmDiscard) {
        AlertDialog(
            onDismissRequest = { confirmDiscard = false },
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            title = { Text("Discard this inspection?") },
            text = { Text("The check-in, photos and notes of this draft will be deleted from the device.") },
            confirmButton = {
                TextButton(onClick = { confirmDiscard = false; vm.discard(onBack) }) {
                    Text("Discard", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { confirmDiscard = false }) { Text("Keep") } },
        )
    }
}
