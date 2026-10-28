package com.fieldpin.data.repo

import com.fieldpin.core.NetworkMonitor
import com.fieldpin.data.local.AppPrefs
import com.fieldpin.data.local.InspectionDao
import com.fieldpin.data.local.InspectionEntity
import com.fieldpin.data.local.SessionStore
import com.fieldpin.data.local.SiteDao
import com.fieldpin.data.local.decodePhotos
import com.fieldpin.data.remote.FieldPinApi
import com.fieldpin.data.remote.InspectionDto
import com.fieldpin.data.remote.OfflineException
import com.fieldpin.domain.model.SyncConflict
import com.fieldpin.domain.model.SyncReport
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import java.io.File

/**
 * Push-then-pull sync.
 *
 *  1. push every locally edited / created site (oldest data first so inspections never reference
 *     a site the server has not seen),
 *  2. push submitted inspections (append-only and idempotent by id, so they never conflict),
 *  3. pull all sites and rebase them under anything still pending.
 *
 * Triggered by the "Sync now" buttons and, while the app process is alive, automatically when the
 * network returns or new changes are queued. Any failure leaves the local data untouched.
 */
@OptIn(FlowPreview::class)
class SyncManager(
    private val api: FieldPinApi,
    private val siteDao: SiteDao,
    private val inspectionDao: InspectionDao,
    private val siteRepository: SiteRepository,
    private val session: SessionStore,
    private val network: NetworkMonitor,
    private val prefs: AppPrefs,
    private val scope: CoroutineScope,
) {
    sealed interface State {
        data object Idle : State
        data object Syncing : State
        data class Failed(val message: String) : State
    }

    private val _state = MutableStateFlow<State>(State.Idle)
    val state: StateFlow<State> = _state.asStateFlow()

    private val _lastReport = MutableStateFlow<SyncReport?>(null)
    val lastReport: StateFlow<SyncReport?> = _lastReport.asStateFlow()

    private val _events = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val events: SharedFlow<String> = _events.asSharedFlow()

    val pendingCount: Flow<Int> = siteDao.observePendingCount()
    val isOnline: StateFlow<Boolean> = network.isOnline
    val lastSyncAt: StateFlow<Long?> = prefs.lastSyncAt

    private val mutex = Mutex()

    /** Starts the automatic trigger. Call once from Application.onCreate. */
    fun start() {
        scope.launch {
            combine(network.isOnline, pendingCount, session.session) { online, pending, user ->
                if (online && user != null) pending else 0
            }
                .distinctUntilChanged()
                .filter { it > 0 }
                .debounce(AUTO_SYNC_DELAY_MS)
                .collect { sync(auto = true) }
        }
    }

    fun syncNow() {
        scope.launch { sync(auto = false) }
    }

    private suspend fun sync(auto: Boolean) {
        if (!mutex.tryLock()) return // a sync is already running
        try {
            val token = session.session.value?.token ?: return
            _state.value = State.Syncing

            var pushedSites = 0
            var pushedInspections = 0
            val conflicts = mutableListOf<SyncConflict>()

            for (site in siteDao.getDirty()) {
                val response = api.pushSite(token, site.toChange())
                siteRepository.applyPushResult(site, response.site)
                conflicts += response.conflicts.map {
                    SyncConflict(site.id, site.name, it.field, it.yours, it.theirs, it.yoursWon)
                }
                pushedSites++
            }
            for (inspection in inspectionDao.getUnsynced()) {
                api.pushInspection(token, inspection.toDto())
                inspectionDao.markSynced(inspection.id)
                pushedInspections++
            }
            val remote = api.fetchSites(token)
            siteRepository.mergePulled(remote)

            val now = System.currentTimeMillis()
            _lastReport.value = SyncReport(pushedSites, pushedInspections, remote.size, conflicts, now)
            prefs.setLastSync(now)
            _state.value = State.Idle

            if (!auto || pushedSites + pushedInspections > 0) {
                val summary = buildString {
                    append("Synced")
                    if (pushedSites + pushedInspections > 0) {
                        append(": $pushedSites site change(s), $pushedInspections inspection(s) uploaded")
                    } else {
                        append(" — everything is up to date")
                    }
                    if (conflicts.isNotEmpty()) append(". ${conflicts.size} conflict(s) resolved")
                }
                _events.tryEmit(summary)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: OfflineException) {
            _state.value = State.Failed("Offline — changes are safe on this device and will sync later")
            if (!auto) _events.tryEmit("You're offline. Changes are saved and will sync when you're back online.")
        } catch (e: Exception) {
            _state.value = State.Failed(e.message ?: "Sync failed")
            if (!auto) _events.tryEmit("Sync failed: ${e.message ?: "unknown error"}")
        } finally {
            mutex.unlock()
        }
    }

    private fun InspectionEntity.toDto() = InspectionDto(
        id = id,
        siteId = siteId,
        checkInAt = checkInAt,
        checkInLat = checkInLat,
        checkInLng = checkInLng,
        checkInAccuracyM = checkInAccuracyM,
        condition = condition,
        notes = notes,
        photoFileNames = decodePhotos(photosJson).map { File(it).name },
        submittedAt = submittedAt ?: checkInAt,
    )

    private companion object {
        const val AUTO_SYNC_DELAY_MS = 1_500L
    }
}
