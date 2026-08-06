package com.alekpeed.lifeos.sync

import com.alekpeed.lifeos.Storage
import kotlinx.datetime.Clock
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

// Sidecar metadata that turns the plain per-key Storage into a sync-ready record
// store WITHOUT changing how any screen reads or writes. Every `Storage.write`
// stamps the key's `updatedAt` here; `Storage.remove` leaves a tombstone. The
// manifest is persisted as JSON under a reserved key. This is the local half of
// last-write-wins sync — the network half (a backend) layers on top later.
object SyncMeta {
    private const val META_KEY = "__syncmeta"
    private const val LASTSYNC_KEY = "__lastsync"
    private val json = Json { ignoreUnknownKeys = true }

    private var loaded = false
    private val meta = mutableMapOf<String, RecordMeta>()

    // Keys we manage ourselves must never be tracked as user data (would recurse
    // through Storage.write forever).
    private fun reserved(key: String) = key.startsWith("__")

    private fun now(): Long = Clock.System.now().toEpochMilliseconds()

    private fun ensureLoaded() {
        if (loaded) return
        loaded = true
        val raw = Storage.read(META_KEY)
        if (!raw.isNullOrBlank()) {
            runCatching { meta.putAll(json.decodeFromString<Map<String, RecordMeta>>(raw)) }
        }
    }

    private fun persist() {
        runCatching { Storage.write(META_KEY, json.encodeToString(meta.toMap())) }
    }

    // Called from Storage.write for every user key.
    fun record(key: String) {
        if (reserved(key)) return
        ensureLoaded()
        meta[key] = RecordMeta(now(), false)
        persist()
    }

    // Called from Storage.remove.
    fun tombstone(key: String) {
        if (reserved(key)) return
        ensureLoaded()
        meta[key] = RecordMeta(now(), true)
        persist()
    }

    // Set an exact (updatedAt, deleted) — used when applying a remote record so it
    // keeps the remote timestamp instead of looking like a fresh local change.
    fun setExact(key: String, updatedAt: Long, deleted: Boolean) {
        if (reserved(key)) return
        ensureLoaded()
        meta[key] = RecordMeta(updatedAt, deleted)
        persist()
    }

    fun metaOf(key: String): RecordMeta? { ensureLoaded(); return meta[key] }
    fun all(): Map<String, RecordMeta> { ensureLoaded(); return meta.toMap() }

    var lastSyncAt: Long
        get() = Storage.read(LASTSYNC_KEY)?.toLongOrNull() ?: 0L
        set(v) { Storage.write(LASTSYNC_KEY, v.toString()) }
}
