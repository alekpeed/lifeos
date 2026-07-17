package com.alekpeed.lifeos.system

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.alekpeed.lifeos.Storage
import com.alekpeed.lifeos.integrations.CoinPrice
import com.alekpeed.lifeos.integrations.CurrencyClient
import com.alekpeed.lifeos.integrations.MarketsClient
import com.alekpeed.lifeos.integrations.WeatherClient
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

// Tools — a home for the keyless live-data utilities. Each card fetches on demand
// (no background polling), so nothing hits the network until you ask it to.
@Composable
fun ToolsScreen() {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("Tools", style = MaterialTheme.typography.headlineMedium)
        Text(
            "Weather, currency, and markets fetch live (keyless, on tap); the unit converter and timezones are offline.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        WeatherCard()
        CurrencyCard()
        MarketsCard()
        UnitConverterCard()
        TimezoneCard()
    }
}

private data class UnitDef(val name: String, val toBase: Double)

private val UNIT_CATS = linkedMapOf(
    "Length" to listOf(UnitDef("m", 1.0), UnitDef("km", 1000.0), UnitDef("cm", 0.01), UnitDef("mi", 1609.344), UnitDef("ft", 0.3048), UnitDef("in", 0.0254), UnitDef("yd", 0.9144)),
    "Weight" to listOf(UnitDef("g", 1.0), UnitDef("kg", 1000.0), UnitDef("lb", 453.592), UnitDef("oz", 28.3495)),
    "Volume" to listOf(UnitDef("L", 1.0), UnitDef("mL", 0.001), UnitDef("gal", 3.78541), UnitDef("cup", 0.236588), UnitDef("fl oz", 0.0295735)),
)
private val TEMP_UNITS = listOf("C", "F", "K")

private fun convertUnit(cat: String, amt: Double?, from: String, to: String): String {
    if (amt == null) return ""
    if (cat == "Temperature") {
        val c = when (from) { "F" -> (amt - 32) * 5 / 9; "K" -> amt - 273.15; else -> amt }
        val out = when (to) { "F" -> c * 9 / 5 + 32; "K" -> c + 273.15; else -> c }
        return "${trim2(amt)}°$from  =  ${trim2(out)}°$to"
    }
    val defs = UNIT_CATS[cat] ?: return ""
    val f = defs.firstOrNull { it.name == from }?.toBase ?: return ""
    val t = defs.firstOrNull { it.name == to }?.toBase ?: return ""
    return "${trim2(amt)} $from  =  ${trim2(amt * f / t)} $to"
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun UnitConverterCard() {
    var cat by remember { mutableStateOf("Length") }
    var amount by remember { mutableStateOf("1") }
    val units = if (cat == "Temperature") TEMP_UNITS else (UNIT_CATS[cat] ?: emptyList()).map { it.name }
    var from by remember(cat) { mutableStateOf(units.first()) }
    var to by remember(cat) { mutableStateOf(units.getOrElse(1) { units.first() }) }
    val result = convertUnit(cat, amount.trim().toDoubleOrNull(), from, to)

    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text("Unit converter", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(10.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                (UNIT_CATS.keys + "Temperature").forEach { c ->
                    FilterChip(selected = cat == c, onClick = { cat = c }, label = { Text(c) })
                }
            }
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(value = amount, onValueChange = { amount = it }, modifier = Modifier.weight(1f), singleLine = true, label = { Text("Amount") })
                Spacer(Modifier.width(8.dp))
                UnitPicker(from, units) { from = it }
                Spacer(Modifier.width(6.dp))
                Text("→", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.width(6.dp))
                UnitPicker(to, units) { to = it }
            }
            if (result.isNotBlank()) {
                Spacer(Modifier.height(10.dp))
                Text(result, style = MaterialTheme.typography.bodyLarge)
            }
        }
    }
}

private val ZONES = listOf(
    "America/Los_Angeles", "America/Denver", "America/Chicago", "America/New_York",
    "Europe/London", "Europe/Paris", "Asia/Dubai", "Asia/Kolkata", "Asia/Tokyo", "Australia/Sydney",
)

private fun fmtClock(dt: LocalDateTime): String {
    val h = if (dt.hour % 12 == 0) 12 else dt.hour % 12
    return "$h:${dt.minute.toString().padStart(2, '0')} ${if (dt.hour < 12) "AM" else "PM"}"
}

