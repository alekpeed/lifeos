package com.alekpeed.lifeos.net

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.net.HttpURLConnection
import java.net.URL

actual suspend fun httpRequest(
    method: String,
    url: String,
    headers: Map<String, String>,
    body: String?,
): NetResponse = withContext(Dispatchers.IO) {
    val conn = URL(url).openConnection() as HttpURLConnection
    try {
        conn.requestMethod = method
        conn.connectTimeout = 30000
        conn.readTimeout = 60000
        headers.forEach { (k, v) -> conn.setRequestProperty(k, v) }
        if (body != null) {
            conn.doOutput = true
            conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
        }
        val status = conn.responseCode
        val stream = if (status in 200..299) conn.inputStream else (conn.errorStream ?: conn.inputStream)
        val text = stream?.bufferedReader()?.use(BufferedReader::readText) ?: ""
        NetResponse(status, text)
    } catch (e: Exception) {
        NetResponse(-1, e.message ?: "network error")
    } finally {
        conn.disconnect()
    }
}
