package com.fieldpin.ui.sites

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fieldpin.core.Fmt
import com.fieldpin.data.repo.SyncManager
import com.fieldpin.domain.model.Site
import com.fieldpin.domain.model.SiteStatus
import com.fieldpin.ui.components.CategoryTile
import com.fieldpin.ui.components.EmptyState
import com.fieldpin.ui.components.FpCard
import com.fieldpin.ui.components.PendingBadge
import com.fieldpin.ui.components.SkeletonCard
import com.fieldpin.ui.components.StatCard
import com.fieldpin.ui.components.StatusDot
import com.fieldpin.ui.components.StatusPill
import com.fieldpin.ui.theme.StatusColors
import org.koin.compose.viewmodel.koinViewModel
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

private fun greeting(): String = when (Calendar.getInstance().get(Calendar.HOUR_OF_DAY)) {
    in 5..11 -> "Good morning"
    in 12..16 -> "Good afternoon"
    else -> "Good evening"
}

@Composable
fun SitesScreen(
    onOpenSite: (String) -> Unit,
    onMapSite: () -> Unit,
    vm: SitesViewModel = koinViewModel(),
) {
    val sites by vm.sites.collectAsStateWithLifecycle()
    val summary by vm.summary.collectAsStateWithLifecycle()
    val query by vm.query.collectAsStateWithLifecycle()
    val filter by vm.filter.collectAsStateWithLifecycle()
    val syncState by vm.syncState.collectAsStateWithLifecycle()
    val online by vm.isOnline.collectAsStateWithLifecycle()

    Box(Modifier.fillMaxSize()) {
        LazyColumn(contentPadding = PaddingValues(bottom = 110.dp), modifier = Modifier.fillMaxSize()) {
            item {
                Row(
                    Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 16.dp, bottom = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            if (vm.firstName.isEmpty()) greeting() else "${greeting()},",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            vm.firstName.ifEmpty { "Field officer" },
                            style = MaterialTheme.typography.headlineMedium,
                        )
                        Text(
                            SimpleDateFormat("EEEE, d MMM", Locale.ENGLISH).format(Date()) + if (online) "" else "  ·  Offline",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (online) MaterialTheme.colorScheme.onSurfaceVariant else StatusColors.Repair,
                        )
                    }
                    IconButton(onClick = vm::syncNow, enabled = syncState !is SyncManager.State.Syncing) {
                        if (syncState is SyncManager.State.Syncing) {
                            CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(
                                Icons.Filled.Sync,
                                contentDescription = "Sync now",
                                tint = if (summary.pending > 0) StatusColors.Repair else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
            item {
                Row(Modifier.padding(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    StatCard("Sites", summary.total.toString(), Icons.Filled.Place, MaterialTheme.colorScheme.primary, Modifier.weight(1f))
                    StatCard("Need repair", summary.needsRepair.toString(), Icons.Filled.Build, StatusColors.Repair, Modifier.weight(1f))
                    StatCard(
                        "Pending sync", summary.pending.toString(), Icons.Filled.Sync,
                        if (summary.pending > 0) StatusColors.Repair else StatusColors.Active, Modifier.weight(1f),
                    )
                }
            }
            item {
                OutlinedTextField(
                    value = query,
                    onValueChange = { vm.query.value = it },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 14.dp),
                    placeholder = { Text("Search sites") },
                    leadingIcon = { Icon(Icons.Filled.Search, null) },
                    trailingIcon = {
                        if (query.isNotEmpty()) {
                            IconButton(onClick = { vm.query.value = "" }) { Icon(Icons.Filled.Close, "Clear search") }
                        }
                    },
                    singleLine = true,
                    shape = MaterialTheme.shapes.extraLarge,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                    ),
                )
            }
            item {
                Row(
                    Modifier.horizontalScroll(rememberScrollState()).padding(start = 20.dp, end = 20.dp, bottom = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    StatusFilterChip("All", null, filter == null) { vm.filter.value = null }
                    SiteStatus.entries.forEach { status ->
                        StatusFilterChip(status.filterLabel, status, filter == status) { vm.filter.value = status }
                    }
                }
            }

            val list = sites
            when {
                list == null -> items(4) { SkeletonCard(Modifier.padding(horizontal = 20.dp, vertical = 5.dp)) }
                list.isEmpty() -> item {
                    EmptyState(
                        Icons.Filled.SearchOff,
                        "No sites found",
                        if (query.isNotEmpty() || filter != null) "Try a different search or filter." else "Tap “Map site” to add your first one.",
                    )
                }
                else -> items(list, key = { it.id }) { site ->
                    SiteCard(site, onClick = { onOpenSite(site.id) }, Modifier.padding(horizontal = 20.dp, vertical = 5.dp))
                }
            }
        }

        ExtendedFloatingActionButton(
            onClick = onMapSite,
            icon = { Icon(Icons.Filled.Add, null) },
            text = { Text("Map site") },
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            shape = MaterialTheme.shapes.extraLarge,
            modifier = Modifier.align(Alignment.BottomEnd).padding(20.dp),
        )
    }
}

@Composable
private fun StatusFilterChip(label: String, status: SiteStatus?, selected: Boolean, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label) },
        leadingIcon = status?.let { { StatusDot(it, 8.dp) } },
        shape = MaterialTheme.shapes.extraLarge,
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
        ),
    )
}

@Composable
fun SiteCard(site: Site, onClick: () -> Unit, modifier: Modifier = Modifier) {
    FpCard(modifier = modifier.fillMaxWidth(), onClick = onClick, contentPadding = 14.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CategoryTile(site.category, site.status)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        site.name,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(Modifier.width(8.dp))
                    StatusPill(site.status)
                }
                Spacer(Modifier.height(2.dp))
                Text(
                    "${site.category.label} · ${Fmt.area(site.areaSqm)}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        site.lastInspectionAt?.let { "Inspected ${Fmt.date(it)}" } ?: "Not inspected yet",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                    if (site.hasPendingSync) PendingBadge()
                }
            }
        }
    }
}
