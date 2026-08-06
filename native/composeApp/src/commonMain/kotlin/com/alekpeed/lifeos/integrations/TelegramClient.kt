package com.alekpeed.lifeos.integrations

import com.alekpeed.lifeos.Storage
import com.alekpeed.lifeos.net.httpPostJson
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

// Telegram — send-only. Life OS messages you through a bot you create with
// @BotFather; you paste the bot token + your chat id in Settings. User-triggered
// only (no listener, no webhook). The two-way linked-account version is a separate,
// backend-dependent feature and is not part of this.
object TelegramClient {

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    @Serializable
    private data class SendMessage(val chat_id: String, val text: String)

    fun token(): String = Storage.read("TgToken")?.trim().orEmpty()
    fun chatId(): String = Storage.read("TgChatId")?.trim().orEmpty()
    fun isConfigured(): Boolean = token().isNotEmpty() && chatId().isNotEmpty()

    suspend fun send(text: String): Result<Unit> {
        val token = token(); val chat = chatId()
        if (token.isEmpty() || chat.isEmpty()) {
            return Result.failure(IllegalStateException("Add your bot token + chat id in Settings"))
        }
        return try {
            val body = json.encodeToString(SendMessage(chat, text))
            val res = httpPostJson(
                "https://api.telegram.org/bot$token/sendMessage",
                mapOf("content-type" to "application/json"),
                body,
            )
            val root = json.parseToJsonElement(res.body).jsonObject
            if (root["ok"]?.jsonPrimitive?.content == "true") {
                Result.success(Unit)
            } else {
                val desc = root["description"]?.jsonPrimitive?.content ?: "Telegram rejected the message"
                Result.failure(RuntimeException(desc))
            }
        } catch (e: Exception) {
            Result.failure(RuntimeException("Couldn't reach Telegram"))
        }
    }
}
