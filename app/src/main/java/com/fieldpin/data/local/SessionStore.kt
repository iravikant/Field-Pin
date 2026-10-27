package com.fieldpin.data.local

import android.content.Context
import com.fieldpin.domain.model.ThemeMode
import com.fieldpin.domain.model.UserSession
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The signed-in session. Persisted in app-private SharedPreferences so it survives restarts;
 * [clear] is part of logout. (A production app would keep the token in the Keystore.)
 */
class SessionStore(context: Context) {
    private val prefs = context.getSharedPreferences("fieldpin_session", Context.MODE_PRIVATE)

    private val _session = MutableStateFlow(read())
    val session: StateFlow<UserSession?> = _session.asStateFlow()

    private fun read(): UserSession? {
        val token = prefs.getString(KEY_TOKEN, null) ?: return null
        val email = prefs.getString(KEY_EMAIL, null) ?: return null
        return UserSession(email = email, name = prefs.getString(KEY_NAME, "") ?: "", token = token)
    }

    fun save(session: UserSession) {
        prefs.edit()
            .putString(KEY_TOKEN, session.token)
            .putString(KEY_EMAIL, session.email)
            .putString(KEY_NAME, session.name)
            .apply()
        _session.value = session
    }

    fun clear() {
        prefs.edit().clear().apply()
        _session.value = null
    }

    private companion object {
        const val KEY_TOKEN = "token"
        const val KEY_EMAIL = "email"
        const val KEY_NAME = "name"
    }
}

/** Non-sensitive preferences that survive logout (theme, which permissions were asked, last sync). */
class AppPrefs(context: Context) {
    private val prefs = context.getSharedPreferences("fieldpin_prefs", Context.MODE_PRIVATE)

    private val _themeMode = MutableStateFlow(
        ThemeMode.entries.firstOrNull { it.name == prefs.getString(KEY_THEME, null) } ?: ThemeMode.DARK
    )
    val themeMode: StateFlow<ThemeMode> = _themeMode.asStateFlow()

    fun setThemeMode(mode: ThemeMode) {
        prefs.edit().putString(KEY_THEME, mode.name).apply()
        _themeMode.value = mode
    }

    private val _lastSyncAt = MutableStateFlow(prefs.getLong(KEY_LAST_SYNC, 0L).takeIf { it > 0 })
    val lastSyncAt: StateFlow<Long?> = _lastSyncAt.asStateFlow()

    fun setLastSync(ms: Long?) {
        prefs.edit().putLong(KEY_LAST_SYNC, ms ?: 0L).apply()
        _lastSyncAt.value = ms
    }

    /** Needed to tell "never asked" from "permanently denied": both report no rationale. */
    fun wasPermissionAsked(key: String): Boolean = prefs.getBoolean("asked_$key", false)

    fun markPermissionAsked(key: String) {
        prefs.edit().putBoolean("asked_$key", true).apply()
    }

    private companion object {
        const val KEY_THEME = "theme"
        const val KEY_LAST_SYNC = "last_sync"
    }
}
