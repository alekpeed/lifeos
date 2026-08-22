package com.alekpeed.lifeos.travel

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.alekpeed.lifeos.Nav
import com.alekpeed.lifeos.attach.AttachmentsSection
import com.alekpeed.lifeos.data.parseDateOrNull
import com.alekpeed.lifeos.data.relativeLabelOf
import com.alekpeed.lifeos.data.today
import com.alekpeed.lifeos.documents.loadDocuments
import com.alekpeed.lifeos.integrations.CurrencyClient
import com.alekpeed.lifeos.packing.allTemplates
import com.alekpeed.lifeos.packing.deleteTemplate
import com.alekpeed.lifeos.packing.saveAsTemplate
import com.alekpeed.lifeos.packing.PackItem
import com.alekpeed.lifeos.packing.PackingList
import com.alekpeed.lifeos.packing.loadPacking
import com.alekpeed.lifeos.packing.packedCount
import com.alekpeed.lifeos.packing.savePacking
import com.alekpeed.lifeos.people.loadContacts
import com.alekpeed.lifeos.photos.Album
import com.alekpeed.lifeos.photos.loadPhotos
import com.alekpeed.lifeos.photos.savePhotos
import com.alekpeed.lifeos.platform.Native
import com.alekpeed.lifeos.ui.DateField
import com.alekpeed.lifeos.ui.SaveToast

// Travel. A trip list, then everything about one trip behind tabs: what it is, what is
// booked, what to pack, what it costs, and which of your documents have to be valid for it.

private fun typeIcon(t: ReservationType) = when (t) {
    ReservationType.FLIGHT -> "✈️"
    ReservationType.LODGING -> "🏨"
    ReservationType.RAIL -> "🚆"
    ReservationType.BUS -> "🚌"
    ReservationType.CAR -> "🚗"
    ReservationType.FERRY -> "⛴️"
    ReservationType.TOUR -> "🧭"
    ReservationType.RESTAURANT -> "🍽️"
    ReservationType.EVENT -> "🎟️"
    ReservationType.OTHER -> "📌"
}

private fun statusLabel(s: TripStatus) = when (s) {
    TripStatus.PLANNING -> "Planning"
    TripStatus.BOOKED -> "Booked"
    TripStatus.ACTIVE -> "Under way"
    TripStatus.PAST -> "Past"
}

