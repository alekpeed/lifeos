package com.alekpeed.lifeos

// Tiny cross-platform local store so data actually persists between launches.
// Android writes to the app's private files dir; desktop writes to ~/.lifeos.
// A real database (structured records, sync) replaces this later, but this makes
// the ported modules genuinely save right now.
expect object Storage {
    fun read(name: String): String?
    fun write(name: String, text: String)
    // Delete a key's value (leaves a sync tombstone via SyncMeta).
    fun remove(name: String)
}
