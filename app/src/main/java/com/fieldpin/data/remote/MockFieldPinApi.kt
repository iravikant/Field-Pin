package com.fieldpin.data.remote

import com.fieldpin.core.NetworkMonitor
import kotlinx.coroutines.delay
import java.security.MessageDigest
import java.util.UUID

/**
 * In-process stand-in for the backend. It behaves like a remote server in the ways that matter:
 * it fails with [OfflineException] when the device has no internet (so airplane mode really
 * blocks sync), it has latency, and it resolves conflicts on the "server" side.
 *
 * Login is validated locally against salted-free SHA-256 digests from the seed file, so the demo
 * account also works offline. [baseUrl] comes from BuildConfig and identifies the target backend.
 */
class MockFieldPinApi(
    val baseUrl: String,
    private val store: MockServerStore,
    private val network: NetworkMonitor,
) : FieldPinApi {

    private fun requireOnline() {
        if (!network.isOnline.value) throw OfflineException()
    }

    private fun sha256(text: String): String =
        MessageDigest.getInstance("SHA-256").digest(text.toByteArray()).joinToString("") { "%02x".format(it) }

    override suspend fun login(email: String, password: String): AuthResponse {
        delay(LATENCY_MS)
        val user = store.snapshot().users.firstOrNull {
            it.email.equals(email.trim(), ignoreCase = true) && it.passwordSha256 == sha256(password)
        } ?: throw AuthException("Incorrect email or password")
        return AuthResponse(token = "mock-${UUID.randomUUID()}", name = user.name, email = user.email)
    }

    override suspend fun fetchSites(token: String): List<SiteDto> {
        requireOnline()
        delay(LATENCY_MS)
        return store.snapshot().sites
    }

    override suspend fun pushSite(token: String, change: SiteChangeDto): SitePushResponse {
        requireOnline()
        delay(LATENCY_MS)
        return store.update { state ->
            val existing = state.sites.firstOrNull { it.id == change.siteId }
            val result = ServerMerge.apply(existing, change)
            val sites = if (existing == null) state.sites + result.site
            else state.sites.map { if (it.id == change.siteId) result.site else it }
            state.copy(sites = sites) to SitePushResponse(result.site, result.conflicts)
        }
    }

    override suspend fun pushInspection(token: String, inspection: InspectionDto) {
        requireOnline()
        delay(LATENCY_MS)
        store.update { state ->
            if (state.inspections.any { it.id == inspection.id }) state to Unit
            else state.copy(inspections = state.inspections + inspection) to Unit
        }
    }

    private companion object {
        const val LATENCY_MS = 350L
    }
}
