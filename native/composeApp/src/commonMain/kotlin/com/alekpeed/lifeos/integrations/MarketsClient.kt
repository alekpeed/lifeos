package com.alekpeed.lifeos.integrations

import com.alekpeed.lifeos.net.httpGet
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

data class CoinPrice(val id: String, val label: String, val usd: Double, val change24h: Double)

// Keyless market data. CoinGecko for a small crypto watchlist, Stooq for the DJIA
// index. Both fetch on demand; watchlist ids are user-editable via Storage.
object MarketsClient {

    private val json = Json { ignoreUnknownKeys = true }

    const val WATCH_KEY = "CryptoWatch"
    private const val DEFAULT_WATCH = "bitcoin,ethereum,solana"

    fun watchlist(read: (String) -> String?): List<String> =
        (read(WATCH_KEY)?.ifBlank { null } ?: DEFAULT_WATCH)
            .split(",").map { it.trim().lowercase() }.filter { it.isNotEmpty() }

    // Persist an edited watchlist (deduped, order preserved).
    fun saveWatchlist(ids: List<String>, write: (String, String) -> Unit) =
        write(WATCH_KEY, ids.map { it.trim().lowercase() }.filter { it.isNotEmpty() }.distinct().joinToString(","))

    suspend fun crypto(ids: List<String>): Result<List<CoinPrice>> {
        if (ids.isEmpty()) return Result.success(emptyList())
        return try {
            val url = "https://api.coingecko.com/api/v3/simple/price?ids=" +
                ids.joinToString("%2C") + "&vs_currencies=usd&include_24hr_change=true"
            val res = httpGet(url)
            if (!res.ok) return Result.failure(RuntimeException("Couldn't reach CoinGecko"))
            val root = json.parseToJsonElement(res.body).jsonObject
            val out = ids.mapNotNull { id ->
                val obj = root[id]?.jsonObject ?: return@mapNotNull null
                val usd = obj["usd"]?.jsonPrimitive?.content?.toDoubleOrNull() ?: return@mapNotNull null
                val chg = obj["usd_24h_change"]?.jsonPrimitive?.content?.toDoubleOrNull() ?: 0.0
                CoinPrice(id, label(id), usd, chg)
            }
            if (out.isEmpty()) Result.failure(RuntimeException("No prices found — check the coin ids"))
            else Result.success(out)
        } catch (e: Exception) {
            Result.failure(RuntimeException("Crypto lookup failed"))
        }
    }

    // Stooq returns a small CSV: header then one row; the DJIA close is column 6.
    suspend fun djia(): Result<Double> {
        return try {
            val res = httpGet("https://stooq.com/q/l/?s=%5Edji&f=sd2t2ohlcv&h&e=csv")
            if (!res.ok) return Result.failure(RuntimeException("Couldn't reach Stooq"))
            val lines = res.body.trim().lines()
            if (lines.size < 2) return Result.failure(RuntimeException("No index data"))
            val cols = lines[1].split(",")
            if (cols.size < 7) return Result.failure(RuntimeException("Unexpected data"))
            val close = cols[6].trim()
            val value = close.toDoubleOrNull()
                ?: return Result.failure(RuntimeException("Market closed / no quote"))
            Result.success(value)
        } catch (e: Exception) {
            Result.failure(RuntimeException("Index lookup failed"))
        }
    }

    private fun label(id: String): String =
        id.split('-').joinToString(" ") { p -> p.replaceFirstChar { it.uppercase() } }
}
