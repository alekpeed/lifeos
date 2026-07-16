package com.alekpeed.lifeos.ai

// A minimal cross-platform POST. Both targets are JVM, so the actuals use
// java.net.HttpURLConnection off the main thread — no HTTP-client dependency.
data class AiHttpResponse(val status: Int, val body: String)

expect suspend fun httpPostJson(url: String, headers: Map<String, String>, body: String): AiHttpResponse
