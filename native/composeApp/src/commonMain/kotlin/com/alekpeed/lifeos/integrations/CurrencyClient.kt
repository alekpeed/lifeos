package com.alekpeed.lifeos.integrations

import com.alekpeed.lifeos.net.httpGet
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

// open.er-api.com — keyless live exchange rates, base USD (~160 currencies). Rates
// are fetched once and cached in memory; conversion is done locally against the
// USD base. A short common-currency list is offered for the pickers.
object CurrencyClient {

    private val json = Json { ignoreUnknownKeys = true }
    private var ratesUsd: Map<String, Double> = emptyMap()

    val common = listOf("USD", "EUR", "GBP", "JPY", "CAD", "AUD", "CHF", "CNY", "INR", "BRL", "MXN", "KRW")

    suspend fun ensureRates(): Boolean {
        if (ratesUsd.isNotEmpty()) return true
        return try {
            val res = httpGet("https://open.er-api.com/v6/latest/USD")
            if (!res.ok) return false
            val root = json.parseToJsonElement(res.body).jsonObject
            if (root["result"]?.jsonPrimitive?.content != "success") return false
            val rates = root["rates"]?.jsonObject ?: return false
            ratesUsd = rates.mapValues { it.value.jsonPrimitive.content.toDoubleOrNull() ?: 0.0 }
            ratesUsd.isNotEmpty()
        } catch (e: Exception) {
            false
        }
    }

    // Convert an amount between two currencies using the USD-based rates. Returns
    // null if rates aren't loaded or a currency is unknown.
    fun convert(amount: Double, from: String, to: String): Double? {
        val rFrom = ratesUsd[from.uppercase()] ?: return null
        val rTo = ratesUsd[to.uppercase()] ?: return null
        if (rFrom == 0.0) return null
        // amount(from) → USD → to
        return amount / rFrom * rTo
    }

    fun hasRates(): Boolean = ratesUsd.isNotEmpty()
}
