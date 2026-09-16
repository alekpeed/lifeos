package com.alekpeed.lifeos.sync

import com.alekpeed.lifeos.Storage
import com.alekpeed.lifeos.storageAtomic
import com.alekpeed.lifeos.net.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.*

data class SyncSummary(val pushed: Int, val applied: Int, val blobsUp: Int = 0, val blobsDown: Int = 0, val blobsFailed: Int = 0, val blobsRemaining: Int = 0)
object FirebaseSync {
    private val gate = Mutex()
    private fun syncable(key: String) = !key.startsWith("__") && key != "MapTileIndex" && !key.contains('/') && !key.contains('\\')
    private fun baseline(key: String) = "__firebase_base_$key"
    fun pendingCount(): Int = storageAtomic { (Storage.keys().filterNot { it.startsWith("__") } + SyncMeta.all().keys).distinct().filter(::syncable).count { Storage.read(it) != Storage.read(baseline(it)) } }
    suspend fun request(method: String, path: String, body: String? = null): NetResponse {
        fun headers() = mapOf("Authorization" to "Bearer ${FirebaseAuth.accessToken().orEmpty()}", "content-type" to "application/json")
        var r = httpRequest(method, FirebaseConfig.URL + path, headers(), body)
        if (r.status == 401 && FirebaseAuth.refresh()) r = httpRequest(method, FirebaseConfig.URL + path, headers(), body)
        return r
    }
    suspend fun syncNow(): Result<SyncSummary> = gate.withLock {
        if (!FirebaseAuth.isSignedIn()) return@withLock Result.failure(IllegalStateException("Sign in to sync"))
        runCatching {
            val uid = FirebaseAuth.userId()
            val up = BlobSync.push()
            val response = request("GET", "/sync")
            check(response.ok) { "Couldn't read cloud data (HTTP ${response.status})" }
            val remoteKeys = Json.parseToJsonElement(response.body).jsonArray.map { it.jsonPrimitive.content }
            val keys = storageAtomic { (Storage.keys().filterNot { it.startsWith("__") } + SyncMeta.all().keys + remoteKeys).distinct().filter(::syncable) }
            var pushed = 0; var applied = 0
            for (key in keys) {
                check(uid == FirebaseAuth.userId()) { "Account changed during sync" }
                val (base, sent) = storageAtomic { Storage.read(baseline(key)) to Storage.read(key) }
                val payload = buildJsonObject { put("key", key); put("base", base); put("text", sent) }.toString()
                val r = request("POST", "/sync", payload)
                check(r.ok) { "Couldn't sync $key (HTTP ${r.status})" }
                val remote = Json.parseToJsonElement(r.body).jsonObject["text"]?.jsonPrimitive?.contentOrNull
                storageAtomic {
                    check(uid == FirebaseAuth.userId()) { "Account changed during sync" }
                    val current = Storage.read(key)
                    val merged = RecordMerge.text(sent, current, remote)
                    if (merged != current) {
                        com.alekpeed.lifeos.history.History.asRemote { if (merged == null) Storage.remove(key) else Storage.write(key, merged) }
                        applied++
                    }
                    // Acknowledge only the returned server version, never an edit made in flight.
                    if (remote == null) Storage.remove(baseline(key)) else Storage.write(baseline(key), remote)
                }
                if (sent != base) pushed++
            }
            val down = BlobSync.pull()
            SyncEngine.markSynced(kotlinx.datetime.Clock.System.now().toEpochMilliseconds())
            SyncSummary(pushed, applied, up.uploaded, down.downloaded, up.failed + down.failed, up.remaining + down.remaining)
        }.onFailure { if (it is kotlinx.coroutines.CancellationException) throw it }
    }
}
