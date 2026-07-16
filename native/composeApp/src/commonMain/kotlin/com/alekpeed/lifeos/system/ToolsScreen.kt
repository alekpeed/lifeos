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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import com.alekpeed.lifeos.integrations.CurrencyClient
import com.alekpeed.lifeos.integrations.WeatherClient
import kotlinx.coroutines.launch

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
            "Live utilities — weather and currency. All keyless; each fetches only when you tap.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        WeatherCard()
        CurrencyCard()
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
