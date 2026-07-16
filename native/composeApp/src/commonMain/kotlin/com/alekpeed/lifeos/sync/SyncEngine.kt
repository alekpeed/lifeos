package com.alekpeed.lifeos.sync

import com.alekpeed.lifeos.Storage

// Last-write-wins merge over the SyncMeta record set. Pure and network-free: a
// backend adapter feeds remote records in (`applyRemote`) and takes local changes
// out (`changesToPush`). The actual round-trip needs a real backend + a device to
// confirm; the merge *rules* here are the deterministic core, decided by
// `updatedAt` with tombstones for deletes.
object SyncEngine {

    // Local records changed since the last successful sync — what to push up.
    fun changesToPush(): List<SyncRecord> {
        val since = SyncMeta.lastSyncAt
        return SyncMeta.all().entries
            .filter { it.value.updatedAt > since }
            .map { (key, m) ->
                SyncRecord(
                    key = key,
                    text = if (m.deleted) null else Storage.read(key),
                    updatedAt = m.updatedAt,
                    deleted = m.deleted,
                )
            }
    }

    // Apply remote records that are newer than what we hold. A remote delete removes
    // the local value; a remote value overwrites it. Either way the local metadata
    // takes the REMOTE timestamp, so an applied record isn't mistaken for a new local
    // change on the next push. Returns how many records were applied.
    fun applyRemote(remote: List<SyncRecord>): Int {
        var applied = 0
        for (r in remote) {
            val local = SyncMeta.metaOf(r.key)
            if (local != null && local.updatedAt >= r.updatedAt) continue // ours is newer/equal
            if (r.deleted) {
                Storage.remove(r.key)
            } else {
                Storage.write(r.key, r.text.orEmpty())
            }
            SyncMeta.setExact(r.key, r.updatedAt, r.deleted)
            applied++
        }
        return applied
    }

    // Call after a successful push+pull, with the server's high-water timestamp.
    fun markSynced(at: Long) { SyncMeta.lastSyncAt = at }

    // How many local records are waiting to be pushed (for a Settings readout).
    fun pendingCount(): Int = changesToPush().size
}
