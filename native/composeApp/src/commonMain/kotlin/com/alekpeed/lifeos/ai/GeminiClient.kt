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

const val DEFAULT_GEMINI_MODEL = "gemini-1.5-flash"

// Google Gemini (Generative Language API). Simple API key, passed as a query param.
// Key + model live in local Settings.
object GeminiClient {

    private val json = Json { ignoreUnknownKeys = true }

    fun key(): String = Storage.read("GeminiKey")?.trim().orEmpty()
    fun model(): String = Storage.read("GeminiModel")?.trim()?.ifBlank { null } ?: DEFAULT_GEMINI_MODEL

    suspend fun ask(system: String, userText: String, maxTokens: Int): AiReply {
        val key = key()
        if (key.isEmpty()) return AiReply("Add your Google Gemini API key in Settings to use this.", isError = true)

        val body = buildJsonObject {
            put("system_instruction", buildJsonObject {
                put("parts", buildJsonArray { add(buildJsonObject { put("text", system) }) })
            })
            put("contents", buildJsonArray {
                add(buildJsonObject {
                    put("role", "user")
                    put("parts", buildJsonArray { add(buildJsonObject { put("text", userText) }) })
                })
            })
            put("generationConfig", buildJsonObject { put("maxOutputTokens", maxTokens) })
        }.toString()

        val url = "https://generativelanguage.googleapis.com/v1beta/models/${model()}:generateContent?key=$key"
        val resp = httpPostJson(url, mapOf("content-type" to "application/json"), body)

        if (resp.ok) {
            return try {
                val text = json.parseToJsonElement(resp.body).jsonObject["candidates"]
                    ?.jsonArray?.firstOrNull()?.jsonObject
                    ?.get("content")?.jsonObject
                    ?.get("parts")?.jsonArray?.firstOrNull()?.jsonObject
                    ?.get("text")?.jsonPrimitive?.content
                if (!text.isNullOrBlank()) AiReply(text.trim(), isError = false)
                else AiReply("The model returned no answer.", isError = true)
            } catch (e: Exception) {
                AiReply("Got an unexpected response from Gemini.", isError = true)
            }
        }

        val detail = try {
            json.parseToJsonElement(resp.body).jsonObject["error"]?.jsonObject
                ?.get("message")?.jsonPrimitive?.content
        } catch (e: Exception) { null }
        return AiReply(friendlyAiError(resp.status, detail, "Gemini"), isError = true)
    }

    // Vision: a user turn whose parts carry the instruction text plus the JPEG as
    // inline_data.
    suspend fun askWithImage(system: String, userText: String, imageBase64: String, maxTokens: Int): AiReply {
        val key = key()
        if (key.isEmpty()) return AiReply("Add your Google Gemini API key in Settings to use this.", isError = true)

        val body = buildJsonObject {
            put("system_instruction", buildJsonObject {
                put("parts", buildJsonArray { add(buildJsonObject { put("text", system) }) })
            })
            put("contents", buildJsonArray {
                add(buildJsonObject {
                    put("role", "user")
                    put("parts", buildJsonArray {
                        add(buildJsonObject { put("text", userText) })
                        add(buildJsonObject {
                            put("inline_data", buildJsonObject {
                                put("mime_type", "image/jpeg")
                                put("data", imageBase64)
                            })
                        })
                    })
                })
            })
            put("generationConfig", buildJsonObject { put("maxOutputTokens", maxTokens) })
        }.toString()

        val url = "https://generativelanguage.googleapis.com/v1beta/models/${model()}:generateContent?key=$key"
        val resp = httpPostJson(url, mapOf("content-type" to "application/json"), body)

        if (resp.ok) {
            return try {
                val text = json.parseToJsonElement(resp.body).jsonObject["candidates"]
                    ?.jsonArray?.firstOrNull()?.jsonObject
                    ?.get("content")?.jsonObject
                    ?.get("parts")?.jsonArray?.firstOrNull()?.jsonObject
                    ?.get("text")?.jsonPrimitive?.content
                if (!text.isNullOrBlank()) AiReply(text.trim(), isError = false)
                else AiReply("The model returned no answer.", isError = true)
            } catch (e: Exception) {
                AiReply("Got an unexpected response from Gemini.", isError = true)
            }
        }

        val detail = try {
            json.parseToJsonElement(resp.body).jsonObject["error"]?.jsonObject
                ?.get("message")?.jsonPrimitive?.content
        } catch (e: Exception) { null }
        return AiReply(friendlyAiError(resp.status, detail, "Gemini"), isError = true)
    }

    // Multi-image vision: one user turn whose parts interleave text and inline_data.
    suspend fun askVision(system: String, blocks: List<VisionBlock>, maxTokens: Int): AiReply {
        val key = key()
        if (key.isEmpty()) return AiReply("Add your Google Gemini API key in Settings to use this.", isError = true)

        val body = buildJsonObject {
            put("system_instruction", buildJsonObject {
                put("parts", buildJsonArray { add(buildJsonObject { put("text", system) }) })
            })
            put("contents", buildJsonArray {
                add(buildJsonObject {
                    put("role", "user")
                    put("parts", buildJsonArray {
                        blocks.forEach { b ->
                            when (b) {
                                is VisionBlock.Txt -> add(buildJsonObject { put("text", b.text) })
                                is VisionBlock.Img -> add(buildJsonObject {
                                    put("inline_data", buildJsonObject {
                                        put("mime_type", "image/jpeg"); put("data", b.base64)
                                    })
                                })
                            }
                        }
                    })
                })
            })
            put("generationConfig", buildJsonObject { put("maxOutputTokens", maxTokens) })
        }.toString()

        val url = "https://generativelanguage.googleapis.com/v1beta/models/${model()}:generateContent?key=$key"
        val resp = httpPostJson(url, mapOf("content-type" to "application/json"), body)

        if (resp.ok) {
            return try {
                val text = json.parseToJsonElement(resp.body).jsonObject["candidates"]
                    ?.jsonArray?.firstOrNull()?.jsonObject
                    ?.get("content")?.jsonObject
                    ?.get("parts")?.jsonArray?.firstOrNull()?.jsonObject
                    ?.get("text")?.jsonPrimitive?.content
                if (!text.isNullOrBlank()) AiReply(text.trim(), isError = false)
                else AiReply("The model returned no answer.", isError = true)
            } catch (e: Exception) {
                AiReply("Got an unexpected response from Gemini.", isError = true)
            }
        }
        val detail = try {
            json.parseToJsonElement(resp.body).jsonObject["error"]?.jsonObject
                ?.get("message")?.jsonPrimitive?.content
        } catch (e: Exception) { null }
        return AiReply(friendlyAiError(resp.status, detail, "Gemini"), isError = true)
    }
}
