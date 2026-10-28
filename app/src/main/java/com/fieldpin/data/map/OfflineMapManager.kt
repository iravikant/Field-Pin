package com.fieldpin.data.map

import com.fieldpin.core.NetworkMonitor
import com.fieldpin.data.local.AppPrefs
import com.fieldpin.data.local.SessionStore
import com.fieldpin.data.repo.SiteRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withContext
import java.io.IOException
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicInteger

data class DownloadProgress(val done: Int, val total: Int) {
    val fraction: Float get() = if (total == 0) 0f else done.toFloat() / total
}

/**
 * Saves the offline map pack on the device. It runs on its own the first time the app is online
 * with sites available (first run after sign-in), and again after a failed or interrupted attempt
 * the next time the network returns. Already-saved tiles are skipped, so resuming is cheap.
 */
class OfflineMapManager(
    val cache: TileCache,
    val fetcher: TileFetcher,
    private val sites: SiteRepository,
    private val network: NetworkMonitor,
    private val session: SessionStore,
    private val prefs: AppPrefs,
    private val scope: CoroutineScope,
) {
    private val _stats = MutableStateFlow(CacheStats())
    val stats: StateFlow<CacheStats> = _stats.asStateFlow()

    private val _progress = MutableStateFlow<DownloadProgress?>(null)
    val progress: StateFlow<DownloadProgress?> = _progress.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    val complete: StateFlow<Boolean> = prefs.offlineMapComplete

    private val mutex = Mutex()
    private var job: Job? = null

    init {
        scope.launch(Dispatchers.IO) { _stats.value = cache.stats() }
    }

    /** Starts the automatic first-run download. Call once from Application.onCreate. */
    fun start() {
        scope.launch {
            combine(
                network.isOnline,
                session.session.map { it != null },
                sites.observeSites("", null).map { it.size },
                complete,
            ) { online, loggedIn, siteCount, done -> online && loggedIn && siteCount > 0 && !done }
                .distinctUntilChanged()
                .filter { it }
                .collect { download() }
        }
    }

    /** Manual "download / update" from the Account screen. */
    fun downloadNow() {
        scope.launch { download() }
    }

    fun cancel() {
        job?.cancel()
    }

    fun clear() {
        cancel()
        scope.launch(Dispatchers.IO) {
            cache.clear()
            prefs.setOfflineMapComplete(false)
            _stats.value = CacheStats()
            _error.value = null
        }
    }

    private suspend fun download() {
        if (!mutex.tryLock()) return
        try {
            job = scope.launch { runDownload() }
            job?.join()
        } finally {
            mutex.unlock()
        }
    }

    private suspend fun runDownload() {
        _error.value = null
        try {
            val plan = OfflineAreaPlanner.plan(sites.observeSites("", null).first().map { it.boundary })
            if (plan.isEmpty()) return
            val missing = withContext(Dispatchers.IO) { plan.filterNot { cache.has(it) } }
            val done = AtomicInteger(plan.size - missing.size)
            val failures = AtomicInteger(0)
            _progress.value = DownloadProgress(done.get(), plan.size)

            val queue = ConcurrentLinkedQueue(missing)
            coroutineScope {
                repeat(WORKERS) {
                    launch(Dispatchers.IO) {
                        while (true) {
                            ensureActive()
                            val key = queue.poll() ?: break
                            try {
                                fetcher.fetch(key)?.let { cache.write(key, it) } ?: failures.incrementAndGet()
                            } catch (e: IOException) {
                                failures.incrementAndGet()
                                queue.clear() // connection lost: stop and resume later
                                _error.value = "Connection lost while saving the map. It will resume when you're online."
                            }
                            _progress.value = DownloadProgress(done.incrementAndGet(), plan.size)
                            delay(POLITE_DELAY_MS)
                        }
                    }
                }
            }
            withContext(Dispatchers.IO) { _stats.value = cache.stats() }
            val allPresent = withContext(Dispatchers.IO) { plan.all { cache.has(it) } }
            prefs.setOfflineMapComplete(allPresent && failures.get() == 0)
            if (!allPresent && _error.value == null) _error.value = "Some map tiles could not be saved."
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            _error.value = e.message ?: "Could not save the offline map"
        } finally {
            _progress.value = null
            withContext(kotlinx.coroutines.NonCancellable + Dispatchers.IO) { _stats.value = cache.stats() }
        }
    }

    private companion object {
        const val WORKERS = 3
        const val POLITE_DELAY_MS = 40L
    }
}
