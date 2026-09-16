package com.alekpeed.lifeos.sync

import com.alekpeed.lifeos.storageAtomic
import com.alekpeed.lifeos.Storage
import com.alekpeed.lifeos.net.httpPostJson
import kotlinx.serialization.json.*

// Firebase Auth REST works on both native JVM targets. Provider tokens never sync.
object FirebaseAuth {
    private const val ACCESS = "__firebase_access"
    private const val REFRESH = "__firebase_refresh"
    private const val UID = "__firebase_uid"
    private const val EMAIL = "__firebase_email"
    fun isSignedIn() = !accessToken().isNullOrBlank()
    fun accessToken() = Storage.read(ACCESS)?.ifBlank { null }
    fun userId() = Storage.read(UID)?.ifBlank { null }
    fun email() = Storage.read(EMAIL)?.ifBlank { null }
    fun signOut() {
        com.alekpeed.lifeos.push.PushRegistration.removeFromServer()
        storageAtomic { for (key in listOf(ACCESS, REFRESH, UID, EMAIL)) Storage.write(key, "") }
        com.alekpeed.lifeos.push.PushRegistration.forget()
    }
    private val headers = mapOf("content-type" to "application/json")
    private suspend fun authenticate(method: String, email: String, password: String): Result<Unit> {
        val body = buildJsonObject { put("email", email); put("password", password); put("returnSecureToken", true) }.toString()
        val res = httpPostJson("https://identitytoolkit.googleapis.com/v1/accounts:$method?key=${FirebaseConfig.API_KEY}", headers, body)
        if (!res.ok) return Result.failure(RuntimeException(error(res.body)))
        return runCatching {
            val o = Json.parseToJsonElement(res.body).jsonObject
            val uid = o.getValue("localId").jsonPrimitive.content
            // Prevent one user's local library being silently uploaded into another account.
            storageAtomic {
            val owner = Storage.read("__firebase_owner")?.ifBlank { null } ?: Storage.read("__sb_uid")?.ifBlank { null }
            check(owner == null || owner == uid) { "This installation contains another account's data. Export it and clear local app data before switching accounts." }
            Storage.write(UID, uid); Storage.write(EMAIL, email)
            Storage.write(REFRESH, o.getValue("refreshToken").jsonPrimitive.content)
            Storage.write(ACCESS, o.getValue("idToken").jsonPrimitive.content)
            Storage.write("__firebase_owner", uid)
            }
            AutoSync.onForeground()
        }
    }
    suspend fun signIn(email: String, password: String) = authenticate("signInWithPassword", email, password)
    suspend fun signUp(email: String, password: String): Result<Boolean> = authenticate("signUp", email, password).map { true }
    suspend fun refresh(): Boolean {
        val token = Storage.read(REFRESH)?.ifBlank { null } ?: return false
        val body = buildJsonObject { put("grant_type", "refresh_token"); put("refresh_token", token) }.toString()
        val res = httpPostJson("https://securetoken.googleapis.com/v1/token?key=${FirebaseConfig.API_KEY}", headers, body)
        if (!res.ok) return false
        return runCatching {
            val o = Json.parseToJsonElement(res.body).jsonObject
            storageAtomic {
            check(Storage.read(REFRESH) == token) { "Session changed" }
            Storage.write(ACCESS, o.getValue("id_token").jsonPrimitive.content)
            Storage.write(REFRESH, o.getValue("refresh_token").jsonPrimitive.content)
            }
            true
        }.getOrDefault(false)
    }
    suspend fun resetPassword(email: String): Result<Unit> {
        val body = buildJsonObject { put("requestType", "PASSWORD_RESET"); put("email", email) }.toString()
        val res = httpPostJson("https://identitytoolkit.googleapis.com/v1/accounts:sendOobCode?key=${FirebaseConfig.API_KEY}", headers, body)
        return if (res.ok) Result.success(Unit) else Result.failure(RuntimeException("Couldn't request a password reset. Check your email and connection."))
    }
    private fun error(raw: String): String = runCatching {
        when (Json.parseToJsonElement(raw).jsonObject["error"]?.jsonObject?.get("message")?.jsonPrimitive?.content) {
            "INVALID_LOGIN_CREDENTIALS", "INVALID_PASSWORD", "EMAIL_NOT_FOUND" -> "Email or password is incorrect."
            "EMAIL_EXISTS" -> "An account with this email already exists."
            "TOO_MANY_ATTEMPTS_TRY_LATER" -> "Too many attempts. Try again later."
            else -> "Unable to sign in. Check your connection, email and password."
        }
    }.getOrDefault("Unable to sign in. Check your connection.")
}
