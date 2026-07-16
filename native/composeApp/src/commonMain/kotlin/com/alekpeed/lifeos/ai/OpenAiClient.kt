package com.alekpeed.lifeos.ai

import com.alekpeed.lifeos.Storage
import com.alekpeed.lifeos.net.httpPostJson
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

const val DEFAULT_OPENAI_MODEL = "gpt-4o-mini"

// OpenAI Chat Completions. Works directly from the native app — the browser-CORS
// limitation that blocks it in the web build doesn't apply here (no browser origin,
// plain HTTPS). Key + model live in local Settings.
object OpenAiClient {

    private val json = Json { ignoreUnknownKeys = true }

    fun key(): String = Storage.read("OpenAiKey")?.trim().orEmpty()
    fun model(): String = Storage.read("OpenAiModel")?.trim()?.ifBlank { null } ?: DEFAULT_OPENAI_MODEL

    suspend fun ask(system: String, userText: String, maxTokens: Int): AiReply {
        val key = key()
        if (key.isEmpty()) return AiReply("Add your OpenAI API key in Settings to use this.", isError = true)

        val body = buildJsonObject {
            put("model", model())
            put("max_tokens", maxTokens)
            putJsonArrayCompat("messages") {
                add(buildJsonObject { put("role", "system"); put("content", system) })
                add(buildJsonObject { put("role", "user"); put("content", userText) })
            }
        }.toString()

        val resp = httpPostJson(
            "https://api.openai.com/v1/chat/completions",
            mapOf("Authorization" to "Bearer $key", "content-type" to "application/json"),
            body,
        )

        if (resp.ok) {
            return try {
                val text = json.parseToJsonElement(resp.body).jsonObject["choices"]
                    ?.jsonArray?.firstOrNull()?.jsonObject
                    ?.get("message")?.jsonObject
                    ?.get("content")?.jsonPrimitive?.content
                if (!text.isNullOrBlank()) AiReply(text.trim(), isError = false)
                else AiReply("The model returned no answer.", isError = true)
            } catch (e: Exception) {
                AiReply("Got an unexpected response from OpenAI.", isError = true)
            }
        }

        val detail = try {
            json.parseToJsonElement(resp.body).jsonObject["error"]?.jsonObject
                ?.get("message")?.jsonPrimitive?.content
        } catch (e: Exception) { null }
        return AiReply(friendlyAiError(resp.status, detail, "OpenAI"), isError = true)
    }
}

// kotlinx.serialization's buildJsonObject has no putJsonArray in this version's DSL
// surface we rely on, so build the array separately and put it.
private inline fun kotlinx.serialization.json.JsonObjectBuilder.putJsonArrayCompat(
    name: String,
    builder: kotlinx.serialization.json.JsonArrayBuilder.() -> Unit,
) {
    put(name, buildJsonArray(builder))
}

// Shared friendly-error mapping for the OpenAI/Gemini providers.
internal fun friendlyAiError(status: Int, detail: String?, who: String): String = when (status) {
    401 -> "Invalid $who API key — check it in Settings."
    403 -> "This $who key doesn't have access to that model."
    404 -> "Model not found — pick another in Settings."
    429 -> "Rate limited by $who — try again in a moment."
    in 500..599 -> "$who had a server error — try again."
    -1 -> "No network connection."
    else -> detail ?: "$who request failed (HTTP $status)."
}
