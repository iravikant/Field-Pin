package com.fieldpin.ui.main

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import com.fieldpin.data.repo.SyncManager
import com.fieldpin.ui.account.AccountScreen
import com.fieldpin.ui.map.MapScreen
import com.fieldpin.ui.sites.SitesScreen
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

private enum class Tab(val label: String, val icon: ImageVector, val selectedIcon: ImageVector) {
    Sites("Sites", Icons.Outlined.Place, Icons.Filled.Place),
    Map("Map", Icons.Outlined.Map, Icons.Filled.Map),
    Account("Account", Icons.Outlined.Person, Icons.Filled.Person),
}

@Composable
fun MainScreen(
    onOpenSite: (String) -> Unit,
    onMapSite: () -> Unit,
    onOpenInspection: (String) -> Unit,
) {
    var tab by rememberSaveable { mutableStateOf(Tab.Sites) }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val show: (String) -> Unit = { message -> scope.launch { snackbar.showSnackbar(message) } }
    val sync: SyncManager = koinInject()

    LaunchedEffect(Unit) { sync.events.collect { show(it) } }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainerLow) {
                Tab.entries.forEach { item ->
                    NavigationBarItem(
                        selected = tab == item,
                        onClick = { tab = item },
                        icon = { Icon(if (tab == item) item.selectedIcon else item.icon, item.label) },
                        label = { Text(item.label) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            selectedTextColor = MaterialTheme.colorScheme.primary,
                            indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                        ),
                    )
                }
            }
        },
    ) { inner ->
        Box(Modifier.fillMaxSize().padding(inner)) {
            when (tab) {
                Tab.Sites -> SitesScreen(onOpenSite = onOpenSite, onMapSite = onMapSite)
                Tab.Map -> MapScreen(onOpenSite = onOpenSite, onOpenInspection = onOpenInspection, onMessage = show)
                Tab.Account -> AccountScreen()
            }
        }
    }
}
