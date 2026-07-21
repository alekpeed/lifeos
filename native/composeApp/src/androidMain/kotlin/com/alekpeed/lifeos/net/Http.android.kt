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

actual suspend fun httpGetImageBase64(url: String): String? = withContext(Dispatchers.IO) {
    val conn = URL(url).openConnection() as HttpURLConnection
    try {
        conn.requestMethod = "GET"
        conn.connectTimeout = 30000
        conn.readTimeout = 60000
        conn.instanceFollowRedirects = true
        val status = conn.responseCode
        if (status !in 200..299) return@withContext null
        val bytes = conn.inputStream.use { it.readBytes() }
        if (bytes.isEmpty() || bytes.size > 8 * 1024 * 1024) return@withContext null
        android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)
    } catch (e: Exception) {
        null
    } finally {
        conn.disconnect()
    }
}

actual suspend fun httpSendBytes(
    method: String,
    url: String,
    headers: Map<String, String>,
    base64Body: String,
): NetResponse = withContext(Dispatchers.IO) {
    val conn = URL(url).openConnection() as HttpURLConnection
    try {
        val bytes = android.util.Base64.decode(base64Body, android.util.Base64.DEFAULT)
        conn.requestMethod = method
        conn.connectTimeout = 30000
        conn.readTimeout = 120000
        headers.forEach { (k, v) -> conn.setRequestProperty(k, v) }
        conn.doOutput = true
        conn.setFixedLengthStreamingMode(bytes.size)
        conn.outputStream.use { it.write(bytes) }
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

actual suspend fun httpGetBytesBase64(url: String, headers: Map<String, String>): String? = withContext(Dispatchers.IO) {
    val conn = URL(url).openConnection() as HttpURLConnection
    try {
        conn.requestMethod = "GET"
        conn.connectTimeout = 30000
        conn.readTimeout = 120000
        conn.instanceFollowRedirects = true
        headers.forEach { (k, v) -> conn.setRequestProperty(k, v) }
        val status = conn.responseCode
        if (status !in 200..299) return@withContext null
        val bytes = conn.inputStream.use { it.readBytes() }
        if (bytes.isEmpty() || bytes.size > 50 * 1024 * 1024) return@withContext null
        android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)
    } catch (e: Exception) {
        null
    } finally {
        conn.disconnect()
    }
}
