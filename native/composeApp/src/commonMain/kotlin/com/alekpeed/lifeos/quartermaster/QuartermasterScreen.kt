package com.alekpeed.lifeos.quartermaster

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.alekpeed.lifeos.ai.AiClient
import com.alekpeed.lifeos.data.today
import com.alekpeed.lifeos.platform.Native
import com.alekpeed.lifeos.ui.SaveToast
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive

private val DANGER = Color(0xFFD64545)

private const val CATALOG_SYSTEM =
    "You look at a photo of a shelf, pantry, garage, closet, or drawer and list the distinct " +
        "physical items you can identify. Respond with ONLY a JSON array of short item-name strings " +
        "— e.g. [\"Cordless drill\",\"Box of nails\",\"Paint roller\"] — and nothing else: no prose, " +
        "no markdown, no code fence. One entry per distinct item. Do not include quantities, do not " +
        "guess at items you cannot actually see, and keep each name short."

private val catalogJson = Json { ignoreUnknownKeys = true }

// Pull the JSON array of item names out of the model's reply (it may wrap it in
// prose despite instructions). Returns the distinct, non-blank names.
private fun parseCatalog(raw: String): List<String> {
    val start = raw.indexOf('[')
    val end = raw.lastIndexOf(']')
    if (start < 0 || end <= start) return emptyList()
    val arr = try {
        catalogJson.parseToJsonElement(raw.substring(start, end + 1)).jsonArray
    } catch (e: Exception) {
        return emptyList()
    }
    return arr.mapNotNull { el ->
        try { el.jsonPrimitive.content.trim().replace("\n", " ").takeIf { it.isNotBlank() } } catch (e: Exception) { null }
    }.distinct()
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun QuartermasterScreen() {
    var data by remember { mutableStateOf(loadInventory()) }
    var counter by remember { mutableStateOf(data.items.maxOfOrNull { it.id } ?: 0L) }
    fun freshId(): Long { counter += 1; return counter }
    fun save(d: QuartermasterData) { data = d; saveInventory(d); SaveToast.show() }

    var name by remember { mutableStateOf("") }
    var loc by remember { mutableStateOf("") }
    var tags by remember { mutableStateOf("") }

    var cataloging by remember { mutableStateOf(false) }
    var catalogError by remember { mutableStateOf<String?>(null) }
    var draft by remember { mutableStateOf<List<String>?>(null) }
    var showSource by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    fun onPhoto(b64: String?) {
        when {
            b64 == null -> {}
            b64.isEmpty() -> catalogError = "Couldn't read that image — try another photo."
            else -> {
                catalogError = null
                cataloging = true
                scope.launch {
                    val reply = AiClient.askWithImage(CATALOG_SYSTEM, "List the distinct items in this photo.", b64, 1024)
                    cataloging = false
                    if (reply.isError) { catalogError = reply.text; return@launch }
                    val items = parseCatalog(reply.text)
                    if (items.isEmpty()) { catalogError = "Couldn't spot any items in that photo — add them by hand."; return@launch }
                    draft = items
                }
            }
        }
    }

    fun startCatalog() {
        if (!AiClient.hasKey()) { catalogError = "Add an AI key in Settings to catalog from a photo."; return }
        catalogError = null
        showSource = true
    }

    val lentOut = data.items.filter { it.lentTo.isNotBlank() }
    val onHand = data.items.filter { it.lentTo.isBlank() }

    Column(Modifier.fillMaxSize().padding(20.dp)) {
        Text("Quartermaster", style = MaterialTheme.typography.headlineMedium)
        Text("Your physical inventory, and who has what right now.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(12.dp))

        OutlinedTextField(name, { name = it }, modifier = Modifier.fillMaxWidth(), singleLine = true, placeholder = { Text("Item name") })
        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(loc, { loc = it }, modifier = Modifier.weight(1f), singleLine = true, placeholder = { Text("Location") })
            Spacer(Modifier.width(6.dp))
            OutlinedTextField(tags, { tags = it }, modifier = Modifier.weight(1f), singleLine = true, placeholder = { Text("Tags") })
            Spacer(Modifier.width(6.dp))
            Button(onClick = {
                val n = name.trim().replace("\n", " ")
                if (n.isNotEmpty()) {
                    val t = tags.split(",").map { it.trim() }.filter { it.isNotEmpty() }
                    save(data.copy(items = data.items + InventoryItem(freshId(), n, loc.trim(), t)))
                    name = ""; loc = ""; tags = ""
                }
            }) { Text("Add") }
        }

        if (Native.supportsCamera) {
            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = { startCatalog() }, enabled = !cataloging) {
                if (cataloging) {
                    CircularProgressIndicator(Modifier.height(16.dp).width(16.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp))
                    Text("Reading…")
                } else {
                    Text("📷 Catalog from a photo")
                }
            }
            catalogError?.let {
                Spacer(Modifier.height(6.dp))
                Text(it, style = MaterialTheme.typography.labelMedium, color = DANGER)
            }
        }

        if (showSource) {
            androidx.compose.material3.AlertDialog(
                onDismissRequest = { showSource = false },
                title = { Text("Catalog from a photo") },
                text = { Text("Take a new photo of a shelf/pantry/drawer, or choose one from your library.") },
                confirmButton = {
                    TextButton(onClick = { showSource = false; Native.takePhoto { onPhoto(it) } }) { Text("Take a photo") }
                },
                dismissButton = {
                    TextButton(onClick = { showSource = false; Native.capturePhoto { onPhoto(it) } }) { Text("Choose from library") }
                },
            )
        }

        draft?.let { items ->
            Spacer(Modifier.height(10.dp))
            CatalogReview(
                items = items,
                onEdit = { i, v -> draft = items.toMutableList().also { it[i] = v } },
                onRemove = { i -> draft = items.filterIndexed { idx, _ -> idx != i } },
                onCancel = { draft = null },
                onAddAll = {
                    val toAdd = items.map { it.trim() }.filter { it.isNotEmpty() }
                        .map { InventoryItem(freshId(), it) }
                    if (toAdd.isNotEmpty()) save(data.copy(items = data.items + toAdd))
                    draft = null
                },
            )
        }

        Spacer(Modifier.height(14.dp))

        LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (lentOut.isNotEmpty()) {
                item { SectionLabel("Lent out (${lentOut.size})") }
                items(lentOut, key = { it.id }) { ItemCard(data, ::save, it) }
            }
            item { SectionLabel("On hand (${onHand.size})") }
            if (onHand.isEmpty()) {
                item { Text("Nothing logged yet.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            } else {
                items(onHand, key = { it.id }) { ItemCard(data, ::save, it) }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ItemCard(data: QuartermasterData, save: (QuartermasterData) -> Unit, item: InventoryItem) {
    fun patch(f: (InventoryItem) -> InventoryItem) = save(data.copy(items = data.items.map { if (it.id == item.id) f(it) else it }))
    var lendTo by remember(item.id) { mutableStateOf("") }

    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant).padding(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(item.name.ifBlank { "(untitled)" }, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            TextButton(onClick = { save(data.copy(items = data.items.filterNot { it.id == item.id })) }) { Text("×") }
        }
        val chips = buildList {
            if (item.location.isNotBlank()) add("📍 ${item.location}")
            item.tags.forEach { add("#$it") }
        }
        if (chips.isNotEmpty()) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                chips.forEach { Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary) }
            }
        }
        Spacer(Modifier.height(6.dp))
        if (item.lentTo.isNotBlank()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Lent to ${item.lentTo} since ${item.lentSince}", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                OutlinedButton(onClick = { patch { it.copy(lentTo = "", lentSince = "") } }) { Text("Mark returned") }
            }
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(lendTo, { lendTo = it }, modifier = Modifier.weight(1f), singleLine = true, placeholder = { Text("Lend to…") })
                Spacer(Modifier.width(8.dp))
                OutlinedButton(onClick = {
                    val who = lendTo.trim().replace("\n", " ")
                    if (who.isNotEmpty()) { patch { it.copy(lentTo = who, lentSince = today().toString()) }; lendTo = "" }
                }) { Text("Lend it out") }
            }
        }
    }
}

// The editable review of items the AI drafted from a photo: tweak names, drop
// any it got wrong, then add them all as inventory items.
@Composable
private fun CatalogReview(
    items: List<String>,
    onEdit: (Int, String) -> Unit,
    onRemove: (Int) -> Unit,
    onCancel: () -> Unit,
    onAddAll: () -> Unit,
) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant).padding(12.dp),
    ) {
        Text("Found ${items.size} item${if (items.size == 1) "" else "s"} — review before adding", style = MaterialTheme.typography.labelLarge)
        Spacer(Modifier.height(8.dp))
        Column(Modifier.fillMaxWidth().heightIn(max = 280.dp).verticalScroll(rememberScrollState())) {
            items.forEachIndexed { i, value ->
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 3.dp)) {
                    OutlinedTextField(
                        value = value,
                        onValueChange = { onEdit(i, it.replace("\n", " ")) },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                    )
                    TextButton(onClick = { onRemove(i) }) { Text("×") }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onCancel) { Text("Cancel") }
            Spacer(Modifier.weight(1f))
            Button(onClick = onAddAll) { Text("Add ${items.count { it.isNotBlank() }}") }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(text, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
}
