package com.alekpeed.lifeos.tags

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material3.MaterialTheme
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
import com.alekpeed.lifeos.ui.SaveToast

// The tag vocabulary, and the two things you can only do from one place: see everything
// tagged one way regardless of which module it lives in, and rename or merge a tag across
// all of them at once.

@Composable
fun TagsScreen() {
    // Bumped after any rewrite so every derived list is read fresh. The vocabulary is
    // derived from the modules, so there is no cached copy to keep in step.
    var tick by remember { mutableStateOf(0) }
    var open by remember { mutableStateOf<String?>(null) }
    var query by remember { mutableStateOf("") }

    val records = remember(tick) { allTagged() }
    val index = remember(tick) { tagIndex(records) }
    val clashes = remember(tick) { tagClashes(index) }

    val shown = remember(index, query) {
        val q = canonicalTag(query).lowercase()
        if (q.isEmpty()) index else index.filter { it.tag.lowercase().contains(q) }
    }

    Column(Modifier.fillMaxWidth().padding(16.dp)) {
        Text("Tags", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(4.dp))
        Text(
            if (index.isEmpty()) {
                "Tags you add in ${taggedModules().joinToString(", ")} all show up here."
            } else {
                "${index.size} tag(s) across ${records.size} record(s). " +
                    "Renaming one here renames it everywhere."
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(12.dp))

        val current = open
        if (current != null) {
            TagDetail(
                tag = current,
                records = taggedWith(current, records),
                onBack = { open = null },
                onChanged = { msg, stillOpen ->
                    tick++
                    open = stillOpen
                    SaveToast.show(msg)
                },
            )
            return@Column
        }

        if (index.isEmpty()) return@Column

        if (clashes.isNotEmpty()) {
            ClashBanner(clashes) { msg -> tick++; SaveToast.show(msg) }
            Spacer(Modifier.height(12.dp))
        }

        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            placeholder = { Text("Find a tag…") },
        )
        Spacer(Modifier.height(10.dp))

        if (shown.isEmpty()) {
            Text(
                "No tag matches that.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            return@Column
        }

        LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            items(shown, key = { it.tag }) { use ->
                Row(
                    Modifier.fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .clickable { open = use.tag }
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("#${use.tag}", style = MaterialTheme.typography.bodyLarge)
                        Text(
                            use.sources.joinToString(", "),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Text(
                        use.count.toString(),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                    )
                }
            }
        }
    }
}

// Case variants of one tag. Shown at the top because they are the thing most worth
// fixing and the thing you would never otherwise notice.
@Composable
private fun ClashBanner(clashes: List<TagClash>, onMerged: (String) -> Unit) {
    Column(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(12.dp),
    ) {
        Text(
            if (clashes.size == 1) "One tag is spelled two ways" else "${clashes.size} tags are spelled more than one way",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
        )
        clashes.take(4).forEach { clash ->
            val keep = clash.preferred
            val others = clash.spellings.filter { it.tag != keep.tag }
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    clash.spellings.joinToString(" · ") { "#${it.tag} (${it.count})" },
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(8.dp))
                TextButton({
                    var moved = 0
                    others.forEach { moved += renameTag(it.tag, keep.tag) }
                    onMerged("Merged into #${keep.tag} · $moved record(s)")
                }) { Text("Merge into #${keep.tag}") }
            }
        }
    }
}

// One tag: everything carrying it, and the operations that only make sense app-wide.
@Composable
private fun TagDetail(
    tag: String,
    records: List<TaggedRecord>,
    onBack: () -> Unit,
    onChanged: (message: String, stillOpen: String?) -> Unit,
) {
    var renaming by remember(tag) { mutableStateOf(false) }
    var newName by remember(tag) { mutableStateOf(tag) }
    var confirmDelete by remember(tag) { mutableStateOf(false) }

    Row(verticalAlignment = Alignment.CenterVertically) {
        TextButton(onBack) { Text("← All tags") }
    }
    Text("#$tag", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
    Text(
        "${records.size} record(s) in ${records.map { it.source }.distinct().size} module(s)",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Row {
        TextButton({ renaming = true }) { Text("Rename or merge") }
        TextButton({ confirmDelete = true }) { Text("Remove tag") }
    }

    if (renaming) {
        Column(Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
            OutlinedTextField(
                value = newName,
                onValueChange = { newName = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text("New name") },
            )
            Text(
                "Naming it after a tag that already exists merges the two.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row {
                TextButton({
                    val target = canonicalTag(newName)
                    if (target.isEmpty() || target == tag) {
                        renaming = false
                    } else {
                        val n = renameTag(tag, target)
                        renaming = false
                        onChanged("#$tag → #$target · $n record(s)", target)
                    }
                }) { Text("Apply everywhere") }
                TextButton({ renaming = false; newName = tag }) { Text("Cancel") }
            }
        }
    }

    Spacer(Modifier.height(6.dp))

    LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        items(records, key = { it.source + "|" + it.id }) { r ->
            Column(
                Modifier.fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .clickable { Nav.open(r.moduleId) }
                    .padding(12.dp),
            ) {
                Text(r.label.ifBlank { "Untitled" }, style = MaterialTheme.typography.bodyLarge)
                Text(
                    r.source + " · " + r.tags.joinToString(" ") { "#$it" },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Remove #$tag everywhere?") },
            text = {
                Text(
                    "The tag comes off ${records.size} record(s). The records themselves stay, " +
                        "and History can put the tag back.",
                )
            },
            confirmButton = {
                TextButton({
                    val n = deleteTag(tag)
                    confirmDelete = false
                    onChanged("#$tag removed from $n record(s)", null)
                }) { Text("Remove it") }
            },
            dismissButton = { TextButton({ confirmDelete = false }) { Text("Keep it") } },
        )
    }
}
