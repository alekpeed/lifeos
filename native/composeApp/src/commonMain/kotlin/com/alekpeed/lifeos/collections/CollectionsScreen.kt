package com.alekpeed.lifeos.collections

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
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
import com.alekpeed.lifeos.attach.PhotoGrid
import com.alekpeed.lifeos.data.today
import com.alekpeed.lifeos.platform.Native
import com.alekpeed.lifeos.ui.DateField
import com.alekpeed.lifeos.ui.SaveToast
import com.alekpeed.lifeos.ui.TagField

// Collections (§5.3). Category-agnostic: nothing here knows what a baseball card is, and
// nothing needs to. The catalog system, the condition grades and the set that would
// complete it are all things you fill in, so one screen serves cards, coins, stamps and
// vinyl equally.

@Composable
fun CollectionsScreen() {
    var data by remember { mutableStateOf(loadCollections()) }
    var openId by remember { mutableStateOf<Long?>(null) }
    var tab by remember { mutableStateOf(0) }
    var adding by remember { mutableStateOf(false) }
    var newName by remember { mutableStateOf("") }

    fun persist(next: CollectionsData) {
        data = next
        saveCollections(next)
        SaveToast.show()
    }

    val open = openId?.let { id -> data.collections.firstOrNull { it.id == id } }
    if (open != null) {
        CollectionDetail(
            coll = open,
            onBack = { openId = null },
            onPatch = { change ->
                persist(data.copy(collections = data.collections.map { if (it.id == open.id) change(it) else it }))
            },
            onDelete = {
                persist(data.copy(collections = data.collections.filterNot { it.id == open.id }))
                openId = null
            },
        )
        return
    }

    Column(Modifier.fillMaxSize().padding(20.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(tab == 0, { tab = 0 }, { Text("Collections") })
            val wanted = remember(data) { wantList(data).size }
            FilterChip(tab == 1, { tab = 1 }, { Text(if (wanted > 0) "Want list · $wanted" else "Want list") })
        }
        Spacer(Modifier.height(12.dp))

        if (tab == 1) {
            WantListTab(data)
            return@Column
        }

        Button({ adding = true }, Modifier.fillMaxWidth()) { Text("+ New collection") }
        Spacer(Modifier.height(10.dp))

        if (data.collections.isEmpty()) {
            Text(
                "No collections yet. A collection is a list plus the thing that makes it a " +
                    "collection: what would complete it.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(data.collections, key = { it.id }) { c ->
                CollectionCard(c) { openId = c.id }
            }
        }
    }

    if (adding) {
        AlertDialog(
            onDismissRequest = { adding = false; newName = "" },
            title = { Text("New collection") },
            text = {
                OutlinedTextField(
                    newName, { newName = it },
                    modifier = Modifier.fillMaxWidth(), singleLine = true,
                    placeholder = { Text("Vinyl, 1952 Topps, world coins…") },
                )
            },
            confirmButton = {
                TextButton({
                    val n = newName.trim().replace("\n", " ")
                    if (n.isNotEmpty()) {
                        val id = nextCollectionId(data)
                        persist(data.copy(collections = data.collections + Collection(id, n)))
                        openId = id
                    }
                    adding = false
                    newName = ""
                }) { Text("Create") }
            },
            dismissButton = { TextButton({ adding = false; newName = "" }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun CollectionCard(c: Collection, onOpen: () -> Unit) {
    val roll = remember(c) { valueRollup(c) }
    val done = remember(c) { completeness(c) }

    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable { onOpen() }.padding(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                c.name.ifBlank { "(untitled)" },
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.weight(1f),
            )
            if (c.category.isNotBlank()) {
                Text(c.category, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
            }
        }
        val bits = buildList {
            add("${c.items.count { it.counted }} held")
            if (roll.value > 0) add("${roll.currency} ${money(roll.value)}")
            val dupes = c.items.count { it.isDuplicate }
            if (dupes > 0) add("$dupes duplicate(s)")
        }
        Text(
            bits.joinToString(" · "),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (done != null) {
            Spacer(Modifier.height(6.dp))
            LinearProgressIndicator(progress = { done.fraction }, modifier = Modifier.fillMaxWidth())
            Text(
                "${done.held} of ${done.target} in the set" + if (done.complete) " · complete" else "",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

// The list you need while standing in a shop, which is useless filed per collection.
@Composable
private fun WantListTab(data: CollectionsData) {
    val wanted = remember(data) { wantList(data) }
    if (wanted.isEmpty()) {
        Text(
            "Nothing on the want list. Mark an item Wanted and it appears here, whichever " +
                "collection it belongs to.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        return
    }
    LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        items(wanted, key = { it.collection.id to it.item.id }) { w ->
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant).padding(10.dp),
            ) {
                Text(w.item.name.ifBlank { "(untitled)" }, style = MaterialTheme.typography.bodyMedium)
                Text(
                    listOfNotNull(
                        w.collection.name.ifBlank { null },
                        w.item.catalogNumber.ifBlank { null },
                        w.item.year.ifBlank { null },
                        itemStatusLabel(w.item.status),
                    ).joinToString(" · "),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

// ---- one collection --------------------------------------------------------------------

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CollectionDetail(
    coll: Collection,
    onBack: () -> Unit,
    onPatch: ((Collection) -> Collection) -> Unit,
    onDelete: () -> Unit,
) {
    var tab by remember(coll.id) { mutableStateOf(0) }
    var openItem by remember(coll.id) { mutableStateOf<Long?>(null) }
    var confirmDelete by remember(coll.id) { mutableStateOf(false) }

    val item = openItem?.let { id -> coll.items.firstOrNull { it.id == id } }
    if (item != null) {
        ItemDetail(
            coll = coll,
            item = item,
            onBack = { openItem = null },
            onPatch = { change ->
                onPatch { c -> c.copy(items = c.items.map { if (it.id == item.id) change(it) else it }) }
            },
            onDelete = {
                onPatch { c -> c.copy(items = c.items.filterNot { it.id == item.id }) }
                openItem = null
            },
        )
        return
    }

    Column(Modifier.fillMaxSize()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onBack) { Text("← Collections") }
            Text(
                coll.name.ifBlank { "(untitled)" },
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f),
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf("Items", "Set", "Value", "Setup").forEachIndexed { i, name ->
                FilterChip(tab == i, { tab = i }, { Text(name) })
            }
        }
        Spacer(Modifier.height(10.dp))

        when (tab) {
            0 -> ItemsTab(coll, onPatch) { openItem = it }
            1 -> SetTab(coll)
            2 -> ValueTab(coll)
            else -> SetupTab(coll, onPatch) { confirmDelete = true }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete ${coll.name}?") },
            text = { Text("Its ${coll.items.size} item(s) go with it. History can put it back.") },
            confirmButton = { TextButton({ confirmDelete = false; onDelete() }) { Text("Delete") } },
            dismissButton = { TextButton({ confirmDelete = false }) { Text("Keep it") } },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ItemsTab(
    coll: Collection,
    onPatch: ((Collection) -> Collection) -> Unit,
    onOpen: (Long) -> Unit,
) {
    var group by remember(coll.id) { mutableStateOf(GroupBy.NONE) }
    var newName by remember(coll.id) { mutableStateOf("") }
    var newCatalog by remember(coll.id) { mutableStateOf("") }
    var onlyDupes by remember(coll.id) { mutableStateOf(false) }

    fun add(name: String, catalog: String) {
        val n = name.trim().replace("\n", " ")
        if (n.isEmpty()) return
        onPatch { c ->
            c.copy(
                items = c.items + CollItem(
                    id = nextItemId(c),
                    name = n,
                    catalogNumber = catalog.trim(),
                    acquiredCurrency = c.defaultCurrency,
                    acquiredDate = today().toString(),
                ),
            )
        }
    }

    Column(Modifier.fillMaxSize()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                newName, { newName = it }, modifier = Modifier.weight(1f),
                singleLine = true, placeholder = { Text("Item name") },
            )
            Spacer(Modifier.width(6.dp))
            OutlinedTextField(
                newCatalog, { newCatalog = it }, modifier = Modifier.width(130.dp),
                singleLine = true, placeholder = { Text(coll.catalogSystem.ifBlank { "Cat. no." }) },
            )
            Spacer(Modifier.width(6.dp))
            Button({ add(newName, newCatalog); newName = ""; newCatalog = "" }) { Text("Add") }
        }
        // The scanner already exists (§5.3 reuse): a barcode drops straight into the
        // catalog field rather than needing a Collections-specific integration.
        if (Native.supportsQrScan) {
            TextButton({
                Native.scanAnyCode { code -> if (!code.isNullOrBlank()) newCatalog = code }
            }) { Text("⌗ Scan a code") }
        }

        Spacer(Modifier.height(8.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            GroupBy.entries.forEach { g ->
                FilterChip(group == g, { group = g }, { Text(groupLabel(g)) })
            }
            val dupes = coll.items.count { it.isDuplicate }
            if (dupes > 0) {
                FilterChip(onlyDupes, { onlyDupes = !onlyDupes }, { Text("Duplicates ($dupes)") })
            }
        }
        Spacer(Modifier.height(8.dp))

        val shown = remember(coll, group, onlyDupes) {
            val base = if (onlyDupes) coll.copy(items = duplicates(coll)) else coll
            grouped(base, group)
        }
        if (coll.items.isEmpty()) {
            Text(
                "Nothing in it yet.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            shown.forEach { (header, items) ->
                if (header.isNotBlank()) {
                    item {
                        Text(
                            header,
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                    }
                }
                items(items, key = { it.id }) { i -> ItemRow(coll, i) { onOpen(i.id) } }
            }
        }
    }
}

@Composable
private fun ItemRow(coll: Collection, i: CollItem, onOpen: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable { onOpen() }.padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(i.name.ifBlank { "(untitled)" }, style = MaterialTheme.typography.bodyMedium)
                if (i.quantity > 1) {
                    Text(
                        "  ×${i.quantity}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            val bits = listOfNotNull(
                i.catalogNumber.ifBlank { null },
                i.year.ifBlank { null },
                i.condition.ifBlank { null },
                if (i.graded) "${i.grader} ${i.gradeValue}".trim().ifBlank { "graded" } else null,
                if (i.status != ItemStatus.OWNED) itemStatusLabel(i.status) else null,
                i.storageLocation.ifBlank { null },
            )
            if (bits.isNotEmpty()) {
                Text(
                    bits.joinToString(" · "),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (i.valuation > 0) {
            Text(
                "${i.acquiredCurrency.ifBlank { coll.defaultCurrency }} ${money(i.valuation)}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun SetTab(coll: Collection) {
    val done = completeness(coll)
    if (done == null) {
        Text(
            "No target set. Add the catalog references that would complete this collection " +
                "in Setup, and this becomes a real completeness figure — until then there is " +
                "nothing to be missing.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        return
    }
    Column(Modifier.fillMaxSize()) {
        Text(
            "${done.held} of ${done.target}",
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.primary,
        )
        LinearProgressIndicator(progress = { done.fraction }, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(10.dp))
        if (done.complete) {
            Text("Complete.", style = MaterialTheme.typography.bodyMedium)
            return@Column
        }
        Text(
            "Missing (${done.missing.size})",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        LazyColumn {
            items(done.missing) { ref ->
                Text(ref, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(vertical = 2.dp))
            }
        }
    }
}

@Composable
private fun ValueTab(coll: Collection) {
    val roll = remember(coll) { valueRollup(coll) }
    var exported by remember(coll.id) { mutableStateOf("") }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        StatRow("Cost basis", "${roll.currency} ${money(roll.cost)}")
        StatRow("Estimated value", "${roll.currency} ${money(roll.value)}")
        StatRow(
            if (roll.gain >= 0) "Unrealised gain" else "Unrealised loss",
            "${roll.currency} ${money(kotlin.math.abs(roll.gain))}",
        )
        if (roll.unpriced > 0 || roll.unvalued > 0) {
            Spacer(Modifier.height(6.dp))
            Text(
                buildString {
                    append("The rollup is only as good as its coverage: ")
                    if (roll.unpriced > 0) append("${roll.unpriced} item(s) have no purchase price")
                    if (roll.unpriced > 0 && roll.unvalued > 0) append(", ")
                    if (roll.unvalued > 0) append("${roll.unvalued} have no valuation")
                    append(".")
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Spacer(Modifier.height(14.dp))
        Text("Insurance export", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            "A plain list with values and photo counts, to attach to a Documents record. " +
                "Plain text on purpose — an insurer wants something they can read and print.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row {
            OutlinedButton({ exported = insuranceExport(coll) }) { Text("Generate") }
            if (exported.isNotBlank()) {
                Spacer(Modifier.width(8.dp))
                OutlinedButton({ Native.shareText(exported); SaveToast.show("Shared") }) { Text("Share") }
            }
        }
        if (exported.isNotBlank()) {
            Spacer(Modifier.height(8.dp))
            Text(
                exported,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .padding(10.dp),
            )
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun StatRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun SetupTab(coll: Collection, onPatch: ((Collection) -> Collection) -> Unit, onDelete: () -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        OutlinedTextField(
            coll.name, { v -> onPatch { it.copy(name = v.replace("\n", " ")) } },
            modifier = Modifier.fillMaxWidth(), singleLine = true, label = { Text("Name") },
        )
        Spacer(Modifier.height(6.dp))
        Row {
            OutlinedTextField(
                coll.category, { v -> onPatch { it.copy(category = v.replace("\n", " ")) } },
                modifier = Modifier.weight(1f), singleLine = true,
                label = { Text("Category") }, placeholder = { Text("cards, coins, vinyl…") },
            )
            Spacer(Modifier.width(8.dp))
            OutlinedTextField(
                coll.defaultCurrency, { v -> onPatch { it.copy(defaultCurrency = v.uppercase().take(3)) } },
                modifier = Modifier.width(110.dp), singleLine = true, label = { Text("Currency") },
            )
        }
        Spacer(Modifier.height(6.dp))
        OutlinedTextField(
            coll.catalogSystem, { v -> onPatch { it.copy(catalogSystem = v.replace("\n", " ")) } },
            modifier = Modifier.fillMaxWidth(), singleLine = true,
            label = { Text("Catalog system") }, placeholder = { Text("Scott, Beckett, Discogs, Krause…") },
        )
        Text(
            "A label, not an integration — nothing here depends on a service that might " +
                "disappear.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(Modifier.height(10.dp))
        OutlinedTextField(
            coll.conditionScale.joinToString(", "),
            { v -> onPatch { it.copy(conditionScale = splitList(v)) } },
            modifier = Modifier.fillMaxWidth(), singleLine = true,
            label = { Text("Condition scale") },
            placeholder = { Text("Poor, Fair, Good, VG, NM, Gem Mint") },
        )
        Text(
            "Worst to best, in your own words. Grouping by condition reads its order from here.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(Modifier.height(10.dp))
        OutlinedTextField(
            coll.targetSet.joinToString(", "),
            { v -> onPatch { it.copy(targetSet = splitList(v)) } },
            modifier = Modifier.fillMaxWidth(), singleLine = false,
            label = { Text("Target set") },
            placeholder = { Text("The catalog references that would complete it") },
        )
        Text(
            "This is what makes it a collection rather than a list. Without it there is " +
                "nothing to be missing.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(Modifier.height(10.dp))
        OutlinedTextField(
            coll.description, { v -> onPatch { it.copy(description = v) } },
            modifier = Modifier.fillMaxWidth(), singleLine = false, label = { Text("Description") },
        )

        Spacer(Modifier.height(16.dp))
        TextButton(onDelete) { Text("Delete collection") }
        Spacer(Modifier.height(24.dp))
    }
}

// ---- one item ---------------------------------------------------------------------------

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ItemDetail(
    coll: Collection,
    item: CollItem,
    onBack: () -> Unit,
    onPatch: ((CollItem) -> CollItem) -> Unit,
    onDelete: () -> Unit,
) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        TextButton(onBack) { Text("← ${coll.name.ifBlank { "Collection" }}") }

        OutlinedTextField(
            item.name, { v -> onPatch { it.copy(name = v.replace("\n", " ")) } },
            modifier = Modifier.fillMaxWidth(), singleLine = true, label = { Text("Name") },
        )

        Label("Identity")
        Row {
            OutlinedTextField(
                item.catalogNumber, { v -> onPatch { it.copy(catalogNumber = v.trim()) } },
                modifier = Modifier.weight(1f), singleLine = true,
                label = { Text(coll.catalogSystem.ifBlank { "Catalog no." }) },
            )
            Spacer(Modifier.width(8.dp))
            OutlinedTextField(
                item.year, { v -> onPatch { it.copy(year = v.take(12)) } },
                modifier = Modifier.width(110.dp), singleLine = true, label = { Text("Year") },
            )
        }
        if (Native.supportsQrScan) {
            TextButton({
                Native.scanAnyCode { code -> if (!code.isNullOrBlank()) onPatch { it.copy(catalogNumber = code) } }
            }) { Text("⌗ Scan a code") }
        }
        Row {
            OutlinedTextField(
                item.series, { v -> onPatch { it.copy(series = v.replace("\n", " ")) } },
                modifier = Modifier.weight(1f), singleLine = true, label = { Text("Series") },
            )
            Spacer(Modifier.width(8.dp))
            OutlinedTextField(
                item.setName, { v -> onPatch { it.copy(setName = v.replace("\n", " ")) } },
                modifier = Modifier.weight(1f), singleLine = true, label = { Text("Set") },
            )
        }
        OutlinedTextField(
            item.variant, { v -> onPatch { it.copy(variant = v.replace("\n", " ")) } },
            modifier = Modifier.fillMaxWidth(), singleLine = true,
            label = { Text("Variant") }, placeholder = { Text("error, short print, coloured vinyl…") },
        )

        Label("Holding")
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                item.quantity.toString(),
                { v -> onPatch { it.copy(quantity = (v.filter { c -> c.isDigit() }.take(4).toIntOrNull() ?: 1).coerceAtLeast(0)) } },
                modifier = Modifier.width(110.dp), singleLine = true, label = { Text("Quantity") },
            )
            Spacer(Modifier.width(8.dp))
            OutlinedTextField(
                item.storageLocation, { v -> onPatch { it.copy(storageLocation = v.replace("\n", " ")) } },
                modifier = Modifier.weight(1f), singleLine = true,
                label = { Text("Stored") }, placeholder = { Text("binder 2, page 4") },
            )
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            ItemStatus.entries.forEach { st ->
                FilterChip(item.status == st, { onPatch { it.copy(status = st) } }, { Text(itemStatusLabel(st)) })
            }
        }

        Label("Condition")
        if (coll.conditionScale.isEmpty()) {
            Text(
                "No condition scale set for this collection — add one in Setup and the grades " +
                    "appear here.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                coll.conditionScale.forEach { grade ->
                    FilterChip(
                        item.condition.equals(grade, ignoreCase = true),
                        { onPatch { it.copy(condition = if (it.condition == grade) "" else grade) } },
                        { Text(grade) },
                    )
                }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(item.graded, { on -> onPatch { it.copy(graded = on) } })
            Text("Professionally graded", style = MaterialTheme.typography.bodyMedium)
        }
        if (item.graded) {
            Row {
                OutlinedTextField(
                    item.grader, { v -> onPatch { it.copy(grader = v.replace("\n", " ")) } },
                    modifier = Modifier.weight(1f), singleLine = true,
                    label = { Text("Grader") }, placeholder = { Text("PSA, BGS, NGC…") },
                )
                Spacer(Modifier.width(8.dp))
                OutlinedTextField(
                    item.gradeValue, { v -> onPatch { it.copy(gradeValue = v.take(12)) } },
                    modifier = Modifier.width(100.dp), singleLine = true, label = { Text("Grade") },
                )
            }
            OutlinedTextField(
                item.certNumber, { v -> onPatch { it.copy(certNumber = v.trim()) } },
                modifier = Modifier.fillMaxWidth(), singleLine = true, label = { Text("Cert number") },
            )
        }

        Label("Acquisition")
        Row {
            Column(Modifier.weight(1f)) {
                Text("Acquired", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                DateField(item.acquiredDate) { v -> onPatch { it.copy(acquiredDate = v) } }
            }
            Spacer(Modifier.width(8.dp))
            OutlinedTextField(
                if (item.acquiredPrice == 0.0) "" else item.acquiredPrice.toString(),
                { v -> onPatch { it.copy(acquiredPrice = v.toDoubleOrNull() ?: 0.0) } },
                modifier = Modifier.width(120.dp), singleLine = true, label = { Text("Paid") },
            )
        }
        OutlinedTextField(
            item.acquiredFrom, { v -> onPatch { it.copy(acquiredFrom = v.replace("\n", " ")) } },
            modifier = Modifier.fillMaxWidth(), singleLine = true,
            label = { Text("From") }, placeholder = { Text("shop, seller, show…") },
        )

        Label("Valuation")
        Row {
            OutlinedTextField(
                if (item.estimatedValue == 0.0) "" else item.estimatedValue.toString(),
                { v -> onPatch { it.copy(estimatedValue = v.toDoubleOrNull() ?: 0.0) } },
                modifier = Modifier.weight(1f), singleLine = true, label = { Text("Estimated value") },
            )
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) {
                Text("Valued on", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                DateField(item.valuationDate) { v -> onPatch { it.copy(valuationDate = v) } }
            }
        }
        OutlinedTextField(
            item.valuationSource, { v -> onPatch { it.copy(valuationSource = v.replace("\n", " ")) } },
            modifier = Modifier.fillMaxWidth(), singleLine = true,
            label = { Text("Valuation source") }, placeholder = { Text("price guide, recent sale, appraisal") },
        )
        if (item.quantity > 1) {
            Text(
                "Per item — ×${item.quantity} makes the holding worth ${money(item.valuation)}.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Label("Photos")
        PhotoGrid(
            attachments = item.photos,
            onChange = { next -> onPatch { it.copy(photos = next) } },
            label = "",
        )

        Label("Provenance")
        OutlinedTextField(
            item.provenance, { v -> onPatch { it.copy(provenance = v) } },
            modifier = Modifier.fillMaxWidth(), singleLine = false,
            placeholder = { Text("Previous owners, the signing, the story of the purchase") },
        )

        Label("Tags")
        TagField(item.tags, "graded, chase, sealed") { v -> onPatch { it.copy(tags = v) } }

        Label("Notes")
        OutlinedTextField(
            item.notes, { v -> onPatch { it.copy(notes = v) } },
            modifier = Modifier.fillMaxWidth(), singleLine = false,
        )

        Spacer(Modifier.height(16.dp))
        TextButton(onDelete) { Text("Delete item") }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun Label(text: String) {
    Spacer(Modifier.height(14.dp))
    Text(text, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

private fun splitList(v: String) = v.split(",").map { it.trim() }.filter { it.isNotEmpty() }

private fun money(v: Double): String = ((v * 100).toLong() / 100.0).toString()
