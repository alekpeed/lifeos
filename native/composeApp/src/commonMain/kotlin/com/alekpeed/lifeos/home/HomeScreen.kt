package com.alekpeed.lifeos.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.alekpeed.lifeos.ui.SaveToast
import kotlinx.coroutines.launch

// Home — one list of everything in the house, through the Home Assistant bridge.
//
// The screen has two states and says which one it is in. Without a hub it explains what
// has to exist before this can do anything, because "no devices found" would be a lie
// about somebody's house. With one, it is a list you act on: favourites at the top, the
// switchable things next, the readable ones after that.
@Composable
fun HomeScreen() {
    var cfg by remember { mutableStateOf(loadHomeConfig()) }
    var url by remember { mutableStateOf(cfg.baseUrl) }
    var token by remember { mutableStateOf(cfg.token) }
    var editing by remember { mutableStateOf(!cfg.configured) }

    var entities by remember { mutableStateOf<List<HomeEntity>>(emptyList()) }
    var favs by remember { mutableStateOf(favourites()) }
    var arrival by remember { mutableStateOf(arrivalScene()) }
    var loading by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("") }
    var filter by remember { mutableStateOf("all") }
    val scope = rememberCoroutineScope()

    suspend fun refresh() {
        if (!cfg.configured) return
        loading = true
        val res = homeStates(cfg)
        entities = res.value.orEmpty()
        message = res.error
        loading = false
    }

    LaunchedEffect(cfg) { refresh() }

    Column(Modifier.fillMaxSize().padding(20.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("Home", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
            if (cfg.configured) {
                TextButton(onClick = { scope.launch { refresh() } }, enabled = !loading) { Text("Refresh") }
                TextButton(onClick = { editing = !editing }) { Text(if (editing) "Done" else "Hub") }
            }
        }

        if (editing) {
            HubSetup(
                url = url, token = token,
                onUrl = { url = it }, onToken = { token = it },
                onSave = {
                    saveHomeConfig(url, token)
                    cfg = loadHomeConfig()
                    url = cfg.baseUrl
                    editing = !cfg.configured
                    SaveToast.show()
                },
                onTest = { scope.launch { message = homePing(HomeConfig(normalizeBaseUrl(url), token.trim())).ifEmpty { "The hub answered. Save it." } } },
            )
        }

        if (message.isNotBlank()) {
            Spacer(Modifier.height(8.dp))
            Text(message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
        }

        if (!cfg.configured) {
            Spacer(Modifier.height(14.dp))
            Text(
                "This module talks to one thing: a Home Assistant hub on your network. HA speaks " +
                    "Tuya, Bluetooth mesh, Zigbee and Matter to the actual gear, so everything you own " +
                    "arrives here as one list whatever radio it came with.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "What has to exist first, and none of it is in this app: the hub itself (a Pi, an old " +
                    "laptop, a mini PC that stays home), an ESP32 bridge if you want the BrMesh lights on " +
                    "the list, and a way in from outside — Tailscale or Nabu Casa — if you want it to work " +
                    "when you're not at home. Then paste the address and a long-lived token above.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            return@Column
        }

        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf("all" to "All", "favourites" to "Favourites", "lights" to "Lights", "run" to "Scenes").forEach { (v, l) ->
                FilterChip(selected = filter == v, onClick = { filter = v }, label = { Text(l) })
            }
        }
        Spacer(Modifier.height(6.dp))

        if (loading && entities.isEmpty()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.height(18.dp).width(18.dp))
                Spacer(Modifier.width(10.dp))
                Text("Asking the hub…", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            return@Column
        }

        val shown = ordered(entities, favs).filter {
            when (filter) {
                "favourites" -> it.entityId in favs
                "lights" -> it.domain == "light"
                "run" -> it.domain in RUNNABLE
                else -> true
            }
        }

        if (shown.isEmpty()) {
            Text(
                if (entities.isEmpty()) "The hub answered, but has nothing on it yet."
                else "Nothing in this filter.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            return@Column
        }

        Text(
            "${entities.count { it.domain in CONTROLLABLE }} switchable · ${entities.size} on the hub",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(6.dp))

        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            items(shown, key = { it.entityId }) { e ->
                EntityRow(
                    e = e,
                    favourite = e.entityId in favs,
                    onFavourite = { toggleFavourite(e.entityId); favs = favourites() },
                    onToggle = { on ->
                        // Optimistic, then re-read: a bulb takes a moment to answer, and a
                        // switch that snaps back before the hub replies reads as broken.
                        entities = entities.map { if (it.entityId == e.entityId) it.copy(state = if (on) "on" else "off") else it }
                        scope.launch {
                            val err = homeToggle(e.entityId, on, cfg)
                            if (err.isNotBlank()) message = err
                            refresh()
                        }
                    },
                    onBrightness = { pct ->
                        entities = entities.map { if (it.entityId == e.entityId) it.copy(brightness = pct) else it }
                        scope.launch {
                            val err = homeBrightness(e.entityId, pct, cfg)
                            if (err.isNotBlank()) message = err
                        }
                    },
                    onArrival = {
                        setArrivalScene(if (arrival == e.entityId) "" else e.entityId)
                        arrival = arrivalScene()
                    },
                    isArrival = arrival == e.entityId,
                )
            }
        }
    }
}

@Composable
private fun HubSetup(
    url: String,
    token: String,
    onUrl: (String) -> Unit,
    onToken: (String) -> Unit,
    onSave: () -> Unit,
    onTest: () -> Unit,
) {
    Spacer(Modifier.height(10.dp))
    OutlinedTextField(
        url, onUrl, modifier = Modifier.fillMaxWidth(), singleLine = true,
        label = { Text("Hub address") },
        placeholder = { Text("192.168.1.40:8123") },
    )
    Spacer(Modifier.height(8.dp))
    OutlinedTextField(
        token, onToken, modifier = Modifier.fillMaxWidth(), singleLine = true,
        label = { Text("Long-lived access token") },
        visualTransformation = PasswordVisualTransformation(),
    )
    Text(
        "Home Assistant → your profile → Security → Long-lived access tokens. The token stays on " +
            "this device and is left out of the backup export, like every other credential.",
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(Modifier.height(8.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Button(onClick = onSave) { Text("Save") }
        OutlinedButton(onClick = onTest) { Text("Test") }
    }
}

@Composable
private fun EntityRow(
    e: HomeEntity,
    favourite: Boolean,
    isArrival: Boolean,
    onFavourite: () -> Unit,
    onToggle: (Boolean) -> Unit,
    onBrightness: (Int) -> Unit,
    onArrival: () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                if (favourite) "★" else "☆",
                modifier = Modifier.clickable { onFavourite() }.padding(end = 8.dp),
                color = if (favourite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Column(Modifier.weight(1f).clickable { expanded = !expanded }) {
                Text(e.name, style = MaterialTheme.typography.bodyLarge)
                Text(
                    buildList {
                        add(e.domain)
                        // A sensor's whole content is its reading.
                        if (e.domain !in CONTROLLABLE && e.domain !in RUNNABLE) {
                            add(listOf(e.state, e.unit).filter { it.isNotBlank() }.joinToString(" "))
                        }
                        if (e.unavailable) add("not answering")
                        if (isArrival) add("runs on arrival")
                    }.filter { it.isNotBlank() }.joinToString(" · "),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (e.unavailable) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            when {
                e.domain in RUNNABLE ->
                    OutlinedButton(onClick = { onToggle(true) }, enabled = !e.unavailable) { Text("Run") }
                e.domain in CONTROLLABLE ->
                    Switch(checked = e.on, enabled = !e.unavailable, onCheckedChange = { onToggle(it) })
            }
        }
        if (expanded) {
            if (e.domain == "light" && e.brightness != null && !e.unavailable) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("${e.brightness}%", style = MaterialTheme.typography.labelMedium, modifier = Modifier.width(48.dp))
                    Slider(
                        value = e.brightness.toFloat(),
                        onValueChange = { onBrightness(it.toInt()) },
                        valueRange = 0f..100f,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            if (e.domain in RUNNABLE) {
                TextButton(onClick = onArrival) {
                    Text(if (isArrival) "Don't run this on arrival" else "Run this when I get home")
                }
            }
            Text(
                e.entityId,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
