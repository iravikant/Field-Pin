package com.fieldpin.data.remote

import android.content.Context
import com.fieldpin.core.AppJson
import com.fieldpin.domain.model.SiteField
import com.fieldpin.domain.model.SiteStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Persists the mock server's database as a JSON file in app storage, seeded from
 * `assets/mock_server_seed.json` on first use. It stands in for a real backend, so it is
 * deliberately *not* wiped on logout (a server does not forget you signed out).
 */
class MockServerStore(private val context: Context) {
    private val file = File(context.filesDir, "mock_server.json")
    private val mutex = Mutex()
    private var cached: ServerState? = null

    private fun load(): ServerState {
        cached?.let { return it }
        val text = if (file.exists()) {
            file.readText()
        } else {
            context.assets.open(SEED_ASSET).bufferedReader().use { it.readText() }
        }
        return AppJson.json.decodeFromString(ServerState.serializer(), text).also { cached = it }
    }

    private fun save(state: ServerState) {
        val tmp = File(file.parentFile, "mock_server.json.tmp")
        tmp.writeText(AppJson.json.encodeToString(ServerState.serializer(), state))
        if (!tmp.renameTo(file)) {
            file.writeText(tmp.readText())
            tmp.delete()
        }
        cached = state
    }

    /** Runs [block] atomically against the current state; persists the new state it returns. */
    suspend fun <T> update(block: (ServerState) -> Pair<ServerState, T>): T = mutex.withLock {
        withContext(Dispatchers.IO) {
            val current = load()
            val (next, result) = block(current)
            if (next !== current) save(next)
            result
        }
    }

    suspend fun snapshot(): ServerState = update { it to it }

    /**
     * Debug helper: acts as "another phone" that edited a site's status while this device was
     * offline. Returns a human-readable description, or null if the site is unknown.
     */
    suspend fun simulateOtherPhoneEdit(siteId: String, now: Long): String? = update { state ->
        val site = state.sites.firstOrNull { it.id == siteId } ?: return@update state to null
        val next = when (SiteStatus.parse(site.status)) {
            SiteStatus.ACTIVE -> SiteStatus.CLOSED
            SiteStatus.NEEDS_REPAIR -> SiteStatus.ACTIVE
            SiteStatus.CLOSED -> SiteStatus.NEEDS_REPAIR
        }
        val change = SiteChangeDto(
            siteId = site.id,
            baseVersion = site.version,
            isCreate = false,
            editedAt = now,
            fields = setOf(SiteField.STATUS),
            status = next.name,
        )
        val merged = ServerMerge.apply(site, change).site
        state.copy(sites = state.sites.map { if (it.id == siteId) merged else it }) to
            "${site.name}: status set to ${next.label} on the server"
    }

    private companion object {
        const val SEED_ASSET = "mock_server_seed.json"
    }
}
