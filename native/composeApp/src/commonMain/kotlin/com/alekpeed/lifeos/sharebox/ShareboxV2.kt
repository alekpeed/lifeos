package com.alekpeed.lifeos.sharebox

import com.alekpeed.lifeos.net.NetResponse
import com.alekpeed.lifeos.net.httpRequest
import com.alekpeed.lifeos.sync.SupabaseAuth
import com.alekpeed.lifeos.sync.SupabaseConfig
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put

// Sharebox v2 — the real multi-user backend, talking to the same Supabase tables
// the web app ships (sharebox_spaces / sharebox_members / sharebox_items) over
// PostgREST. A "space" is a row both people are members of; membership IS the
// share. Postgres Row Level Security is the access control — the native app just
// signs in (email account, shared across devices) and calls REST.
//
// Files (kind=file) upload to the private `sharebox-files` Storage bucket (see
// ShareboxStorage); only the object path rides on the row's storage_path.

@Serializable
data class SpaceRow(
    val id: String,
    val name: String = "Sharebox",
    @SerialName("created_by") val createdBy: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
)

@Serializable
data class MemberRow(
    @SerialName("user_id") val userId: String,
    @SerialName("display_name") val displayName: String = "",
    @SerialName("joined_at") val joinedAt: String? = null,
)

@Serializable
data class ItemRow(
    val id: String,
    @SerialName("space_id") val spaceId: String? = null,
    @SerialName("posted_by") val postedBy: String? = null,
    val kind: String = "note",
    val url: String? = null,
    val title: String? = null,
    val body: String? = null,
    val urgency: String = "normal",
    @SerialName("storage_path") val storagePath: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null,
)

object ShareboxV2 {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val REST = "${SupabaseConfig.URL}/rest/v1"

    // ---- spaces & membership ----

    suspend fun getMySpaces(): Result<List<SpaceRow>> = req {
        get("$REST/sharebox_spaces?select=id,name,created_by,created_at&order=created_at.asc")
    }.map { decodeList(it) }

    // create_space() is a SECURITY DEFINER RPC that inserts the space AND the
    // creator's membership atomically, then returns the space row (the SELECT
    // policy needs membership to exist first, so a plain insert can't read back).
    suspend fun createSpace(name: String, displayName: String): Result<SpaceRow> {
        val body = buildJsonObject {
            put("p_name", name.ifBlank { "Sharebox" })
            put("p_display_name", displayName.ifBlank { "Me" })
        }.toString()
        return req { post("$REST/rpc/create_space", body) }.mapCatching { res ->
            val el = json.parseToJsonElement(res)
            val obj = if (el is kotlinx.serialization.json.JsonArray) el.jsonArray.first().jsonObject else el.jsonObject
            json.decodeFromJsonElement(SpaceRow.serializer(), obj)
        }
    }

    // Join (or update display name in) a space someone shared the id of. Idempotent
    // via upsert on the (space_id, user_id) primary key.
    suspend fun joinSpace(spaceId: String, displayName: String): Result<Unit> {
        val uid = SupabaseAuth.userId() ?: return Result.failure(IllegalStateException("Sign in to join a space"))
        val body = "[" + buildJsonObject {
            put("space_id", spaceId)
            put("user_id", uid)
            put("display_name", displayName.ifBlank { "Me" })
        }.toString() + "]"
        return req {
            post(
                "$REST/sharebox_members?on_conflict=space_id,user_id",
                body,
                extra = mapOf("Prefer" to "resolution=merge-duplicates"),
            )
        }.map { }
    }

    suspend fun getMembers(spaceId: String): Result<List<MemberRow>> = req {
        get("$REST/sharebox_members?space_id=eq.$spaceId&select=user_id,display_name,joined_at")
    }.map { decodeList(it) }

    // ---- items ----

    suspend fun listItems(spaceId: String): Result<List<ItemRow>> = req {
        get("$REST/sharebox_items?space_id=eq.$spaceId&select=*&order=created_at.desc")
    }.map { decodeList(it) }

    suspend fun addItem(
        spaceId: String,
        kind: String,
        url: String?,
        title: String?,
        body: String?,
        urgency: String,
        storagePath: String? = null,
    ): Result<Unit> {
        val uid = SupabaseAuth.userId() ?: return Result.failure(IllegalStateException("Sign in to post"))
        val rowJson = "[" + buildJsonObject {
            put("space_id", spaceId)
            put("posted_by", uid)
            put("kind", kind)
            if (url != null) put("url", url) else put("url", null as String?)
            if (title != null) put("title", title) else put("title", null as String?)
            if (body != null) put("body", body) else put("body", null as String?)
            put("urgency", urgency)
            if (storagePath != null) put("storage_path", storagePath) else put("storage_path", null as String?)
        }.toString() + "]"
        return req { post("$REST/sharebox_items", rowJson) }.map { }
    }

    suspend fun removeItem(id: String): Result<Unit> =
        req { delete("$REST/sharebox_items?id=eq.$id") }.map { }

    // ---- REST plumbing (mirrors SupabaseSync: authed request, refresh on 401) ----

    private class Call(val method: String, val url: String, val body: String?, val extra: Map<String, String>)

    private fun get(url: String) = Call("GET", url, null, emptyMap())
    private fun post(url: String, body: String, extra: Map<String, String> = emptyMap()) = Call("POST", url, body, extra)
    private fun delete(url: String) = Call("DELETE", url, null, emptyMap())

    private suspend fun req(build: () -> Call): Result<String> {
        if (!SupabaseAuth.isSignedIn()) return Result.failure(IllegalStateException("Sign in to use shared spaces"))
        val call = build()
        var res = send(call)
        if (res.status == 401 && SupabaseAuth.refresh()) res = send(call)
        return if (res.ok) Result.success(res.body)
        else Result.failure(RuntimeException(errorText(res)))
    }

    private suspend fun send(call: Call): NetResponse =
        httpRequest(call.method, call.url, headers() + call.extra, call.body)

    private fun headers(): Map<String, String> = buildMap {
        put("apikey", SupabaseConfig.ANON_KEY)
        SupabaseAuth.accessToken()?.let { put("Authorization", "Bearer $it") }
        put("content-type", "application/json")
    }

    private inline fun <reified T> decodeList(bodyText: String): List<T> =
        runCatching { json.decodeFromString<List<T>>(bodyText) }.getOrElse { emptyList() }

    private fun errorText(res: NetResponse): String {
        val detail = runCatching {
            res.body.let { json.parseToJsonElement(it).jsonObject }["message"]
                ?.let { (it as? kotlinx.serialization.json.JsonPrimitive)?.content }
        }.getOrNull()
        return detail ?: when (res.status) { -1 -> "No network connection."; else -> "Request failed (HTTP ${res.status})." }
    }
}
