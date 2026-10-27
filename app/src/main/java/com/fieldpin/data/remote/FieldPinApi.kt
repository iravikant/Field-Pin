package com.fieldpin.data.remote

import java.io.IOException

class AuthException(message: String) : Exception(message)

/** Thrown when the device has no usable internet connection. */
class OfflineException : IOException("No internet connection")

/**
 * The contract of the backend. [MockFieldPinApi] implements it in-process; a real client would
 * implement it with Retrofit/Ktor against `BuildConfig.API_BASE_URL`.
 */
interface FieldPinApi {
    suspend fun login(email: String, password: String): AuthResponse

    suspend fun fetchSites(token: String): List<SiteDto>

    suspend fun pushSite(token: String, change: SiteChangeDto): SitePushResponse

    /** Idempotent: pushing the same inspection id twice is harmless. */
    suspend fun pushInspection(token: String, inspection: InspectionDto)
}