@Composable
fun TravelScreen() {
    var data by remember { mutableStateOf(loadTravel()) }
    var openTripId by remember { mutableStateOf<Long?>(null) }

    fun persist(next: TravelData) {
        data = next
        saveTravel(next)
    }

    val open = openTripId?.let { id -> data.trips.firstOrNull { it.id == id } }
    if (open != null) {
        TripDetail(
            trip = open,
            data = data,
            onChange = ::persist,
            onBack = { openTripId = null; data = loadTravel() },
        )
        return
    }

    val now = today()
    val sorted = remember(data) {
        data.trips.sortedWith(
            compareBy({ it.status(now).ordinal }, { it.startDate.ifBlank { "9999" } }),
        )
    }

    Column(Modifier.fillMaxWidth().padding(16.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("Trips", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            Button(onClick = {
                val id = (data.trips.maxOfOrNull { it.id } ?: 0L) + 1
                persist(data.copy(trips = data.trips + Trip(id = id, name = "New trip")))
                openTripId = id
            }) { Text("New trip") }
        }
        Spacer(Modifier.height(12.dp))

        if (sorted.isEmpty()) {
            Text(
                "No trips yet. A trip holds its reservations, packing lists, budget and the documents that have to be valid for it.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(sorted, key = { it.id }) { trip -> TripRow(trip, data, now) { openTripId = trip.id } }
            }
        }

        val loose = remember(data) { loadPacking().lists.filter { it.tripId == 0L } }
        if (loose.isNotEmpty()) {
            Spacer(Modifier.height(20.dp))
            Text("Packing lists with no trip", style = MaterialTheme.typography.titleMedium)
            Text(
                "From before packing moved in here. Open a trip to attach one.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(6.dp))
            loose.forEach { l ->
                Text(
                    "🧳  ${l.name} — ${l.packedCount()}/${l.items.size} packed",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(vertical = 3.dp),
                )
            }
        }
    }
}

@Composable
private fun TripRow(trip: Trip, data: TravelData, now: kotlinx.datetime.LocalDate, onOpen: () -> Unit) {
    val res = reservationsFor(data, trip.id)
    val away = trip.daysAway(now)
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClick = onOpen).padding(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                trip.name.ifBlank { "(untitled trip)" },
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
            )
            Text(statusLabel(trip.status(now)), style = MaterialTheme.typography.labelMedium)
        }
        val where = trip.destinations.filter { it.isNotBlank() }.joinToString(" · ")
        if (where.isNotBlank()) {
            Text(where, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        val bits = buildList {
            if (trip.startDate.isNotBlank()) add(relativeLabelOf(trip.startDate))
            if (away != null && away in 1..365) add("in $away day${if (away == 1) "" else "s"}")
            if (res.isNotEmpty()) add("${res.size} booking${if (res.size == 1) "" else "s"}")
        }
        if (bits.isNotEmpty()) {
            Text(bits.joinToString(" · "), style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun TripDetail(trip: Trip, data: TravelData, onChange: (TravelData) -> Unit, onBack: () -> Unit) {
    var tab by remember { mutableStateOf(0) }
    val tabs = listOf("Trip", "Bookings", "Packing", "Budget", "Documents", "Places & photos")

    fun patch(f: (Trip) -> Trip) {
        onChange(data.copy(trips = data.trips.map { if (it.id == trip.id) f(it) else it }))
    }

    Column(Modifier.fillMaxWidth().padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) { Text("‹ Trips") }
            Text(
                trip.name.ifBlank { "(untitled trip)" },
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.weight(1f).padding(start = 4.dp),
            )
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            tabs.forEachIndexed { i, name ->
                FilterChip(selected = tab == i, onClick = { tab = i }, label = { Text(name) })
            }
        }
        Spacer(Modifier.height(14.dp))

        when (tab) {
            0 -> TripOverview(trip, ::patch) {
                onChange(
                    data.copy(
                        trips = data.trips.filterNot { it.id == trip.id },
                        reservations = data.reservations.filterNot { it.tripId == trip.id },
                    ),
                )
                onBack()
            }
            1 -> ReservationsTab(trip, data, onChange)
            2 -> PackingTab(trip)
            3 -> BudgetTab(trip, data, ::patch)
            4 -> DocumentsTab(trip, ::patch)
            else -> PlacesPhotosTab(trip, ::patch)
        }
    }
}

@Composable
private fun TripOverview(trip: Trip, patch: ((Trip) -> Trip) -> Unit, onDelete: () -> Unit) {
    Column {
        OutlinedTextField(
            value = trip.name, onValueChange = { v -> patch { it.copy(name = v) } },
            label = { Text("Trip name") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = trip.destinations.joinToString(", "),
            onValueChange = { v -> patch { it.copy(destinations = v.split(",").map { d -> d.trim() }.filter { d -> d.isNotBlank() }) } },
            label = { Text("Destinations (comma separated)") }, modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(10.dp))
        Text("Leaves", style = MaterialTheme.typography.labelLarge)
        DateField(trip.startDate) { v -> patch { it.copy(startDate = v) } }
        Spacer(Modifier.height(8.dp))
        Text("Returns", style = MaterialTheme.typography.labelLarge)
        DateField(trip.endDate) { v -> patch { it.copy(endDate = v) } }

        Spacer(Modifier.height(12.dp))
        Text("Status", style = MaterialTheme.typography.labelLarge)
        Text(
            "Worked out from the dates unless you say otherwise.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(4.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            FilterChip(
                selected = trip.statusOverride == null,
                onClick = { patch { it.copy(statusOverride = null) } },
                label = { Text("Auto") },
            )
            TripStatus.entries.forEach { s ->
                FilterChip(
                    selected = trip.statusOverride == s,
                    onClick = { patch { it.copy(statusOverride = s) } },
                    label = { Text(statusLabel(s)) },
                )
            }
        }

        Spacer(Modifier.height(12.dp))
        TravelersPicker(trip, patch)

        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = trip.notes, onValueChange = { v -> patch { it.copy(notes = v) } },
            label = { Text("Notes") }, modifier = Modifier.fillMaxWidth().height(120.dp),
        )
        Spacer(Modifier.height(18.dp))
        TextButton(onClick = onDelete) {
            Text("Delete trip", color = MaterialTheme.colorScheme.error)
        }
    }
}

@Composable
private fun TravelersPicker(trip: Trip, patch: ((Trip) -> Trip) -> Unit) {
    val contacts = remember { loadContacts().contacts }
    var picking by remember { mutableStateOf(false) }
    Text("Travelling with", style = MaterialTheme.typography.labelLarge)
    val names = trip.travelerIds.mapNotNull { id -> contacts.firstOrNull { it.id == id }?.name }
    Text(
        if (names.isEmpty()) "Nobody added." else names.joinToString(", "),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    if (contacts.isNotEmpty()) {
        TextButton(onClick = { picking = true }) { Text("Choose from contacts") }
    }
    if (picking) {
        AlertDialog(
            onDismissRequest = { picking = false },
            confirmButton = { TextButton(onClick = { picking = false }) { Text("Done") } },
            title = { Text("Travelling with") },
            text = {
                LazyColumn(Modifier.height(320.dp)) {
                    items(contacts, key = { it.id }) { c ->
                        val on = c.id in trip.travelerIds
                        Row(
                            Modifier.fillMaxWidth().clickable {
                                patch { t ->
                                    t.copy(travelerIds = if (on) t.travelerIds - c.id else t.travelerIds + c.id)
                                }
                            }.padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Checkbox(checked = on, onCheckedChange = null)
                            Spacer(Modifier.width(8.dp))
                            Text(c.name.ifBlank { "(unnamed)" })
                        }
                    }
                }
            },
        )
    }
}

@Composable
private fun ReservationsTab(trip: Trip, data: TravelData, onChange: (TravelData) -> Unit) {
    val res = reservationsFor(data, trip.id)
    var openId by remember { mutableStateOf<Long?>(null) }

    fun patchRes(id: Long, f: (Reservation) -> Reservation) {
        onChange(data.copy(reservations = data.reservations.map { if (it.id == id) f(it) else it }))
    }

    val editing = openId?.let { id -> data.reservations.firstOrNull { it.id == id } }
    if (editing != null) {
        ReservationEditor(
            r = editing,
            onPatch = { f -> patchRes(editing.id, f) },
            onDelete = {
                onChange(data.copy(reservations = data.reservations.filterNot { it.id == editing.id }))
                openId = null
            },
            onBack = { openId = null },
        )
        return
    }

    Column {
        Button(onClick = {
            val id = (data.reservations.maxOfOrNull { it.id } ?: 0L) + 1
            onChange(data.copy(reservations = data.reservations + Reservation(id = id, tripId = trip.id)))
            openId = id
        }) { Text("Add a booking") }
        Spacer(Modifier.height(10.dp))

        if (res.isEmpty()) {
            Text(
                "Nothing booked yet. Flights, lodging, trains, cars, tours, tables — each with its confirmation number, cost and a link back to the booking.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                items(res, key = { it.id }) { r ->
                    Column(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .clickable { openId = r.id }.padding(10.dp),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(typeIcon(r.type))
                            Spacer(Modifier.width(8.dp))
                            Text(
                                r.provider.ifBlank { r.type.name.lowercase().replaceFirstChar { c -> c.uppercase() } },
                                style = MaterialTheme.typography.bodyLarge,
                                modifier = Modifier.weight(1f),
                            )
                            if (r.status == ReservationStatus.CANCELLED) {
                                Text("cancelled", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
                            } else if (!r.paid && r.cost > 0) {
                                Text("unpaid", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                        val bits = buildList {
                            if (r.startDateTime.isNotBlank()) add(relativeLabelOf(r.startDateTime))
                            if (r.confirmationNumber.isNotBlank()) add(r.confirmationNumber)
                            if (r.cost > 0) add("${r.currency} ${r.cost}")
                        }
                        if (bits.isNotEmpty()) {
                            Text(bits.joinToString(" · "), style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ReservationEditor(
    r: Reservation,
    onPatch: ((Reservation) -> Reservation) -> Unit,
    onDelete: () -> Unit,
    onBack: () -> Unit,
) {
    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onBack) { Text("‹ Bookings") }
                Text("Booking", style = MaterialTheme.typography.titleMedium)
            }
        }
        item {
            Text("Type", style = MaterialTheme.typography.labelLarge)
            Column {
                ReservationType.entries.chunked(4).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        row.forEach { t ->
                            FilterChip(
                                selected = r.type == t,
                                onClick = { onPatch { it.copy(type = t) } },
                                label = { Text("${typeIcon(t)} ${t.name.lowercase().replaceFirstChar { c -> c.uppercase() }}") },
                            )
                        }
                    }
                }
            }
        }
        item {
            OutlinedTextField(
                value = r.provider, onValueChange = { v -> onPatch { it.copy(provider = v) } },
                label = { Text("Provider (airline, hotel, company)") }, singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        item {
            OutlinedTextField(
                value = r.confirmationNumber, onValueChange = { v -> onPatch { it.copy(confirmationNumber = v) } },
                label = { Text("Confirmation number") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
            )
        }
        item {
            Text("Status", style = MaterialTheme.typography.labelLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                ReservationStatus.entries.forEach { s ->
                    FilterChip(
                        selected = r.status == s, onClick = { onPatch { it.copy(status = s) } },
                        label = { Text(s.name.lowercase().replaceFirstChar { c -> c.uppercase() }) },
                    )
                }
            }
        }
        item {
            Text("Starts", style = MaterialTheme.typography.labelLarge)
            // withTime is the point of the M-01(a) dependency: a flight departs at 07:25.
            DateField(r.startDateTime, withTime = true) { v -> onPatch { it.copy(startDateTime = v) } }
        }
        item {
            Text("Ends", style = MaterialTheme.typography.labelLarge)
            DateField(r.endDateTime, withTime = true) { v -> onPatch { it.copy(endDateTime = v) } }
        }
        item {
            OutlinedTextField(
                value = r.location, onValueChange = { v -> onPatch { it.copy(location = v) } },
                label = { Text("Address / location") }, modifier = Modifier.fillMaxWidth(),
            )
        }
        item {
            OutlinedTextField(
                value = r.externalLink, onValueChange = { v -> onPatch { it.copy(externalLink = v) } },
                label = { Text("Link to the booking") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
            )
            if (r.externalLink.isNotBlank()) {
                TextButton(onClick = { Native.openUrl(r.externalLink) }) { Text("Open link") }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = if (r.cost == 0.0) "" else r.cost.toString(),
                    onValueChange = { v -> onPatch { it.copy(cost = v.toDoubleOrNull() ?: 0.0) } },
                    label = { Text("Cost") }, singleLine = true, modifier = Modifier.weight(1f),
                )
                OutlinedTextField(
                    value = r.currency, onValueChange = { v -> onPatch { it.copy(currency = v.uppercase().take(3)) } },
                    label = { Text("Ccy") }, singleLine = true, modifier = Modifier.width(90.dp),
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = r.paid, onCheckedChange = { v -> onPatch { it.copy(paid = v) } })
                Text("Paid")
            }
        }
        item {
            Text("Contact", style = MaterialTheme.typography.labelLarge)
            OutlinedTextField(
                value = r.contactPhone, onValueChange = { v -> onPatch { it.copy(contactPhone = v) } },
                label = { Text("Phone") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
            )
            if (r.contactPhone.isNotBlank()) {
                // No dial capability exists in the platform layer; a tel: URL goes through
                // the same openUrl the rest of the app uses for outbound links.
                TextButton(onClick = { Native.openUrl("tel:" + r.contactPhone.filter { c -> c.isDigit() || c == '+' }) }) { Text("Call") }
            }
        }
        item {
            AttachmentsSection(
                attachments = r.attachments,
                onChange = { list -> onPatch { it.copy(attachments = list) } },
                label = "Boarding passes, tickets, vouchers",
            )
        }
        item {
            OutlinedTextField(
                value = r.notes, onValueChange = { v -> onPatch { it.copy(notes = v) } },
                label = { Text("Notes") }, modifier = Modifier.fillMaxWidth().height(100.dp),
            )
        }
        item {
            TextButton(onClick = onDelete) { Text("Delete booking", color = MaterialTheme.colorScheme.error) }
        }
    }
}

@Composable
private fun PackingTab(trip: Trip) {
    var lists by remember { mutableStateOf(loadPacking().lists) }
    var templates by remember { mutableStateOf(allTemplates()) }

    fun persist(next: List<PackingList>) {
        lists = next
        savePacking(loadPacking().copy(lists = next))
    }

    val mine = lists.filter { it.tripId == trip.id }
    val loose = lists.filter { it.tripId == 0L }

    Column {
        Button(onClick = {
            val id = (lists.maxOfOrNull { it.id } ?: 0L) + 1
            persist(lists + PackingList(id = id, name = "Packing for ${trip.name.ifBlank { "the trip" }}", tripId = trip.id))
        }) { Text("New list") }
        Spacer(Modifier.height(10.dp))

        if (mine.isEmpty()) {
            Text(
                "No packing list for this trip yet.",
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        mine.forEach { l ->
            Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("🧳  ${l.name}", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                    Text("${l.packedCount()}/${l.items.size}", style = MaterialTheme.typography.labelMedium)
                }

                // Templates and item entry come with the module, not just its data. Losing
                // them to the nav change would have left a list you could tick but never fill.
                Text(
                    "Start from a template",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                templates.chunked(3).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        row.forEach { tpl ->
                            OutlinedButton(onClick = {
                                var next = l.items
                                var nid = (next.maxOfOrNull { i -> i.id } ?: 0L) + 1_000_000L
                                tpl.groups.forEach { g ->
                                    g.items.forEach { nm -> nid += 1; next = next + PackItem(nid, nm, g.category) }
                                }
                                val fixed = next
                                persist(lists.map { pl -> if (pl.id == l.id) pl.copy(items = fixed) else pl })
                            }) { Text(tpl.name, style = MaterialTheme.typography.labelSmall) }
                        }
                    }
                }
                Spacer(Modifier.height(6.dp))
                AddItemRow { name, cat ->
                    val nid = (l.items.maxOfOrNull { i -> i.id } ?: 0L) + 1
                    persist(
                        lists.map { pl ->
                            if (pl.id == l.id) pl.copy(items = pl.items + PackItem(nid, name, cat)) else pl
                        },
                    )
                }
                Spacer(Modifier.height(6.dp))
                l.items.groupBy { it.category }.forEach { (cat, items) ->
                    Text(cat, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    items.forEach { item ->
                        Row(
                            Modifier.fillMaxWidth().clickable {
                                persist(
                                    lists.map { pl ->
                                        if (pl.id != l.id) pl
                                        else pl.copy(items = pl.items.map { i -> if (i.id == item.id) i.copy(packed = !i.packed) else i })
                                    },
                                )
                            },
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Checkbox(checked = item.packed, onCheckedChange = null)
                            Text(item.name)
                        }
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (l.items.isNotEmpty()) {
                        TextButton(onClick = {
                            saveAsTemplate(l, l.name)
                            templates = allTemplates()
                            SaveToast.show("Saved as a template")
                        }) { Text("Save as template") }
                    }
                    TextButton(onClick = { persist(lists.filterNot { it.id == l.id }) }) {
                        Text("Delete list", color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }

        val saved = templates.filter { it.id > 0 }
        if (saved.isNotEmpty()) {
            Spacer(Modifier.height(14.dp))
            Text("Your templates", style = MaterialTheme.typography.titleSmall)
            saved.forEach { tpl ->
                Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "${tpl.name} — ${tpl.groups.sumOf { g -> g.items.size }} items",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = { deleteTemplate(tpl.id); templates = allTemplates() }) {
                        Text("Delete", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }

        if (loose.isNotEmpty()) {
            Spacer(Modifier.height(14.dp))
            Text("Attach an existing list", style = MaterialTheme.typography.titleSmall)
            loose.forEach { l ->
                Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("${l.name} (${l.items.size} items)", modifier = Modifier.weight(1f))
                    OutlinedButton(onClick = {
                        persist(lists.map { if (it.id == l.id) it.copy(tripId = trip.id) else it })
                        SaveToast.show("Attached to this trip")
                    }) { Text("Attach") }
                }
            }
        }
    }
}

@Composable
private fun AddItemRow(onAdd: (String, String) -> Unit) {
    var name by remember { mutableStateOf("") }
    var cat by remember { mutableStateOf("") }
    Row(verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(
            name, { name = it }, modifier = Modifier.weight(2f), singleLine = true,
            placeholder = { Text("Add an item") },
        )
        Spacer(Modifier.width(6.dp))
        OutlinedTextField(
            cat, { cat = it }, modifier = Modifier.weight(1f), singleLine = true,
            placeholder = { Text("Category") },
        )
        Spacer(Modifier.width(6.dp))
        Button(onClick = {
            val n = name.trim().replace("\n", " ")
            if (n.isNotEmpty()) {
                onAdd(n, cat.trim().ifBlank { "Other" })
                name = ""; cat = ""
            }
        }) { Text("Add") }
    }
}

@Composable
private fun BudgetTab(trip: Trip, data: TravelData, patch: ((Trip) -> Trip) -> Unit) {
    val b = tripBudget(data, trip)
    Column {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = if (trip.budgetEstimate == 0.0) "" else trip.budgetEstimate.toString(),
                onValueChange = { v -> patch { it.copy(budgetEstimate = v.toDoubleOrNull() ?: 0.0) } },
                label = { Text("Budget") }, singleLine = true, modifier = Modifier.weight(1f),
            )
            OutlinedTextField(
                value = trip.currency, onValueChange = { v -> patch { it.copy(currency = v.uppercase().take(3)) } },
                label = { Text("Ccy") }, singleLine = true, modifier = Modifier.width(90.dp),
            )
        }
        Spacer(Modifier.height(14.dp))
        BudgetLine("Booked in ${b.currency}", money(b.currency, b.booked))
        if (b.hasForeign) {
            BudgetLine("Converted at today's rate", money(b.currency, b.convertedBooked))
            BudgetLine("Booked, all in", money(b.currency, b.totalBooked), bold = true)
        }
        BudgetLine("Paid", money(b.currency, b.totalPaid))
        BudgetLine("Still to pay", money(b.currency, b.outstanding))
        if (b.estimate > 0) {
            BudgetLine(
                if (b.overEstimate) "Over budget by" else "Left in budget",
                money(b.currency, if (b.overEstimate) b.totalBooked - b.estimate else b.estimate - b.totalBooked),
                warn = b.overEstimate,
            )
        }

        if (b.hasForeign) {
            Spacer(Modifier.height(12.dp))
            Text("Booked in other currencies", style = MaterialTheme.typography.labelLarge)
            b.foreign.toSortedMap().forEach { (code, amount) ->
                BudgetLine(code, money(code, amount))
            }
            Spacer(Modifier.height(6.dp))
            Text(
                "Converted through the Tools rates, which are today's. What you actually paid " +
                    "depended on the rate on the day, so the native amounts above are the real " +
                    "record and the converted total is an estimate.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (!CurrencyClient.hasRates()) {
                Text(
                    "No rates loaded yet — open Tools once to fetch them, and these convert.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
        if (b.unconvertible.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            Text(
                "No rate for ${b.unconvertible.sorted().joinToString(", ")} — those are counted nowhere above.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
}

// Two decimal places without a platform formatter.
private fun money(code: String, amount: Double): String {
    val cents = kotlin.math.round(amount * 100).toLong()
    val whole = cents / 100
    val frac = (if (cents < 0) -cents else cents) % 100
    return "$code $whole.${frac.toString().padStart(2, '0')}"
}

@Composable
private fun BudgetLine(label: String, value: String, warn: Boolean = false, bold: Boolean = false) {
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
        Text(
            label,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (bold) FontWeight.SemiBold else FontWeight.Normal,
        )
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = if (warn) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
        )
    }
}

// Places and photos: what the trip is near, what it turned out to include, and where the
// pictures go. Both halves are links — the photos live in Photos and the places in Places.
@Composable
private fun PlacesPhotosTab(trip: Trip, patch: ((Trip) -> Trip) -> Unit) {
    val places = remember(trip.id, trip.startDate, trip.endDate, trip.destinations) { tripPlaces(trip) }
    var photos by remember { mutableStateOf(loadPhotos()) }
    val album = trip.albumId?.let { id -> photos.albums.firstOrNull { it.id == id } }

    Column {
        Text("Photos", style = MaterialTheme.typography.titleSmall)
        if (album == null) {
            Text(
                "No album linked. The photos live in Photos — this just points at the album for this trip.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = {
                    val id = (photos.albums.maxOfOrNull { it.id } ?: 0L) + 1
                    val name = trip.name.ifBlank { "Trip" }
                    val next = photos.copy(albums = photos.albums + Album(id = id, name = name, description = "Trip photos"))
                    savePhotos(next)
                    photos = next
                    patch { it.copy(albumId = id) }
                }) { Text("Create an album") }
            }
            if (photos.albums.isNotEmpty()) {
                Text("or link one you already have:", style = MaterialTheme.typography.bodySmall)
                photos.albums.forEach { a ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("${a.name} (${a.captions.size})", modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                        OutlinedButton(onClick = { patch { it.copy(albumId = a.id) } }) { Text("Link") }
                    }
                }
            }
        } else {
            Text(
                "📷  ${album.name} — ${album.captions.size} photo${if (album.captions.size == 1) "" else "s"}",
                style = MaterialTheme.typography.bodyMedium,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { Nav.open("photos") }) { Text("Open in Photos") }
                TextButton(onClick = { patch { it.copy(albumId = null) } }) { Text("Unlink") }
            }
        }

        Spacer(Modifier.height(16.dp))
        Text("Worth doing there", style = MaterialTheme.typography.titleSmall)
        if (places.suggestions.isEmpty()) {
            Text(
                "Nothing from your want-to-go list or bucket list matches these destinations yet.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            places.suggestions.forEach { name ->
                Text("○  $name", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(vertical = 2.dp))
            }
        }

        Spacer(Modifier.height(16.dp))
        Text("Visited on this trip", style = MaterialTheme.typography.titleSmall)
        if (places.visited.isEmpty()) {
            Text(
                if (trip.startDate.isBlank()) "Set the dates and any place you log while away shows up here."
                else "Nothing logged in Places between these dates yet.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            places.visited.forEach { name ->
                Text("📍  $name", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(vertical = 2.dp))
            }
        }
        Spacer(Modifier.height(10.dp))
        TextButton(onClick = { Nav.open("places") }) { Text("Open Places →") }
    }
}

@Composable
private fun DocumentsTab(trip: Trip, patch: ((Trip) -> Trip) -> Unit) {
    val docs = remember { loadDocuments().documents }
    val linked = docs.filter { it.id in trip.documentIds }
    val end = trip.end() ?: trip.start()

    Column {
        Text(
            "Passport, visa, insurance — linked to your Documents records rather than copied, so the expiry date has one owner.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(10.dp))

        // The check that makes linking worth it: anything lapsing before the trip ends.
        val lapsing = linked.filter { d ->
            val exp = parseDateOrNull(d.expiryDate)
            exp != null && end != null && exp <= end
        }
        if (lapsing.isNotEmpty()) {
            Text(
                "Expires before this trip is over:",
                style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.error,
            )
            lapsing.forEach { d ->
                Text("⚠️  ${d.title} — ${d.expiryDate}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
            }
            Spacer(Modifier.height(10.dp))
        }

        if (docs.isEmpty()) {
            Text("No documents saved yet.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            docs.forEach { d ->
                val on = d.id in trip.documentIds
                Row(
                    Modifier.fillMaxWidth().clickable {
                        patch { t -> t.copy(documentIds = if (on) t.documentIds - d.id else t.documentIds + d.id) }
                    }.padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(checked = on, onCheckedChange = null)
                    Spacer(Modifier.width(6.dp))
                    Column(Modifier.weight(1f)) {
                        Text(d.title.ifBlank { "(untitled)" }, style = MaterialTheme.typography.bodyMedium)
                        if (d.expiryDate.isNotBlank()) {
                            Text("expires ${d.expiryDate}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        TextButton(onClick = { Nav.open("documents") }) { Text("Open Documents →") }
    }
}
