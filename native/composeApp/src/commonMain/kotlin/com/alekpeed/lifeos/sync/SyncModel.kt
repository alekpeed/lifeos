package com.alekpeed.lifeos.sync

import kotlinx.serialization.Serializable

// One syncable unit. In this build a "record" is a single Storage key — the whole
// per-key text blob — which is the granularity the existing 42 screens already
// persist at. `updatedAt` (epoch millis) drives last-write-wins; `deleted` is a
// tombstone so a deletion propagates instead of a stale value being resurrected.
@Serializable
data class SyncRecord(
    val key: String,
    val text: String? = null, // null when deleted
    val updatedAt: Long,
    val deleted: Boolean = false,
)

// Local per-key metadata kept alongside the value.
@Serializable
data class RecordMeta(
    val updatedAt: Long,
    val deleted: Boolean = false,
)
