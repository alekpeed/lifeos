package com.alekpeed.lifeos.push

import com.alekpeed.lifeos.Storage
import com.alekpeed.lifeos.net.httpPostJson
import com.alekpeed.lifeos.platform.Native
import com.alekpeed.lifeos.sync.SupabaseAuth
import com.alekpeed.lifeos.sync.SupabaseConfig
import kotlinx.datetime.Clock
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

// §7 D-5 phase 2 — telling the server where to send.
//
// The account's devices live in `fcm_tokens` (sql/supabase-fcm-schema.sql), one row
// per device, and send-fcm reads them. A token is not a secret and not user data: it
// is an address FCM hands out, and it rolls on reinstall, on cleared app data, and on
// a restore onto a new phone — which is why this runs at every app open rather than
// once at setup, and why the send function deletes rows FCM reports as dead.
//
// The last token registered is remembered under a reserved key, so the ordinary case
// (nothing changed) costs one string comparison and no request. Reserved keys never
// sync and never enter the mutation log: this is a fact about this device, not a
// record of yours, and it has no business on your other machines.

private const val LAST_TOKEN_KEY = "__fcm_token"

object PushRegistration {

    // Called at app open. Silent about every failure: not signed in, no push transport
    // on this platform, the network down — none of these are things to interrupt
    // somebody opening their notes with.
    suspend fun registerIfNeeded() {
        if (!Native.supportsNotifications) return
        if (!SupabaseAuth.isSignedIn()) return
        val token = currentToken() ?: return
        if (token == Storage.read(LAST_TOKEN_KEY)) return
        if (upload(token)) Storage.write(LAST_TOKEN_KEY, token)
    }

    // Forget the device on sign-out, so the next account on this phone re-registers
    // rather than inheriting a row that still points at the previous one.
    fun forget() {
        Storage.write(LAST_TOKEN_KEY, "")
    }

    private suspend fun currentToken(): String? {
        var result: String? = null
        // The platform hands the token back through a callback because FCM's own API
        // does. Android's implementation is synchronous today (it returns null until a
        // Firebase project exists), and this stays correct when it stops being.
        Native.devicePushToken { result = it }
        return result?.ifBlank { null }
    }

    private suspend fun upload(token: String): Boolean {
        val uid = SupabaseAuth.userId() ?: return false
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
        val headers = buildMap {
            put("apikey", SupabaseConfig.ANON_KEY)
            SupabaseAuth.accessToken()?.let { put("Authorization", "Bearer $it") }
            put("content-type", "application/json")
            // The token is the primary key: the same device re-registering updates its
            // row rather than failing on a conflict.
            put("Prefer", "resolution=merge-duplicates")
        }
        val res = runCatching {
            httpPostJson("${SupabaseConfig.URL}/rest/v1/fcm_tokens?on_conflict=token", headers, row)
        }.getOrNull() ?: return false
        return res.ok
    }
}
