package com.alekpeed.lifeos.ai

import com.alekpeed.lifeos.Storage
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

const val DEFAULT_AI_MODEL = "claude-opus-4-8"

@Serializable
private data class Msg(val role: String, val content: String)

@Serializable
private data class Req(
    val model: String,
    @SerialName("max_tokens") val maxTokens: Int,
    val system: String? = null,
    val messages: List<Msg>,
)

@Serializable
private data class Block(val type: String, val text: String? = null)

@Serializable
private data class Resp(
    val content: List<Block> = emptyList(),
    @SerialName("stop_reason") val stopReason: String? = null,
)

@Serializable
private data class ErrBody(val message: String? = null)

@Serializable
private data class ErrEnvelope(val error: ErrBody? = null)

data class AiReply(val text: String, val isError: Boolean)

// The AI entry point. Routes Ask + the Assistant to whichever provider is selected
// in Settings (Claude / OpenAI / Gemini); each provider's key + model live in local
// Storage. Claude is the built-in default and lives here; OpenAI/Gemini are in their
// own client objects. For Claude, `thinking` is omitted (cheaper for quick Q&A) with
// a concise-answer system prompt; Opus 4.8 rejects temperature/top_p/top_k so none
// are sent.
object AiClient {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    // "claude" | "openai" | "gemini"
    fun provider(): String = Storage.read("AiProvider")?.trim()?.ifBlank { null } ?: "claude"

    // True when the *active* provider has a key set.
    fun hasKey(): Boolean = when (provider()) {
        "openai" -> OpenAiClient.key().isNotEmpty()
        "gemini" -> GeminiClient.key().isNotEmpty()
        else -> !Storage.read("ApiKey")?.trim().isNullOrEmpty()
    }

    fun model(): String = Storage.read("AiModel")?.trim()?.ifBlank { null } ?: DEFAULT_AI_MODEL

    suspend fun ask(system: String, userText: String, maxTokens: Int = 1024): AiReply =
        when (provider()) {
            "openai" -> OpenAiClient.ask(system, userText, maxTokens)
            "gemini" -> GeminiClient.ask(system, userText, maxTokens)
            else -> askClaude(system, userText, maxTokens)
        }

    private suspend fun askClaude(system: String, userText: String, maxTokens: Int): AiReply {
        val key = Storage.read("ApiKey")?.trim().orEmpty()
        if (key.isEmpty()) return AiReply("Add your Anthropic API key in Settings to use this.", isError = true)

        val body = json.encodeToString(
            Req(
                model = model(),
                maxTokens = maxTokens,
                system = system,
                messages = listOf(Msg("user", userText)),
            ),
        )
        val headers = mapOf(
            "x-api-key" to key,
            "anthropic-version" to "2023-06-01",
            "content-type" to "application/json",
        )

        val resp = try {
            httpPostJson("https://api.anthropic.com/v1/messages", headers, body)
        } catch (e: Exception) {
            return AiReply("Couldn't reach the model: ${e.message ?: "network error"}", isError = true)
        }

        if (resp.status in 200..299) {
            val parsed = try {
                json.decodeFromString<Resp>(resp.body)
            } catch (e: Exception) {
                return AiReply("Got an unexpected response from the model.", isError = true)
            }
            if (parsed.stopReason == "refusal") {
                return AiReply("The model declined to answer that.", isError = true)
            }
            val text = parsed.content.firstOrNull { it.type == "text" && !it.text.isNullOrBlank() }?.text
            return if (text != null) AiReply(text.trim(), isError = false)
            else AiReply("The model returned no answer.", isError = true)
        }

        val detail = try {
            json.decodeFromString<ErrEnvelope>(resp.body).error?.message
        } catch (e: Exception) {
            null
        }
        val friendly = when (resp.status) {
            401 -> "Invalid API key — check it in Settings."
            403 -> "This key doesn't have access to that model."
            404 -> "Model not found — pick another in Settings."
            429 -> "Rate limited by the API — try again in a moment."
            in 500..599 -> "The API had a server error — try again."
            -1 -> "No network connection."
            else -> detail ?: "Request failed (HTTP ${resp.status})."
        }
        return AiReply(friendly, isError = true)
    }
}
