package com.alekpeed.lifeos.integrations

import com.alekpeed.lifeos.net.NetResponse
import com.alekpeed.lifeos.net.httpGet
import com.alekpeed.lifeos.net.httpPostJson
import com.alekpeed.lifeos.net.httpRequest
import com.alekpeed.lifeos.sync.FirebaseAuth
import com.alekpeed.lifeos.sync.FirebaseConfig
import kotlinx.serialization.json.put
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.random.Random

// Link a chat to the signed-in Firebase account through its Cloud Function webhook.
object TelegramLink {

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private const val LINKS = "${FirebaseConfig.URL}/rest/v1/telegram_links"
    private const val TOKENS = "${FirebaseConfig.URL}/rest/v1/telegram_link_tokens"

    data class State(val signedIn: Boolean, val linked: Boolean)

    private fun headers(): Map<String, String> = buildMap {
        // Firebase ID token is the only authorization credential.
        FirebaseAuth.accessToken()?.let { put("Authorization", "Bearer $it") }
        put("content-type", "application/json")
    }

    private suspend fun authed(call: suspend (Map<String, String>) -> NetResponse): NetResponse {
        var res = call(headers())
        if (res.status == 401 && FirebaseAuth.refresh()) res = call(headers())
        return res
    }

    // Is a chat linked to this account right now? Silent on failure — offline or signed
    // out both mean "not linked" as far as the screen is concerned.
    suspend fun state(): State {
        val uid = FirebaseAuth.userId() ?: return State(signedIn = false, linked = false)
        if (!FirebaseAuth.isSignedIn()) return State(signedIn = false, linked = false)
        val res = runCatching {
            authed { h -> httpGet("$LINKS?user_id=eq.$uid&select=telegram_chat_id", h) }
        }.getOrNull() ?: return State(signedIn = true, linked = false)
        if (!res.ok) return State(signedIn = true, linked = false)
        val linked = runCatching { json.parseToJsonElement(res.body).jsonArray.isNotEmpty() }.getOrElse { false }
        return State(signedIn = true, linked = linked)
    }

    // Telegram's /start payload allows only [A-Za-z0-9_-], so the token is plain hex.
    private fun freshToken(): String {
        val chars = "0123456789abcdef"
        return (1..32).map { chars[Random.nextInt(chars.length)] }.joinToString("")
    }

    // Mint a token, ask Telegram for the bot's @username, and build the link that opens
    // the chat with that token attached. The bot claims the token on /start.
    suspend fun createDeepLink(): Result<String> {
        val botToken = TelegramClient.token()
        if (botToken.isEmpty()) {
            return Result.failure(IllegalStateException("Add your bot token above first (from @BotFather)."))
        }
        val uid = FirebaseAuth.userId()
        if (uid == null || !FirebaseAuth.isSignedIn()) {
            return Result.failure(IllegalStateException("Sign in to your account first — the link is per account."))
        }

        val configured = com.alekpeed.lifeos.sync.FirebaseSync.request("POST", "/telegram/configure",
            kotlinx.serialization.json.buildJsonObject { put("token", botToken) }.toString())
        if (!configured.ok) return Result.failure(RuntimeException("Couldn't configure the Firebase Telegram webhook (HTTP ${configured.status})"))
        val username = json.parseToJsonElement(configured.body).jsonObject.getValue("username").jsonPrimitive.content

        val token = freshToken()
        val res = runCatching {
            authed { h -> httpPostJson(TOKENS, h, """{"token":"$token","user_id":"$uid"}""") }
        }.getOrNull()
        if (res == null || !res.ok) {
            return Result.failure(RuntimeException("Couldn't create the link (HTTP ${res?.status ?: 0})"))
        }
        return Result.success("https://t.me/$username?start=$token")
    }

    suspend fun unlink(): Result<Unit> {
        val uid = FirebaseAuth.userId() ?: return Result.success(Unit)
        val res = runCatching {
            authed { h -> httpRequest("DELETE", "$LINKS?user_id=eq.$uid", h, null) }
        }.getOrNull()
        return if (res != null && res.ok) Result.success(Unit)
        else Result.failure(RuntimeException("Couldn't disconnect"))
    }
}
