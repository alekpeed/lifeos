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

// The model the app ships with. If AI calls start failing with a "model not
// found" / 404, verify this id against OpenAI's current model list — a mistyped
// or out-of-date id fails every request.
const val DEFAULT_OPENAI_MODEL = "gpt-5.6-luna"

// OpenAI Chat Completions. Works directly from the native app — the browser-CORS
// limitation that blocks it in the web build doesn't apply here (no browser origin,
// plain HTTPS). Key + model live in local Settings.
object OpenAiClient {

    private val json = Json { ignoreUnknownKeys = true }

    // A key set in Settings wins; otherwise fall back to the key baked into the
    // build (from a CI secret), so the app works out of the box.
    fun key(): String = Storage.read("OpenAiKey")?.trim()?.ifBlank { null } ?: bakedOpenAiKey()
    fun model(): String = Storage.read("OpenAiModel")?.trim()?.ifBlank { null } ?: DEFAULT_OPENAI_MODEL

    suspend fun ask(system: String, userText: String, maxTokens: Int): AiReply {
        val key = key()
        if (key.isEmpty()) return AiReply("Add your OpenAI API key in Settings to use this.", isError = true)

        val body = buildJsonObject {
            put("model", model())
            put("max_completion_tokens", maxTokens)
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

    // Embeddings — text-embedding-3-small (1536-dim, unit-normalized so a dot
    // product IS cosine similarity). Backs Ask's semantic memory search. Returns
    // one vector per input in order, or null if the call failed / no key.
    suspend fun embed(inputs: List<String>): List<FloatArray>? {
        val key = key()
        if (key.isEmpty() || inputs.isEmpty()) return null
        val body = buildJsonObject {
            put("model", "text-embedding-3-small")
            putJsonArrayCompat("input") { inputs.forEach { add(it.take(6000)) } }
        }.toString()
        val resp = httpPostJson(
            "https://api.openai.com/v1/embeddings",
            mapOf("Authorization" to "Bearer $key", "content-type" to "application/json"),
            body,
        )
        if (!resp.ok) return null
        return try {
            json.parseToJsonElement(resp.body).jsonObject["data"]?.jsonArray?.map { row ->
                row.jsonObject["embedding"]!!.jsonArray.map { it.jsonPrimitive.content.toFloat() }.toFloatArray()
            }
        } catch (e: Exception) {
            null
        }
    }

    // Vision: a chat message whose content is a text part plus an image_url part
    // carrying the JPEG as a data URI.
    suspend fun askWithImage(system: String, userText: String, imageBase64: String, maxTokens: Int): AiReply {
        val key = key()
        if (key.isEmpty()) return AiReply("Add your OpenAI API key in Settings to use this.", isError = true)

        val body = buildJsonObject {
            put("model", model())
            put("max_completion_tokens", maxTokens)
            putJsonArrayCompat("messages") {
                add(buildJsonObject { put("role", "system"); put("content", system) })
                add(buildJsonObject {
                    put("role", "user")
                    putJsonArrayCompat("content") {
                        add(buildJsonObject { put("type", "text"); put("text", userText) })
                        add(buildJsonObject {
                            put("type", "image_url")
                            put("image_url", buildJsonObject { put("url", "data:image/jpeg;base64,$imageBase64") })
                        })
                    }
                })
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

    // Multi-image vision: a single user message whose content interleaves text and
    // image_url (data-URI) parts.
    suspend fun askVision(system: String, blocks: List<VisionBlock>, maxTokens: Int): AiReply {
        val key = key()
        if (key.isEmpty()) return AiReply("Add your OpenAI API key in Settings to use this.", isError = true)

        val body = buildJsonObject {
            put("model", model())
            put("max_completion_tokens", maxTokens)
            putJsonArrayCompat("messages") {
                add(buildJsonObject { put("role", "system"); put("content", system) })
                add(buildJsonObject {
                    put("role", "user")
                    putJsonArrayCompat("content") {
                        blocks.forEach { b ->
                            when (b) {
                                is VisionBlock.Txt -> add(buildJsonObject { put("type", "text"); put("text", b.text) })
                                is VisionBlock.Img -> add(buildJsonObject {
                                    put("type", "image_url")
                                    put("image_url", buildJsonObject { put("url", "data:image/jpeg;base64,${b.base64}") })
                                })
                            }
                        }
                    }
                })
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
