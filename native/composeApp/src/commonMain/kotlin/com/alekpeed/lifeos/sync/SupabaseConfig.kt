package com.alekpeed.lifeos.sync

// Supabase project coordinates. The anon key is public by design — real access
// control is Postgres Row Level Security (each row scoped to auth.uid()), exactly
// as the web app ships it. The native per-key records live in the shared
// `sync_records` table under a dedicated `store` namespace, so they coexist with
// the web app's own stores without colliding.
object SupabaseConfig {
    const val URL = "https://ukqdbxxhxxafbcnkmskg.supabase.co"
    const val ANON_KEY =
        "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6InVrcWRieHhoeHhhZmJjbmttc2tnIiwicm9sZSI6ImFub24iLCJpYXQiOjE3ODM1MzA5MzcsImV4cCI6MjA5OTEwNjkzN30.Z-h6cSQrlIYjmM1ROs4oaBxPHpAb8ajwT5QGVgaPWmo"

    // Namespace for the native app's per-key blobs inside sync_records.
    const val STORE = "kv"
}
