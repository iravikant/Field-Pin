package com.fieldpin.ui.sites

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fieldpin.data.repo.SiteRepository
import com.fieldpin.data.repo.SyncManager
import com.fieldpin.domain.model.Site
import com.fieldpin.domain.model.SiteStatus
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

data class SitesSummary(val total: Int = 0, val needsRepair: Int = 0, val pending: Int = 0)

@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
class SitesViewModel(repo: SiteRepository, private val sync: SyncManager) : ViewModel() {
    val query = MutableStateFlow("")
    val filter = MutableStateFlow<SiteStatus?>(null)

    /** Null until the first database emission (shows a spinner instead of a flash of "empty"). */
    val sites: StateFlow<List<Site>?> = combine(
        query.debounce { if (it.isEmpty()) 0L else 150L },
        filter,
    ) { q, f -> q to f }
        .flatMapLatest { (q, f) -> repo.observeSites(q, f) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val summary: StateFlow<SitesSummary> = repo.observeSites("", null)
        .map { all ->
            SitesSummary(
                total = all.size,
                needsRepair = all.count { it.status == SiteStatus.NEEDS_REPAIR },
                pending = all.count { it.hasPendingSync },
            )
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SitesSummary())

    val syncState = sync.state
    val isOnline = sync.isOnline

    fun syncNow() = sync.syncNow()
}
