package com.alekpeed.lifeos.push

import com.alekpeed.lifeos.Storage
import kotlinx.coroutines.launch
import com.alekpeed.lifeos.net.httpPostJson
import com.alekpeed.lifeos.platform.Native
import com.alekpeed.lifeos.sync.FirebaseAuth
import com.alekpeed.lifeos.sync.FirebaseConfig
import kotlinx.datetime.Clock
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

// Registers this installation with Firebase so Cloud Scheduler can send due reminders.
// Device tokens remain local and are refreshed after sign-in or rotation.

private const val LAST_TOKEN_KEY = "__firebase_fcm_token"

object PushRegistration {

    // Called at app open. Silent about every failure: not signed in, no push transport
    // on this platform, the network down — none of these are things to interrupt
    // somebody opening their notes with.
    suspend fun registerIfNeeded() {
        if (!Native.supportsNotifications) return
        if (!FirebaseAuth.isSignedIn()) return
        val token = currentToken() ?: return
        if (token == Storage.read(LAST_TOKEN_KEY)) return
        if (upload(token)) Storage.write(LAST_TOKEN_KEY, token)
    }

    // Forget the device on sign-out, so the next account on this phone re-registers
    // rather than inheriting a row that still points at the previous one.
    fun forget() {
        Storage.write(LAST_TOKEN_KEY, "")
    }

    fun removeFromServer() {
        val token = Storage.read(LAST_TOKEN_KEY)?.ifBlank { null } ?: return
        val access = FirebaseAuth.accessToken() ?: return
        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Default).launch {
            com.alekpeed.lifeos.net.httpPostJson("${FirebaseConfig.URL}/devices/remove",
                mapOf("Authorization" to "Bearer $access", "content-type" to "application/json"),
                buildJsonObject { put("token", token) }.toString())
        }
    }
    private suspend fun currentToken(): String? = kotlinx.coroutines.suspendCancellableCoroutine { cont ->
        Native.devicePushToken { token -> if (cont.isActive) cont.resumeWith(Result.success(token?.ifBlank { null })) }
    }

    private suspend fun upload(token: String): Boolean {
        val uid = FirebaseAuth.userId() ?: return false
        val row = buildJsonArray {
            add(
                buildJsonObject {
                    put("user_id", uid)
                    put("token", token)
                    put("platform", "android")
                    put("updated_at", Clock.System.now().toString())
                },
            )
        }.toString()
        val res = runCatching {
            com.alekpeed.lifeos.sync.FirebaseSync.request("POST", "/rest/v1/fcm_tokens", row)
        }.getOrNull() ?: return false
        return res.ok
    }
}
