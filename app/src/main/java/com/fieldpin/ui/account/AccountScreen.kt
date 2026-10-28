package com.fieldpin.ui.account

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import android.widget.Toast
import com.fieldpin.BuildConfig
import com.fieldpin.core.Fmt
import com.fieldpin.data.repo.SyncManager
import com.fieldpin.domain.model.Site
import com.fieldpin.domain.model.SiteCategory
import com.fieldpin.domain.model.SiteField
import com.fieldpin.domain.model.SiteStatus
import com.fieldpin.domain.model.SyncConflict
import com.fieldpin.domain.model.ThemeMode
import com.fieldpin.ui.common.fieldLabel
import com.fieldpin.ui.components.FpCard
import com.fieldpin.ui.components.KeyValueRow
import com.fieldpin.ui.components.PrimaryButton
import com.fieldpin.ui.components.SecondaryButton
import com.fieldpin.ui.components.SectionLabel
import com.fieldpin.ui.theme.StatusColors
import org.koin.compose.viewmodel.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountScreen(vm: AccountViewModel = koinViewModel()) {
    val context = LocalContext.current
    val session by vm.session.collectAsStateWithLifecycle()
    val theme by vm.themeMode.collectAsStateWithLifecycle()
    val online by vm.online.collectAsStateWithLifecycle()
    val pending by vm.pending.collectAsStateWithLifecycle()
    val syncState by vm.syncState.collectAsStateWithLifecycle()
    val lastSync by vm.lastSyncAt.collectAsStateWithLifecycle()
    val report by vm.lastReport.collectAsStateWithLifecycle()
    val sites by vm.sites.collectAsStateWithLifecycle()
    val simulated by vm.simulated.collectAsStateWithLifecycle()

    var confirmLogout by remember { mutableStateOf(false) }
    var picker by remember { mutableStateOf<Picker?>(null) }

    LazyColumn(
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        item { Text("Account", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(top = 12.dp, bottom = 4.dp)) }

        item {
            FpCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier.size(52.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            (session?.name?.firstOrNull() ?: 'F').uppercase(),
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                    }
                    Spacer(Modifier.width(14.dp))
                    Column {
                        Text(session?.name.orEmpty(), style = MaterialTheme.typography.titleMedium)
                        Text(session?.email.orEmpty(), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }

        item {
            Column {
                SectionLabel("Sync")
                FpCard {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(10.dp).clip(CircleShape).background(if (online) StatusColors.Active else StatusColors.Repair))
                            Spacer(Modifier.width(8.dp))
                            Text(if (online) "Online" else "Offline (airplane mode or no signal)", style = MaterialTheme.typography.titleSmall)
                        }
                        Spacer(Modifier.height(8.dp))
                        KeyValueRow("Waiting to sync", if (pending == 0) "Nothing" else "$pending change(s)")
                        KeyValueRow("Last sync", lastSync?.let { Fmt.dateTime(it) } ?: "Never")
                        KeyValueRow("Server", "mock · ${BuildConfig.API_BASE_URL}")
                        (syncState as? SyncManager.State.Failed)?.let {
                            Text(it.message, style = MaterialTheme.typography.bodySmall, color = StatusColors.Repair)
                            Spacer(Modifier.height(6.dp))
                        }
                        Spacer(Modifier.height(8.dp))
                        PrimaryButton(
                            "Sync now",
                            onClick = vm::syncNow,
                            loading = syncState is SyncManager.State.Syncing,
                            icon = Icons.Filled.Sync,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        val last = report
                        if (last != null && last.conflicts.isNotEmpty()) {
                            Spacer(Modifier.height(14.dp))
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                            Spacer(Modifier.height(10.dp))
                            Text("Conflicts resolved in the last sync", style = MaterialTheme.typography.titleSmall)
                            last.conflicts.forEach {
                                Text(
                                    "• " + describe(it),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(top = 4.dp),
                                )
                            }
                        }
                    }
                }
            }
        }

        item {
            Column {
                SectionLabel("Appearance")
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    ThemeMode.entries.forEachIndexed { index, mode ->
                        SegmentedButton(
                            selected = theme == mode,
                            onClick = { vm.setTheme(mode) },
                            shape = SegmentedButtonDefaults.itemShape(index, ThemeMode.entries.size),
                            label = { Text(mode.label) },
                        )
                    }
                }
            }
        }

        item {
            Column {
                SectionLabel("Privacy")
                FpCard {
                    Text(
                        "Location is used only while FieldPin is open, to show you on the map and to check you in. " +
                            "It is never read in the background. Photos and inspections stay on this device until you sync.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        if (BuildConfig.DEBUG) {
            item {
                Column {
                    SectionLabel("Developer tools (debug build)")
                    FpCard {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text(
                                if (simulated != null) "Simulated location is ON. The app ignores real GPS."
                                else "Simulated location is off (real GPS).",
                                style = MaterialTheme.typography.bodySmall,
                                color = if (simulated != null) StatusColors.Repair else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            SecondaryButton("Teleport me inside a site", { picker = Picker.Teleport }, Modifier.fillMaxWidth())
                            SecondaryButton("Use real GPS again", vm::stopSimulation, Modifier.fillMaxWidth(), enabled = simulated != null)
                            SecondaryButton("Simulate another phone editing a site", { picker = Picker.OtherPhone }, Modifier.fillMaxWidth())
                            Text(
                                "Tip for the conflict demo: go offline, change a site's status, then pick the same site here, go online and sync.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }

        item {
            SecondaryButton("Log out", { confirmLogout = true }, Modifier.fillMaxWidth(), icon = Icons.AutoMirrored.Filled.Logout)
            Text(
                "FieldPin ${BuildConfig.VERSION_NAME}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
            )
        }
    }

    if (confirmLogout) {
        AlertDialog(
            onDismissRequest = { confirmLogout = false },
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            title = { Text("Log out?") },
            text = {
                Text(
                    if (pending > 0) "$pending change(s) have not been synced yet. Logging out deletes all data on this device, including them. Sync first if you can."
                    else "All sites, inspections and photos on this device will be deleted. Everything is already synced."
                )
            },
            confirmButton = {
                TextButton(onClick = { confirmLogout = false; vm.logout() }) { Text("Log out", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                Row {
                    if (pending > 0 && online) TextButton(onClick = { confirmLogout = false; vm.syncNow() }) { Text("Sync first") }
                    TextButton(onClick = { confirmLogout = false }) { Text("Cancel") }
                }
            },
        )
    }

    picker?.let { mode ->
        SitePicker(
            title = if (mode == Picker.Teleport) "Teleport inside…" else "Edit on the server…",
            sites = sites,
            onDismiss = { picker = null },
            onPick = { site ->
                picker = null
                if (mode == Picker.Teleport) {
                    vm.simulateInside(site)
                    Toast.makeText(context, "You are now inside ${site.name}", Toast.LENGTH_SHORT).show()
                } else {
                    vm.simulateOtherPhoneEdit(site.id) { Toast.makeText(context, it, Toast.LENGTH_LONG).show() }
                }
            },
        )
    }
}

private enum class Picker { Teleport, OtherPhone }

@Composable
private fun SitePicker(title: String, sites: List<Site>, onDismiss: () -> Unit, onPick: (Site) -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        title = { Text(title) },
        text = {
            LazyColumn(Modifier.height(360.dp)) {
                items(sites, key = { it.id }) { site ->
                    TextButton(onClick = { onPick(site) }, modifier = Modifier.fillMaxWidth()) {
                        Text(site.name, modifier = Modifier.fillMaxWidth())
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
}

private fun describe(c: SyncConflict): String {
    val field = fieldLabel(c.field)
    val yours = display(c.field, c.yours)
    val theirs = display(c.field, c.theirs)
    return if (c.yoursWon) "${c.siteName}: your $field “$yours” replaced “$theirs” from another phone."
    else "${c.siteName}: kept “$theirs” from another phone; your $field “$yours” was older."
}

private fun display(field: String, raw: String?): String = when (field) {
    SiteField.STATUS -> SiteStatus.parse(raw).label
    SiteField.CATEGORY -> SiteCategory.parse(raw).label
    SiteField.LAST_INSPECTION -> raw?.toLongOrNull()?.let { Fmt.dateTime(it) } ?: "—"
    else -> raw?.take(40) ?: "—"
}
