package com.fieldpin.domain

import kotlinx.serialization.Serializable


enum class Condition(val label: String) {
    GOOD("Good"),
    FAIR("Fair"),
    POOR("Poor");

    companion object {
        fun parse(value: String?): Condition? = entries.firstOrNull { it.name == value }
    }
}

data class UserSession(val email: String, val name: String, val token: String)

enum class ThemeMode(val label: String) { SYSTEM("System"), DARK("Dark"), LIGHT("Light") }

data class SyncConflict(
    val siteId: String,
    val siteName: String,
    val field: String,
    val yours: String?,
    val theirs: String?,
    val yoursWon: Boolean,
)


