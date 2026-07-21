package com.alekpeed.lifeos.realtime

import com.alekpeed.lifeos.sync.SupabaseAuth
import com.alekpeed.lifeos.sync.SupabaseConfig
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

// OkHttp-backed Supabase Realtime client. Speaks just enough of the Phoenix
// channel protocol: join a per-space topic subscribed to sharebox_items changes,
// heartbeat to stay alive, and reconnect on drops. Any postgres_changes frame
// (insert/update/delete) triggers onChange() — the screen reloads rather than
// parsing the row, which keeps this robust to payload shape.
private class ShareboxRealtimeConn(
    private val spaceId: String,
    private val onChange: () -> Unit,
) {
    private val closed = AtomicBoolean(false)
    private val ref = AtomicInteger(0)
    private val topic = "realtime:sharebox:$spaceId"
    private val json = Json { ignoreUnknownKeys = true }
    private val client = OkHttpClient.Builder()
        .pingInterval(20, TimeUnit.SECONDS)
        .build()
    @Volatile private var ws: WebSocket? = null
    private val exec: ScheduledExecutorService = Executors.newSingleThreadScheduledExecutor { r ->
        Thread(r, "sharebox-realtime").apply { isDaemon = true }
    }

    fun start() {
        connect()
        exec.scheduleAtFixedRate({ sendHeartbeat() }, 25, 25, TimeUnit.SECONDS)
    }

    private fun connect() {
        if (closed.get()) return
        val base = SupabaseConfig.URL.replaceFirst("https://", "wss://").replaceFirst("http://", "ws://")
        val url = "$base/realtime/v1/websocket?apikey=${SupabaseConfig.ANON_KEY}&vsn=1.0.0"
        val req = Request.Builder().url(url).build()
        ws = client.newWebSocket(req, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                runCatching { webSocket.send(joinMessage()) }
            }
            override fun onMessage(webSocket: WebSocket, text: String) {
                val event = runCatching {
                    json.parseToJsonElement(text).jsonObject["event"]?.jsonPrimitive?.content
                }.getOrNull()
                if (event == "postgres_changes") onChange()
            }
            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                scheduleReconnect()
            }
            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                if (code != 1000) scheduleReconnect()
            }
        })
    }

    private fun scheduleReconnect() {
        if (closed.get()) return
        runCatching { exec.schedule({ connect() }, 3, TimeUnit.SECONDS) }
    }

    private fun joinMessage(): String {
        val r = ref.incrementAndGet().toString()
        return buildJsonObject {
            put("topic", topic)
            put("event", "phx_join")
            put("ref", r)
            put("join_ref", r)
            putJsonObject("payload") {
                putJsonObject("config") {
                    putJsonObject("broadcast") { put("ack", false); put("self", false) }
                    putJsonObject("presence") { put("key", "") }
                    putJsonArray("postgres_changes") {
                        add(buildJsonObject {
                            put("event", "*")
                            put("schema", "public")
                            put("table", "sharebox_items")
                            put("filter", "space_id=eq.$spaceId")
                        })
                    }
                }
                SupabaseAuth.accessToken()?.let { put("access_token", it) }
            }
        }.toString()
    }

    private fun sendHeartbeat() {
        val socket = ws ?: return
        val msg = buildJsonObject {
            put("topic", "phoenix")
            put("event", "heartbeat")
            put("ref", ref.incrementAndGet().toString())
            putJsonObject("payload") {}
        }.toString()
        runCatching { socket.send(msg) }
    }

    fun close() {
        if (closed.getAndSet(true)) return
        runCatching { exec.shutdownNow() }
        runCatching { ws?.close(1000, "closed") }
        runCatching { client.dispatcher.executorService.shutdown() }
        runCatching { client.connectionPool.evictAll() }
    }
}

actual fun openShareboxRealtime(spaceId: String, onChange: () -> Unit): RealtimeHandle {
    val conn = ShareboxRealtimeConn(spaceId, onChange)
    conn.start()
    return RealtimeHandle { conn.close() }
}
