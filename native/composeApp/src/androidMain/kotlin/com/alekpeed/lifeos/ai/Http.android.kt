package com.alekpeed.lifeos.ai

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.net.HttpURLConnection
import java.net.URL

actual suspend fun httpPostJson(url: String, headers: Map<String, String>, body: String): AiHttpResponse =
    withContext(Dispatchers.IO) {
        val conn = URL(url).openConnection() as HttpURLConnection
        try {
            conn.requestMethod = "POST"
            conn.doOutput = true
            conn.connectTimeout = 30000
            conn.readTimeout = 60000
            headers.forEach { (k, v) -> conn.setRequestProperty(k, v) }
            conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            val status = conn.responseCode
            val stream = if (status in 200..299) conn.inputStream else (conn.errorStream ?: conn.inputStream)
            val text = stream?.bufferedReader()?.use(BufferedReader::readText) ?: ""
            AiHttpResponse(status, text)
        } catch (e: Exception) {
            AiHttpResponse(-1, e.message ?: "network error")
        } finally {
            conn.disconnect()
        }
    }
