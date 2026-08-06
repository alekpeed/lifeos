package com.alekpeed.lifeos.realtime

// Live push for shared Sharebox spaces. Opens a Supabase Realtime (Phoenix
// channel) websocket subscribed to postgres changes on sharebox_items, filtered
// to one space; onChange() fires on any insert/update/delete so the screen can
// reload without a manual refresh. Both platforms are JVM and back this with
// OkHttp; call close() when the space/screen goes away.

// A cancellable subscription. Just wraps the platform cleanup so callers stay
// platform-agnostic (no expect/actual class to keep in sync).
class RealtimeHandle internal constructor(private val onClose: () -> Unit) {
    private var closed = false
    fun close() {
        if (closed) return
        closed = true
        onClose()
    }
}

expect fun openShareboxRealtime(spaceId: String, onChange: () -> Unit): RealtimeHandle
