package com.alekpeed.lifeos.integrations

import com.alekpeed.lifeos.net.httpGet
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

data class WeatherNow(
    val place: String,
    val tempF: Int,
    val highF: Int,
    val lowF: Int,
    val description: String,
)

// Open-Meteo — keyless. Two hops: geocode a typed city name to coordinates, then
// pull current conditions + today's high/low. No account, no location permission,
// so it works identically on Android and desktop.
object WeatherClient {

    private val json = Json { ignoreUnknownKeys = true }

    suspend fun forCity(city: String): Result<WeatherNow> {
        val q = city.trim()
        if (q.isEmpty()) return Result.failure(IllegalArgumentException("Enter a city"))
        return try {
            val geoUrl = "https://geocoding-api.open-meteo.com/v1/search?name=" +
                q.encodeUrl() + "&count=1&language=en&format=json"
            val geoRes = httpGet(geoUrl)
            if (!geoRes.ok) return Result.failure(RuntimeException("Couldn't reach the weather service"))
            val results = json.parseToJsonElement(geoRes.body).jsonObject["results"]?.jsonArray
            if (results == null || results.isEmpty()) return Result.failure(RuntimeException("No place called “$q”"))
            val first = results.first().jsonObject
            val lat = first["latitude"]!!.jsonPrimitive.content
            val lon = first["longitude"]!!.jsonPrimitive.content
            val name = first["name"]?.jsonPrimitive?.content ?: q
            val country = first["country_code"]?.jsonPrimitive?.content ?: ""
            val place = if (country.isNotBlank()) "$name, $country" else name

            val wxUrl = "https://api.open-meteo.com/v1/forecast?latitude=$lat&longitude=$lon" +
                "&current=temperature_2m,weather_code&daily=temperature_2m_max,temperature_2m_min" +
                "&temperature_unit=fahrenheit&timezone=auto"
            val wxRes = httpGet(wxUrl)
            if (!wxRes.ok) return Result.failure(RuntimeException("Couldn't reach the weather service"))
            val root = json.parseToJsonElement(wxRes.body).jsonObject
            val current = root["current"]!!.jsonObject
            val daily = root["daily"]!!.jsonObject
            val temp = current["temperature_2m"]!!.jsonPrimitive.content.toFloat().roundToIntCompat()
            val code = current["weather_code"]!!.jsonPrimitive.content.toIntOrNull() ?: -1
            val hi = daily["temperature_2m_max"]!!.jsonArray.first().jsonPrimitive.content.toFloat().roundToIntCompat()
            val lo = daily["temperature_2m_min"]!!.jsonArray.first().jsonPrimitive.content.toFloat().roundToIntCompat()
            Result.success(WeatherNow(place, temp, hi, lo, describe(code)))
        } catch (e: Exception) {
            Result.failure(RuntimeException("Weather lookup failed"))
        }
    }

    // WMO weather-interpretation codes → a short human label.
    private fun describe(code: Int): String = when (code) {
        0 -> "Clear"
        1, 2 -> "Partly cloudy"
        3 -> "Overcast"
        45, 48 -> "Fog"
        51, 53, 55 -> "Drizzle"
        56, 57 -> "Freezing drizzle"
        61, 63, 65 -> "Rain"
        66, 67 -> "Freezing rain"
        71, 73, 75 -> "Snow"
        77 -> "Snow grains"
        80, 81, 82 -> "Rain showers"
        85, 86 -> "Snow showers"
        95 -> "Thunderstorm"
        96, 99 -> "Thunderstorm w/ hail"
        else -> "—"
    }
}

private fun Float.roundToIntCompat(): Int = kotlin.math.round(this).toInt()

// Minimal URL-encoding for a query value (spaces, common punctuation). Enough for
// city names; avoids pulling a URL library into commonMain.
private fun String.encodeUrl(): String = buildString {
    for (c in this@encodeUrl) {
        when {
            c.isLetterOrDigit() || c == '-' || c == '_' || c == '.' || c == '~' -> append(c)
            c == ' ' -> append("%20")
            else -> for (b in c.toString().encodeToByteArray()) {
                append('%'); append(((b.toInt() and 0xFF)).toString(16).uppercase().padStart(2, '0'))
            }
        }
    }
}
