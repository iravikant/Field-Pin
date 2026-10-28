package com.fieldpin.ui.sites

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.FloatingActionButton
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fieldpin.core.Fmt
import com.fieldpin.data.repo.SyncManager
import com.fieldpin.domain.model.Site
import com.fieldpin.domain.model.SiteStatus
import com.fieldpin.ui.components.EmptyState
import com.fieldpin.ui.components.FpCard
import com.fieldpin.ui.components.MetricTile
import com.fieldpin.ui.components.PendingBadge
import com.fieldpin.ui.components.ScreenHeader
import com.fieldpin.ui.components.StatusDot
import com.fieldpin.ui.components.StatusPill
import com.fieldpin.ui.theme.StatusColors
import org.koin.compose.viewmodel.koinViewModel

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
        LazyColumn(
            contentPadding = PaddingValues(bottom = 110.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            item {
                ScreenHeader(
                    title = "Sites",
                    subtitle = if (online) "${summary.total} sites" else "${summary.total} sites · Offline",
                    actions = {
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
                    },
                )
            }
            item {
                Row(Modifier.padding(horizontal = 20.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    MetricTile("Total", summary.total.toString(), Modifier.weight(1f))
                    MetricTile("Need repair", summary.needsRepair.toString(), Modifier.weight(1f), valueColor = StatusColors.Repair)
                    MetricTile(
                        "Pending sync", summary.pending.toString(), Modifier.weight(1f),
                        valueColor = if (summary.pending > 0) StatusColors.Repair else MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
            item {
                OutlinedTextField(
                    value = query,
                    onValueChange = { vm.query.value = it },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
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
                    Modifier.horizontalScroll(rememberScrollState()).padding(start = 20.dp, end = 20.dp, bottom = 8.dp),
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
                list == null -> item {
                    Box(Modifier.fillMaxWidth().padding(48.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                }
                list.isEmpty() -> item {
                    EmptyState(
                        Icons.Filled.SearchOff,
                        "No sites found",
                        if (query.isNotEmpty() || filter != null) "Try a different search or filter." else "Tap + to map your first site.",
                    )
                }
                else -> items(list, key = { it.id }) { site ->
                    SiteCard(site, onClick = { onOpenSite(site.id) }, Modifier.padding(horizontal = 20.dp, vertical = 5.dp))
                }
            }
        }

        FloatingActionButton(
            onClick = onMapSite,
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            shape = MaterialTheme.shapes.large,
            modifier = Modifier.align(Alignment.BottomEnd).padding(20.dp),
        ) { Icon(Icons.Filled.Add, "Map a new site") }
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
    FpCard(modifier = modifier.fillMaxWidth(), onClick = onClick) {
        androidx.compose.foundation.layout.Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    site.name,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(8.dp))
                StatusPill(site.status)
            }
            Spacer(Modifier.padding(top = 4.dp))
            Text(
                "${site.category.label} · ${Fmt.area(site.areaSqm)}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.padding(top = 6.dp))
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
