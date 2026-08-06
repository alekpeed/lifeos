package com.alekpeed.lifeos.sync

import com.alekpeed.lifeos.net.NetResponse
import com.alekpeed.lifeos.net.httpGet
import com.alekpeed.lifeos.net.httpPostJson
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

data class SyncSummary(val pushed: Int, val applied: Int)

// The transport: pushes local changes and pulls remote ones against the shared
// `sync_records` table (store = "kv"), then hands the remote rows to SyncEngine's
// last-write-wins merge. Timestamps convert between the app's epoch-millis and the
// table's timestamptz. The merge rules are deterministic (SyncEngine); the actual
// round-trip needs a signed-in account + a device to confirm.
object SupabaseSync {
    private val json = Json { ignoreUnknownKeys = true }
    private val REST = "${SupabaseConfig.URL}/rest/v1/sync_records"

    suspend fun syncNow(): Result<SyncSummary> {
        if (!SupabaseAuth.isSignedIn()) return Result.failure(IllegalStateException("Sign in to sync"))
        return try {
            val pushed = push().getOrElse { return Result.failure(it) }
            val applied = pull().getOrElse { return Result.failure(it) }
            SyncEngine.markSynced(now())
            Result.success(SyncSummary(pushed, applied))
        } catch (e: Exception) {
            Result.failure(RuntimeException("Sync failed"))
        }
    }

    // ---- push ----

    private suspend fun push(): Result<Int> {
        val changes = SyncEngine.changesToPush()
        if (changes.isEmpty()) return Result.success(0)
        val uid = SupabaseAuth.userId() ?: return Result.failure(IllegalStateException("Not signed in"))
        val rows = buildJsonArray {
            for (r in changes) add(buildJsonObject {
                put("user_id", uid)
                put("store", SupabaseConfig.STORE)
                put("record_id", r.key)
                put("data", buildJsonObject { put("text", r.text ?: "") })
                put("updated_at", iso(r.updatedAt))
                put("deleted_at", if (r.deleted) iso(r.updatedAt) else null)
            })
        }.toString()
        val res = authed { headers ->
            httpPostJson(
                "$REST?on_conflict=user_id,store,record_id",
                headers + ("Prefer" to "resolution=merge-duplicates"),
                rows,
            )
        }
        return if (res.ok) Result.success(changes.size)
        else Result.failure(RuntimeException("Couldn't push changes (HTTP ${res.status})"))
    }

    // ---- pull ----

    private suspend fun pull(): Result<Int> {
        val res = authed { headers ->
            httpGet(
                "$REST?store=eq.${SupabaseConfig.STORE}&select=record_id,data,updated_at,deleted_at",
                headers,
            )
        }
        if (!res.ok) return Result.failure(RuntimeException("Couldn't pull changes (HTTP ${res.status})"))
        val remote = try {
            json.parseToJsonElement(res.body).jsonArray.mapNotNull { el ->
                val o = el.jsonObject
                val key = o["record_id"]?.jsonPrimitive?.content ?: return@mapNotNull null
                val text = o["data"]?.jsonObject?.get("text")?.jsonPrimitive?.content
                val updatedAt = o["updated_at"]?.jsonPrimitive?.content?.let(::millis) ?: return@mapNotNull null
                val deleted = o["deleted_at"]?.let { it.jsonPrimitive.contentOrNullSafe() != null } ?: false
                SyncRecord(key = key, text = if (deleted) null else text, updatedAt = updatedAt, deleted = deleted)
            }
        } catch (e: Exception) {
            return Result.failure(RuntimeException("Unexpected sync response"))
        }
        return Result.success(SyncEngine.applyRemote(remote))
    }

    // ---- auth plumbing ----

    // Run an authed request; on 401 refresh the token once and retry.
    private suspend fun authed(call: suspend (Map<String, String>) -> NetResponse): NetResponse {
        var res = call(headers())
        if (res.status == 401 && SupabaseAuth.refresh()) res = call(headers())
        return res
    }

    private fun headers(): Map<String, String> = buildMap {
        put("apikey", SupabaseConfig.ANON_KEY)
        SupabaseAuth.accessToken()?.let { put("Authorization", "Bearer $it") }
        put("content-type", "application/json")
    }

    private fun now(): Long = Clock.System.now().toEpochMilliseconds()
    private fun iso(ms: Long): String = Instant.fromEpochMilliseconds(ms).toString()
    private fun millis(isoText: String): Long? = runCatching { Instant.parse(isoText).toEpochMilliseconds() }.getOrNull()
}

// A JSON null reads back as content "null" via jsonPrimitive; distinguish a real
// value from SQL NULL by checking JsonNull explicitly.
private fun kotlinx.serialization.json.JsonPrimitive.contentOrNullSafe(): String? =
    if (this is kotlinx.serialization.json.JsonNull) null else content
