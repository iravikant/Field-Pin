package com.fieldpin.data.repo

import com.fieldpin.data.local.AppPrefs
import com.fieldpin.data.local.FieldPinDatabase
import com.fieldpin.data.local.SessionStore
import com.fieldpin.data.remote.AuthException
import com.fieldpin.data.remote.FieldPinApi
import com.fieldpin.domain.model.UserSession
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext

class AuthRepository(
    private val api: FieldPinApi,
    private val sessionStore: SessionStore,
    private val siteRepository: SiteRepository,
    private val db: FieldPinDatabase,
    private val photos: PhotoStorage,
    private val prefs: AppPrefs,
    private val documents: DocumentStorage,
) {
    val session: StateFlow<UserSession?> = sessionStore.session

    suspend fun login(email: String, password: String): Result<UserSession> = try {
        val auth = api.login(email, password)
        val session = UserSession(email = auth.email, name = auth.name, token = auth.token)
        sessionStore.save(session)
        siteRepository.bootstrap(session.token)
        Result.success(session)
    } catch (e: AuthException) {
        Result.failure(e)
    } catch (e: Exception) {
        // Sign-in succeeded but the first data load failed: stay signed in with whatever we have.
        sessionStore.session.value?.let { Result.success(it) } ?: Result.failure(e)
    }

    /** Logout: drop the session and every piece of local data (database, photos, documents, sync state). */
    suspend fun logout() = withContext(Dispatchers.IO) {
        db.clearAllTables()
        photos.deleteAll()
        documents.deleteAll()
        prefs.setLastSync(null)
        sessionStore.clear()
    }
}