@Composable
private fun TimezoneCard() {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text("Timezones", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(6.dp))
            val now = Clock.System.now()
            val local = TimeZone.currentSystemDefault()
            Text("Local (${local.id.substringAfterLast('/').replace('_', ' ')}): ${fmtClock(now.toLocalDateTime(local))}", style = MaterialTheme.typography.bodyLarge)
            Spacer(Modifier.height(6.dp))
            ZONES.forEach { z ->
                val tz = runCatching { TimeZone.of(z) }.getOrNull()
                if (tz != null && tz != local) {
                    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(z.substringAfterLast('/').replace('_', ' '), modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                        Text(fmtClock(now.toLocalDateTime(tz)), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

@Composable
private fun UnitPicker(value: String, options: List<String>, onPick: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(onClick = { open = true }) { Text(value) }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            options.forEach { o -> DropdownMenuItem(text = { Text(o) }, onClick = { onPick(o); open = false }) }
        }
    }
}

@Composable
private fun WeatherCard() {
    var city by remember { mutableStateOf(Storage.read("WeatherCity")?.ifBlank { null } ?: "") }
    var loading by remember { mutableStateOf(false) }
    var line by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    fun go() {
        val q = city.trim()
        if (q.isEmpty() || loading) return
        Storage.write("WeatherCity", q)
        loading = true; line = null; error = null
        scope.launch {
            WeatherClient.forCity(q)
                .onSuccess { w -> line = "${w.place} · ${w.tempF}°F ${w.description} · H ${w.highF}° / L ${w.lowF}°" }
                .onFailure { error = it.message ?: "Weather lookup failed" }
            loading = false
        }
    }

    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text("Weather", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = city,
                    onValueChange = { city = it; error = null },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    placeholder = { Text("City, e.g. Austin") },
                )
                Spacer(Modifier.width(10.dp))
                Button(onClick = { go() }, enabled = !loading) { Text("Get") }
            }
            Spacer(Modifier.height(10.dp))
            when {
                loading -> LoadingRow("Checking the sky…")
                error != null -> Text(error!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
                line != null -> Text(line!!, style = MaterialTheme.typography.bodyLarge)
                else -> Text("Enter a city and tap Get.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun CurrencyCard() {
    var amount by remember { mutableStateOf("100") }
    var from by remember { mutableStateOf("USD") }
    var to by remember { mutableStateOf("EUR") }
    var loading by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    fun convert() {
        val amt = amount.trim().toDoubleOrNull()
        if (amt == null) { error = "Enter a number"; result = null; return }
        if (loading) return
        loading = true; error = null; result = null
        scope.launch {
            val ok = CurrencyClient.ensureRates()
            if (!ok) { error = "Couldn't fetch rates"; loading = false; return@launch }
            val out = CurrencyClient.convert(amt, from, to)
            if (out == null) error = "Unknown currency"
            else result = "${trim2(amt)} $from  =  ${trim2(out)} $to"
            loading = false
        }
    }

    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text("Currency converter", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = amount,
                    onValueChange = { amount = it; error = null },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    label = { Text("Amount") },
                )
                Spacer(Modifier.width(10.dp))
                CurrencyPicker(from) { from = it }
                Spacer(Modifier.width(6.dp))
                Text("→", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.width(6.dp))
                CurrencyPicker(to) { to = it }
            }
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Button(onClick = { convert() }, enabled = !loading) { Text("Convert") }
                Spacer(Modifier.width(12.dp))
                when {
                    loading -> LoadingRow("Fetching rates…")
                    error != null -> Text(error!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
                    result != null -> Text(result!!, style = MaterialTheme.typography.bodyLarge)
                    else -> {}
                }
            }
        }
    }
}

@Composable
private fun MarketsCard() {
    var watch by remember { mutableStateOf(Storage.read("CryptoWatch")?.ifBlank { null } ?: "bitcoin,ethereum,solana") }
    var loading by remember { mutableStateOf(false) }
    var coins by remember { mutableStateOf<List<CoinPrice>>(emptyList()) }
    var djia by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    fun refresh() {
        if (loading) return
        Storage.write("CryptoWatch", watch.trim())
        loading = true; error = null
        scope.launch {
            val ids = MarketsClient.watchlist { Storage.read(it) }
            MarketsClient.crypto(ids)
                .onSuccess { coins = it }
                .onFailure { error = it.message }
            MarketsClient.djia()
                .onSuccess { djia = "DJIA  ${money(it)}" }
                .onFailure { if (djia == null) djia = "DJIA  —" }
            loading = false
        }
    }

    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text("Markets", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                value = watch,
                onValueChange = { watch = it; error = null },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text("Crypto watchlist (CoinGecko ids)") },
            )
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Button(onClick = { refresh() }, enabled = !loading) { Text("Refresh") }
                Spacer(Modifier.width(12.dp))
                if (loading) LoadingRow("Fetching quotes…")
            }
            if (error != null) {
                Spacer(Modifier.height(8.dp))
                Text(error!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
            }
            if (coins.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                coins.forEach { c ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(c.label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                        Text("$${money(c.usd)}", style = MaterialTheme.typography.bodyLarge)
                        Spacer(Modifier.width(10.dp))
                        val up = c.change24h >= 0
                        Text(
                            (if (up) "+" else "") + trim2(c.change24h) + "%",
                            color = if (up) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            }
            if (djia != null) {
                Spacer(Modifier.height(10.dp))
                Text(djia!!, style = MaterialTheme.typography.bodyLarge)
            }
        }
    }
}

@Composable
private fun CurrencyPicker(value: String, onPick: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(onClick = { open = true }) { Text(value) }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            CurrencyClient.common.forEach { code ->
                DropdownMenuItem(text = { Text(code) }, onClick = { onPick(code); open = false })
            }
        }
    }
}

@Composable
private fun LoadingRow(label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.height(18.dp).width(18.dp))
        Spacer(Modifier.width(10.dp))
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private fun trim2(v: Double): String {
    val r = kotlin.math.round(v * 100) / 100.0
    val s = r.toString()
    return if (s.endsWith(".0")) s.dropLast(2) else s
}

// Group the integer part with commas; keep 2 decimals for small values, none for
// large index/price figures. Pure Kotlin — no platform number formatter needed.
private fun money(v: Double): String {
    val decimals = if (v >= 1000) 0 else 2
    val rounded = if (decimals == 0) kotlin.math.round(v) else kotlin.math.round(v * 100) / 100.0
    val whole = rounded.toLong()
    val grouped = whole.toString().reversed().chunked(3).joinToString(",").reversed()
    if (decimals == 0) return grouped
    val frac = kotlin.math.round((rounded - whole) * 100).toLong().toString().padStart(2, '0')
    return "$grouped.$frac"
}
