package com.fieldpin.ui.account

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fieldpin.data.local.AppPrefs
import com.fieldpin.data.map.OfflineMapManager
import com.fieldpin.data.location.LocationTracker
import com.fieldpin.data.remote.MockServerStore
import com.fieldpin.data.repo.AuthRepository
import com.fieldpin.data.repo.SiteRepository
import com.fieldpin.data.repo.SyncManager
import com.fieldpin.domain.gis.GeoMath
import com.fieldpin.domain.model.Site
import com.fieldpin.domain.model.ThemeMode
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class AccountViewModel(
    private val auth: AuthRepository,
    private val sync: SyncManager,
    private val prefs: AppPrefs,
    private val tracker: LocationTracker,
    repo: SiteRepository,
    private val server: MockServerStore,
    private val offlineMap: OfflineMapManager,
) : ViewModel() {
    val session = auth.session
    val themeMode = prefs.themeMode
    val online = sync.isOnline
    val syncState = sync.state
    val lastSyncAt = sync.lastSyncAt
    val lastReport = sync.lastReport
    val simulated = tracker.simulated

    // offline map pack
    val offlineStats = offlineMap.stats
    val offlineProgress = offlineMap.progress
    val offlineComplete = offlineMap.complete
    val offlineError = offlineMap.error

    fun downloadOfflineMap() = offlineMap.downloadNow()

    fun deleteOfflineMap() = offlineMap.clear()

    val pending: StateFlow<Int> = sync.pendingCount
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    val sites: StateFlow<List<Site>> = repo.observeSites("", null)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun setTheme(mode: ThemeMode) = prefs.setThemeMode(mode)

    fun syncNow() = sync.syncNow()

    fun logout() {
        viewModelScope.launch {
            tracker.simulate(null)
            auth.logout()
        }
    }

    // ---- developer tools (only shown in debug builds) ----

    fun simulateInside(site: Site) = tracker.simulate(GeoMath.interiorPoint(site.boundary))

    fun stopSimulation() = tracker.simulate(null)

    /** Plays "another phone" editing the site on the server while this device has local edits. */
    fun simulateOtherPhoneEdit(siteId: String, onResult: (String) -> Unit) {
        viewModelScope.launch {
            val message = server.simulateOtherPhoneEdit(siteId, System.currentTimeMillis())
            onResult(message ?: "Site not found on the server")
        }
    }
}
