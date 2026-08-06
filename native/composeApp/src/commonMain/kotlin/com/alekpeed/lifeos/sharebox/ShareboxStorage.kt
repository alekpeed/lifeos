package com.alekpeed.lifeos.sharebox

import com.alekpeed.lifeos.net.NetResponse
import com.alekpeed.lifeos.net.httpGetBytesBase64
import com.alekpeed.lifeos.net.httpRequest
import com.alekpeed.lifeos.net.httpSendBytes
import com.alekpeed.lifeos.sync.SupabaseAuth
import com.alekpeed.lifeos.sync.SupabaseConfig
import kotlinx.datetime.Clock

// Sharebox file transport over Supabase Storage. Files (kind=file) are the one
// Sharebox payload that does NOT live on the device-local blob store — they need
// to reach the friend's device, so the bytes go to the private `sharebox-files`
// bucket and only the object path rides on the sharebox_items row (storage_path).
//
// Access control is the same membership model as the tables: bucket RLS scopes
// every object to `is_space_member(<first path segment>)`, so the path MUST start
// with "<spaceId>/". A non-member can't read or write another space's files even
// with the anon key.
object ShareboxStorage {
    private const val BUCKET = "sharebox-files"
    private val OBJECT = "${SupabaseConfig.URL}/storage/v1/object"

    // Upload the base64 bytes to <spaceId>/<millis>-<safeName>; returns the stored
    // object path (what goes in sharebox_items.storage_path), or a failure.
    suspend fun upload(spaceId: String, name: String, mime: String, base64: String): Result<String> {
        if (!SupabaseAuth.isSignedIn()) return Result.failure(IllegalStateException("Sign in to share files"))
        val path = "$spaceId/${Clock.System.now().toEpochMilliseconds()}-${safeName(name)}"
        val contentType = mime.ifBlank { "application/octet-stream" }
        val res = sendUpload(path, contentType, base64)
        val ok = if (res.status == 401 && SupabaseAuth.refresh()) sendUpload(path, contentType, base64) else res
        return if (ok.ok) Result.success(path)
        else Result.failure(RuntimeException(errorText(ok, "Upload failed")))
    }

    private suspend fun sendUpload(path: String, contentType: String, base64: String): NetResponse =
        httpSendBytes(
            "POST", "$OBJECT/$BUCKET/$path",
            buildMap {
                put("apikey", SupabaseConfig.ANON_KEY)
                SupabaseAuth.accessToken()?.let { put("Authorization", "Bearer $it") }
                put("content-type", contentType)
                put("x-upsert", "false")
            },
            base64,
        )

    // Download an object back as base64 (no data: prefix), or null on failure.
    suspend fun download(storagePath: String): String? {
        if (!SupabaseAuth.isSignedIn()) return null
        fun headers() = buildMap {
            put("apikey", SupabaseConfig.ANON_KEY)
            SupabaseAuth.accessToken()?.let { put("Authorization", "Bearer $it") }
        }
        val url = "$OBJECT/$BUCKET/$storagePath"
        return httpGetBytesBase64(url, headers()) ?: run {
            if (SupabaseAuth.refresh()) httpGetBytesBase64(url, headers()) else null
        }
    }

    // Best-effort delete of the stored object (when its item is removed). RLS lets
    // any space member delete; failures are swallowed — the row delete is the
    // source of truth, an orphaned object is harmless.
    suspend fun delete(storagePath: String) {
        if (storagePath.isBlank() || !SupabaseAuth.isSignedIn()) return
        httpRequest(
            "DELETE", "$OBJECT/$BUCKET/$storagePath",
            buildMap {
                put("apikey", SupabaseConfig.ANON_KEY)
                SupabaseAuth.accessToken()?.let { put("Authorization", "Bearer $it") }
            },
            null,
        )
    }

    private fun safeName(name: String): String {
        val cleaned = name.trim().ifBlank { "file" }
            .map { c -> if (c.isLetterOrDigit() || c == '.' || c == '-' || c == '_') c else '_' }
            .joinToString("")
        return cleaned.takeLast(80)
    }

    private fun errorText(res: NetResponse, fallback: String): String = when (res.status) {
        -1 -> "No network connection."
        else -> "$fallback (HTTP ${res.status})."
    }
}
