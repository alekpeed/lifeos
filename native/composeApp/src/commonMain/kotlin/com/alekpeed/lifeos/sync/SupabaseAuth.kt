package com.alekpeed.lifeos.sync

import com.alekpeed.lifeos.Storage
import com.alekpeed.lifeos.net.httpPostJson
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

// Email + password auth against Supabase GoTrue over plain HTTPS — no OAuth
// redirect, so it works cleanly in the native app. Tokens are stored locally under
// reserved __keys (never tracked as syncable data). Cross-device sync works by
// signing into the same account on each device: same auth.uid() → same rows.
object SupabaseAuth {
    private val json = Json { ignoreUnknownKeys = true }

    private const val K_ACCESS = "__sb_access"
    private const val K_REFRESH = "__sb_refresh"
    private const val K_UID = "__sb_uid"
    private const val K_EMAIL = "__sb_email"

    fun isSignedIn(): Boolean = !Storage.read(K_ACCESS).isNullOrBlank()
    fun email(): String? = Storage.read(K_EMAIL)?.ifBlank { null }
    fun userId(): String? = Storage.read(K_UID)?.ifBlank { null }
    fun accessToken(): String? = Storage.read(K_ACCESS)?.ifBlank { null }

    fun signOut() {
        Storage.write(K_ACCESS, ""); Storage.write(K_REFRESH, "")
        Storage.write(K_UID, ""); Storage.write(K_EMAIL, "")
    }

    private fun baseHeaders() = mapOf(
        "apikey" to SupabaseConfig.ANON_KEY,
        "content-type" to "application/json",
    )

    // Create an account. If the project requires email confirmation, no session is
    // returned yet — the caller is told to confirm, then sign in.
    suspend fun signUp(email: String, password: String): Result<Boolean> {
        val body = buildJsonObject { put("email", email); put("password", password) }.toString()
        val res = httpPostJson("${SupabaseConfig.URL}/auth/v1/signup", baseHeaders(), body)
        if (!res.ok) return Result.failure(RuntimeException(authError(res.body, res.status)))
        // A session in the response means confirmation is off → we're signed in.
        val session = runCatching { storeSession(res.body) }.getOrDefault(false)
        return Result.success(session)
    }

    suspend fun signIn(email: String, password: String): Result<Unit> {
        val body = buildJsonObject { put("email", email); put("password", password) }.toString()
        val res = httpPostJson("${SupabaseConfig.URL}/auth/v1/token?grant_type=password", baseHeaders(), body)
        if (!res.ok) return Result.failure(RuntimeException(authError(res.body, res.status)))
        return if (storeSession(res.body)) Result.success(Unit)
        else Result.failure(RuntimeException("Signed in but no token returned"))
    }

    // Refresh the access token from the stored refresh token; false if it failed
    // (caller should treat as signed-out).
    suspend fun refresh(): Boolean {
        val refresh = Storage.read(K_REFRESH)?.ifBlank { null } ?: return false
        val body = buildJsonObject { put("refresh_token", refresh) }.toString()
        val res = httpPostJson("${SupabaseConfig.URL}/auth/v1/token?grant_type=refresh_token", baseHeaders(), body)
        if (!res.ok) return false
        return runCatching { storeSession(res.body) }.getOrDefault(false)
    }

    private fun storeSession(bodyText: String): Boolean {
        val root = json.parseToJsonElement(bodyText).jsonObject
        val access = root["access_token"]?.jsonPrimitive?.content ?: return false
        val refresh = root["refresh_token"]?.jsonPrimitive?.content
        val user = root["user"]?.jsonObject
        val uid = user?.get("id")?.jsonPrimitive?.content
        val mail = user?.get("email")?.jsonPrimitive?.content
        Storage.write(K_ACCESS, access)
        if (refresh != null) Storage.write(K_REFRESH, refresh)
        if (uid != null) Storage.write(K_UID, uid)
        if (mail != null) Storage.write(K_EMAIL, mail)
        return true
    }

    private fun authError(bodyText: String, status: Int): String {
        val detail = runCatching {
            val o = json.parseToJsonElement(bodyText).jsonObject
            o["error_description"]?.jsonPrimitive?.content
                ?: o["msg"]?.jsonPrimitive?.content
                ?: o["error"]?.jsonPrimitive?.content
        }.getOrNull()
        return when {
            detail != null -> detail
            status == -1 -> "No network connection."
            else -> "Sign-in failed (HTTP $status)."
        }
    }
}
