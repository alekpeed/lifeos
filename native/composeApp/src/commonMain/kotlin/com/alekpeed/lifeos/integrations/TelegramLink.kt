package com.alekpeed.lifeos.integrations

import com.alekpeed.lifeos.net.NetResponse
import com.alekpeed.lifeos.net.httpGet
import com.alekpeed.lifeos.net.httpPostJson
import com.alekpeed.lifeos.net.httpRequest
import com.alekpeed.lifeos.sync.SupabaseAuth
import com.alekpeed.lifeos.sync.SupabaseConfig
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.random.Random

// Two-way Telegram — the client half of linking a chat to the account. The bot itself
// is the telegram-webhook Edge Function, which reads incoming messages, files ideas and
// tasks, and answers questions; nothing here listens. All this does is mint the one-time
// token that lets the bot know which account a chat belongs to, then hand back the t.me
// link that starts the conversation.
//
// Same tables and same token flow the web app uses, so a chat linked from either side is
// linked for both. Requires being signed in — the link is per account, not per device.
object TelegramLink {

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private const val LINKS = "${SupabaseConfig.URL}/rest/v1/telegram_links"
    private const val TOKENS = "${SupabaseConfig.URL}/rest/v1/telegram_link_tokens"

    data class State(val signedIn: Boolean, val linked: Boolean)

    private fun headers(): Map<String, String> = buildMap {
        put("apikey", SupabaseConfig.ANON_KEY)
        SupabaseAuth.accessToken()?.let { put("Authorization", "Bearer $it") }
        put("content-type", "application/json")
    }

    private suspend fun authed(call: suspend (Map<String, String>) -> NetResponse): NetResponse {
        var res = call(headers())
        if (res.status == 401 && SupabaseAuth.refresh()) res = call(headers())
        return res
    }

    // Is a chat linked to this account right now? Silent on failure — offline or signed
    // out both mean "not linked" as far as the screen is concerned.
    suspend fun state(): State {
        val uid = SupabaseAuth.userId() ?: return State(signedIn = false, linked = false)
        if (!SupabaseAuth.isSignedIn()) return State(signedIn = false, linked = false)
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
        val uid = SupabaseAuth.userId()
        if (uid == null || !SupabaseAuth.isSignedIn()) {
            return Result.failure(IllegalStateException("Sign in to your account first — the link is per account."))
        }

        val username = runCatching {
            val me = httpGet("https://api.telegram.org/bot$botToken/getMe")
            val root = json.parseToJsonElement(me.body).jsonObject
            if (root["ok"]?.jsonPrimitive?.content != "true") null
            else root["result"]?.jsonObject?.get("username")?.jsonPrimitive?.content
        }.getOrNull()
        if (username.isNullOrBlank()) {
            return Result.failure(RuntimeException("Couldn't read the bot — double-check the bot token above."))
        }

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
        val uid = SupabaseAuth.userId() ?: return Result.success(Unit)
        val res = runCatching {
            authed { h -> httpRequest("DELETE", "$LINKS?user_id=eq.$uid", h, null) }
        }.getOrNull()
        return if (res != null && res.ok) Result.success(Unit)
        else Result.failure(RuntimeException("Couldn't disconnect"))
    }
}
