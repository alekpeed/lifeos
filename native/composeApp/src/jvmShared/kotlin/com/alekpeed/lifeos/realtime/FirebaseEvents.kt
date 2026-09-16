package com.alekpeed.lifeos.realtime

import com.alekpeed.lifeos.sync.FirebaseAuth
import com.alekpeed.lifeos.sync.FirebaseConfig
import kotlinx.coroutines.*
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

// Firestore document listener relayed as authenticated server-sent events. The same
// transport runs on Android and desktop; closing the screen cancels the connection.
fun firebaseEvents(spaceId: String, onChange: () -> Unit): RealtimeHandle {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    val client = OkHttpClient.Builder().readTimeout(65, TimeUnit.SECONDS).build()
    val job = scope.launch {
        while (isActive && FirebaseAuth.isSignedIn()) {
            try {
                val request = Request.Builder().url("${FirebaseConfig.URL}/events?space=$spaceId")
                    .header("Authorization", "Bearer ${FirebaseAuth.accessToken().orEmpty()}").build()
                client.newCall(request).execute().use { response ->
                    if (response.code == 401) FirebaseAuth.refresh()
                    else if (response.isSuccessful) response.body?.source()?.let { source ->
                        while (isActive && !source.exhausted()) {
                            if (source.readUtf8Line()?.startsWith("data:") == true) withContext(Dispatchers.Main) { onChange() }
                        }
                    }
                }
            } catch (e: CancellationException) { throw e } catch (_: Exception) { }
            delay(2000)
        }
    }
    return RealtimeHandle { job.cancel(); scope.cancel(); client.dispatcher.cancelAll(); client.connectionPool.evictAll() }
}
