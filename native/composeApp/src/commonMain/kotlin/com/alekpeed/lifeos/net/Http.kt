package com.alekpeed.lifeos.net

// A small, dependency-free HTTP surface shared by the external integrations.
// Both targets are JVM, so the actuals use java.net.HttpURLConnection off the main
// thread. Integrations build their own request/parse on top of this.
data class NetResponse(val status: Int, val body: String) {
    val ok: Boolean get() = status in 200..299
}

// Generic request. `body` is written only when non-null (GET passes null). Never
// throws — network failures come back as status -1 with the message in `body`.
expect suspend fun httpRequest(
    method: String,
    url: String,
    headers: Map<String, String> = emptyMap(),
    body: String? = null,
): NetResponse

suspend fun httpGet(url: String, headers: Map<String, String> = emptyMap()): NetResponse =
    httpRequest("GET", url, headers, null)

// GET binary image bytes and return them as standard base64 (no data: prefix),
// ready to hand to the blob store. Returns null on any non-2xx / network failure,
// or if the payload is empty or implausibly large. Text httpGet would corrupt
// binary data through the charset decode, so images need this dedicated path.
expect suspend fun httpGetImageBase64(url: String): String?

suspend fun httpPostJson(url: String, headers: Map<String, String>, body: String): NetResponse =
    httpRequest("POST", url, headers, body)
