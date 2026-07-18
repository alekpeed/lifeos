package com.alekpeed.lifeos.places

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.alekpeed.lifeos.data.parseDateOrNull
import com.alekpeed.lifeos.data.today
import com.alekpeed.lifeos.integrations.WeatherClient
import com.alekpeed.lifeos.platform.Native
import com.alekpeed.lifeos.platform.deleteBlob
import com.alekpeed.lifeos.platform.loadBlobImage
import com.alekpeed.lifeos.platform.saveBlob
import com.alekpeed.lifeos.ui.DateField
import com.alekpeed.lifeos.ui.SaveToast
import com.alekpeed.lifeos.ui.usDate
import kotlinx.coroutines.launch

private val DANGER = Color(0xFFD64545)
private val STAR_ON = Color(0xFFE0A63C)

@Composable
fun PlacesScreen() {
    var data by remember { mutableStateOf(loadPlaces()) }
    var counter by remember {
        mutableStateOf(
            maxOf(data.places.maxOfOrNull { it.id } ?: 0L, data.bucket.maxOfOrNull { it.id } ?: 0L),
        )
    }
    fun freshId(): Long { counter += 1; return counter }
    fun save(d: PlacesData) { data = d; savePlaces(d); SaveToast.show() }

    var tab by remember { mutableStateOf("visited") }
    var selected by remember { mutableStateOf<Long?>(null) }
    // null = not checked yet; empty = checked, nothing nearby; non-empty = results.
    var nearby by remember { mutableStateOf<List<NearbyNudge>?>(null) }

    Column(Modifier.fillMaxSize().padding(20.dp)) {
        Text("Places", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("visited" to "Visited", "wantToGo" to "Want to go", "map" to "Map", "bucket" to "Bucket list").forEach { (v, lbl) ->
                FilterChip(selected = tab == v, onClick = { tab = v; selected = null }, label = { Text(lbl) })
            }
        }

        if (tab == "map") {
            Spacer(Modifier.height(12.dp))
            val pins = mapPins(data.places)
            if (pins.isEmpty()) {
                Text(
                    "No places have coordinates yet. Open a place and tap “Find on map” (or “Use my location”) to pin it.",
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                // Tapping a pin jumps to that place's list with its editor open.
                WorldMapView(pins) { id ->
                    val place = data.places.firstOrNull { it.id == id } ?: return@WorldMapView
                    tab = if (place.listType == "wantToGo") "wantToGo" else "visited"
                    selected = id
                }
            }
            return@Column
        }

        if (tab != "bucket" && Native.supportsLocation) {
            Spacer(Modifier.height(10.dp))
            OutlinedButton(onClick = {
                Native.getCurrentLocation { lat, lng ->
                    if (lat != null && lng != null) nearby = findNearbyNudges(data.places, lat, lng)
                }
            }) { Text("📍 Check nearby places") }
            nearby?.let { NearbyBanner(it) { nearby = null } }
        }
        Spacer(Modifier.height(12.dp))

        if (tab == "bucket") {
            BucketList(data, ::save, ::freshId)
        } else {
            PlaceList(data, ::save, ::freshId, tab, selected) { selected = if (selected == it) null else it }
        }
    }
}

@Composable
private fun NearbyBanner(nudges: List<NearbyNudge>, onDismiss: () -> Unit) {
    Spacer(Modifier.height(8.dp))
    if (nudges.isEmpty()) {
        Muted("Nothing nearby right now.")
        return
    }
    Column {
        nudges.forEach { n ->
            Row(
                Modifier.fillMaxWidth().padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(n.place.name.ifBlank { "(untitled)" }, style = MaterialTheme.typography.bodyMedium)
                    Text(
                        "${n.reason} · ${n.distanceMeters.toInt()}m away",
                        style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        TextButton(onClick = onDismiss) { Text("Dismiss") }
    }
}

// ---------- Visited / Want-to-go ----------

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PlaceList(
    data: PlacesData,
    save: (PlacesData) -> Unit,
    freshId: () -> Long,
    listType: String,
    selected: Long?,
    onSelect: (Long) -> Unit,
) {
    var input by remember { mutableStateOf("") }
    val places = data.places.filter { (it.listType.ifBlank { "visited" }) == listType }

    Row(verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(
            value = input, onValueChange = { input = it }, modifier = Modifier.weight(1f), singleLine = true,
            placeholder = { Text(if (listType == "wantToGo") "New place to visit" else "New place") },
        )
        Spacer(Modifier.width(10.dp))
        Button(onClick = {
            val n = input.trim().replace("\n", " ")
            if (n.isNotEmpty()) { save(data.copy(places = data.places + Place(freshId(), n, listType))); input = "" }
        }) { Text("Add") }
    }
    Spacer(Modifier.height(12.dp))

    if (places.isEmpty()) { Muted("Nothing here yet."); return }
    LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items(places, key = { it.id }) { p ->
            Column {
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant).clickable { onSelect(p.id) }.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(p.name.ifBlank { "(untitled)" }, style = MaterialTheme.typography.bodyLarge)
                        val chips = buildList {
                            if (p.category.isNotBlank()) add(p.category)
                            if (p.rating > 0) add("★".repeat(p.rating))
                            if (p.revisit) add("Revisit")
                            if (p.visitDates.isNotEmpty()) add("${p.visitDates.size} visit${if (p.visitDates.size > 1) "s" else ""}")
                        }
                        if (chips.isNotEmpty()) {
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                chips.forEach { Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary) }
                            }
                        }
                    }
                }
                if (selected == p.id) PlaceDetail(data, save, p) { onSelect(p.id) }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PlaceDetail(data: PlacesData, save: (PlacesData) -> Unit, place: Place, onClose: () -> Unit) {
    fun patch(f: (Place) -> Place) = save(data.copy(places = data.places.map { if (it.id == place.id) f(it) else it }))
    var newNote by remember { mutableStateOf("") }
    var showSource by remember { mutableStateOf(false) }

    // Attach/replace the photo: save the new blob, drop the old one, point the
    // record at the new id.
    fun onAttach(b64: String?) {
        if (b64.isNullOrEmpty()) return
        val id = saveBlob(b64) ?: return
        deleteBlob(place.photoBlob)
        patch { it.copy(photoBlob = id) }
    }

    Panel {
        Label("Name")
        Field(place.name, "Name") { v -> patch { it.copy(name = v.replace("\n", " ")) } }
        Label("Category")
        Field(place.category, "bar, restaurant, trail…") { v -> patch { it.copy(category = v.replace("\n", " ")) } }
        Label("List")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf("visited" to "Visited", "wantToGo" to "Want to go").forEach { (v, lbl) ->
                FilterChip(selected = (place.listType.ifBlank { "visited" }) == v, onClick = { patch { it.copy(listType = v) } }, label = { Text(lbl) })
            }
        }
        Label("Address")
        Field(place.address, "Address") { v -> patch { it.copy(address = v.replace("\n", " ")) } }
        Row {
            Column(Modifier.weight(1f)) {
                Label("Latitude")
                Field(place.lat?.toString() ?: "", "lat") { v -> patch { it.copy(lat = v.toDoubleOrNull()) } }
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Label("Longitude")
                Field(place.lng?.toString() ?: "", "lng") { v -> patch { it.copy(lng = v.toDoubleOrNull()) } }
            }
        }
        run {
            val scope = rememberCoroutineScope()
            var geoBusy by remember { mutableStateOf(false) }
            var geoErr by remember { mutableStateOf<String?>(null) }
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (Native.supportsLocation) {
                    TextButton(onClick = {
                        Native.getCurrentLocation { lat, lng ->
                            if (lat != null && lng != null) patch { it.copy(lat = lat, lng = lng) }
                        }
                    }) { Text("Use my location") }
                }
                // Geocode the name/address (keyless Open-Meteo) so the place shows
                // on the Map tab without hand-typing coordinates.
                TextButton(
                    onClick = {
                        if (geoBusy) return@TextButton
                        geoBusy = true; geoErr = null
                        val query = place.address.ifBlank { place.name }
                        scope.launch {
                            WeatherClient.geocode(query)
                                .onSuccess { (lat, lng) -> patch { it.copy(lat = lat, lng = lng) } }
                                .onFailure { geoErr = it.message }
                            geoBusy = false
                        }
                    },
                    enabled = !geoBusy,
                ) { Text(if (geoBusy) "Finding…" else "🗺 Find on map") }
            }
            geoErr?.let {
                Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
            }
        }

        if ((place.listType.ifBlank { "visited" }) == "visited") {
            Label("Rating")
            Row {
                (1..5).forEach { i ->
                    Text(
                        if (i <= place.rating) "★" else "☆",
                        color = if (i <= place.rating) STAR_ON else MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.headlineSmall,
                        modifier = Modifier.clickable { patch { it.copy(rating = if (i == it.rating) 0 else i) } }.padding(end = 4.dp),
                    )
                }
            }
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = place.revisit, onCheckedChange = { c -> patch { it.copy(revisit = c) } })
            Text("Want to revisit", style = MaterialTheme.typography.bodyMedium)
        }

        if ((place.listType.ifBlank { "visited" }) == "visited") {
            Label("Visit dates")
            place.visitDates.sorted().forEach { d ->
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(usDate(d).ifBlank { d }, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                    TextButton(onClick = { patch { it.copy(visitDates = it.visitDates.filterNot { x -> x == d }) } }) { Text("×") }
                }
            }
            DateAdder { d -> patch { it.copy(visitDates = (it.visitDates + d).distinct().sorted()) } }
        }

        Label("Notes")
        Field(place.notes, "Memory / story notes", singleLine = false) { v -> patch { it.copy(notes = v) } }

        Label("Notes-to-self")
        place.notesToSelf.forEach { note ->
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("📝 $note", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                TextButton(onClick = { patch { it.copy(notesToSelf = it.notesToSelf.filterNot { x -> x == note }) } }) { Text("×") }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(newNote, { newNote = it }, modifier = Modifier.weight(1f), singleLine = true, placeholder = { Text("New note-to-self") })
            Spacer(Modifier.width(8.dp))
            Button(onClick = {
                val t = newNote.trim().replace("\n", " ")
                if (t.isNotEmpty()) { patch { it.copy(notesToSelf = it.notesToSelf + t) }; newNote = "" }
            }) { Text("Add") }
        }

        if (Native.supportsGeofence) {
            Spacer(Modifier.height(10.dp))
            OutlinedButton(onClick = {
                Native.armArrivalHere(place.name.ifBlank { "this place" })
            }) { Text("📍 Remind me when I'm back here") }
            Text(
                "Arms an arrival alert at your current location.",
                style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Label("Photo")
        val photo = remember(place.photoBlob) { loadBlobImage(place.photoBlob) }
        if (place.photoBlob.isNotBlank()) {
            if (photo != null) {
                Image(
                    bitmap = photo,
                    contentDescription = "Attached photo",
                    modifier = Modifier.fillMaxWidth().heightIn(max = 240.dp).clip(RoundedCornerShape(8.dp)),
                    contentScale = ContentScale.Fit,
                )
            } else {
                Text("Photo attached (no preview available).", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (Native.supportsCamera) TextButton(onClick = { showSource = true }) { Text("Replace") }
                TextButton(onClick = { deleteBlob(place.photoBlob); patch { it.copy(photoBlob = "") } }) { Text("Remove photo") }
            }
        } else if (Native.supportsCamera) {
            OutlinedButton(onClick = { showSource = true }) { Text("📷 Attach photo") }
        } else {
            Text("Photo attachments need a camera.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        if (showSource) {
            AlertDialog(
                onDismissRequest = { showSource = false },
                title = { Text("Attach a photo") },
                text = { Text("Take a new photo, or choose one from your library.") },
                confirmButton = {
                    TextButton(onClick = { showSource = false; Native.takePhoto { onAttach(it) } }) { Text("Take a photo") }
                },
                dismissButton = {
                    TextButton(onClick = { showSource = false; Native.capturePhoto { onAttach(it) } }) { Text("Choose from library") }
                },
            )
        }

        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onClose) { Text("Done") }
            Spacer(Modifier.weight(1f))
            TextButton(onClick = {
                deleteBlob(place.photoBlob)
                save(data.copy(places = data.places.filterNot { it.id == place.id })); onClose()
            }) { Text("Delete place", color = DANGER) }
        }
    }
}

// ---------- Bucket list ----------

@Composable
private fun BucketList(data: PlacesData, save: (PlacesData) -> Unit, freshId: () -> Long) {
    var input by remember { mutableStateOf("") }
    Row(verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(input, { input = it }, modifier = Modifier.weight(1f), singleLine = true, placeholder = { Text("New bucket-list goal") })
        Spacer(Modifier.width(10.dp))
        Button(onClick = {
            val t = input.trim().replace("\n", " ")
            if (t.isNotEmpty()) { save(data.copy(bucket = data.bucket + BucketItem(freshId(), t))); input = "" }
        }) { Text("Add") }
    }
    Spacer(Modifier.height(12.dp))

    if (data.bucket.isEmpty()) { Muted("No bucket-list goals yet."); return }
    val shown = data.bucket.sortedBy { it.done }
    LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        items(shown, key = { it.id }) { item ->
            fun patch(f: (BucketItem) -> BucketItem) = save(data.copy(bucket = data.bucket.map { if (it.id == item.id) f(it) else it }))
            Column(Modifier.padding(vertical = 2.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = item.done, onCheckedChange = { c -> patch { it.copy(done = c) } })
                    Text(
                        item.title, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f),
                        textDecoration = if (item.done) TextDecoration.LineThrough else null,
                    )
                    if (item.targetDate.isNotBlank()) {
                        Text(usDate(item.targetDate).ifBlank { item.targetDate }, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    TextButton(onClick = { save(data.copy(bucket = data.bucket.filterNot { it.id == item.id })) }) { Text("×") }
                }
                Row(Modifier.padding(start = 44.dp), verticalAlignment = Alignment.CenterVertically) {
                    AssistChip(onClick = { patch { it.copy(targetDate = today().toString()) } }, label = { Text("Target: today") })
                    if (item.targetDate.isNotBlank()) TextButton(onClick = { patch { it.copy(targetDate = "") } }) { Text("Clear date") }
                }
            }
        }
    }
}

// ---------- Shared bits ----------

@Composable
private fun DateAdder(onAdd: (String) -> Unit) {
    var date by remember { mutableStateOf("") }
    Column {
        DateField(date) { v -> date = v }
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            AssistChip(onClick = { onAdd(today().toString()) }, label = { Text("Today") })
            Spacer(Modifier.width(6.dp))
            Button(onClick = { if (parseDateOrNull(date) != null) { onAdd(date); date = "" } }) { Text("+") }
        }
    }
}

@Composable
private fun Panel(content: @Composable () -> Unit) {
    Column(
        Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 8.dp)
            .clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.surfaceVariant).padding(12.dp),
    ) { content() }
}

@Composable
private fun Label(text: String) {
    Spacer(Modifier.height(10.dp))
    Text(text, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Spacer(Modifier.height(4.dp))
}

@Composable
private fun Field(value: String, placeholder: String, singleLine: Boolean = true, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value, onValueChange = onChange, modifier = Modifier.fillMaxWidth(),
        singleLine = singleLine, placeholder = { Text(placeholder) },
    )
}

@Composable
private fun Muted(text: String) {
    Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}
