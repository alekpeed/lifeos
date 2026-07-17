package com.alekpeed.lifeos.books

import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.alekpeed.lifeos.data.today
import com.alekpeed.lifeos.platform.Native
import com.alekpeed.lifeos.platform.deleteBlob
import com.alekpeed.lifeos.platform.loadBlobImage
import com.alekpeed.lifeos.platform.saveBlob
import com.alekpeed.lifeos.ui.SaveToast
import com.alekpeed.lifeos.ui.usDate

private val DANGER = Color(0xFFD64545)
private val STAR_ON = Color(0xFFE0A63C)

// A small fixed palette for spine colors, indexed by a hash of the book — avoids
// depending on an HSL/HSV Color builder that may not exist in commonMain.
private val SPINE_COLORS = listOf(
    Color(0xFF6C7BdC), Color(0xFF57A773), Color(0xFFD98C5F), Color(0xFFB05C8E),
    Color(0xFF4C9AA6), Color(0xFFC0A24C), Color(0xFF8E6CC0), Color(0xFFCB6A6A),
)
private fun spineColor(book: Book): Color {
    val s = book.genre.ifBlank { book.author.ifBlank { book.title.ifBlank { "book" } } }
    var h = 0
    for (c in s) h = (h * 31 + c.code) and 0x7fffffff
    return SPINE_COLORS[h % SPINE_COLORS.size]
}

@Composable
fun BooksScreen() {
    var data by remember { mutableStateOf(loadBooks()) }
    var counter by remember {
        mutableStateOf(
            maxOf(data.books.maxOfOrNull { it.id } ?: 0L, data.books.flatMap { it.logs }.maxOfOrNull { it.id } ?: 0L),
        )
    }
    fun freshId(): Long { counter += 1; return counter }
    fun save(d: BooksData) { data = d; saveBooks(d); SaveToast.show() }

    var tab by remember { mutableStateOf("reading") }
    var selected by remember { mutableStateOf<Long?>(null) }
    var input by remember { mutableStateOf("") }

    Column(Modifier.fillMaxSize().padding(20.dp)) {
        Text("Books", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(12.dp))
        FlowRowTabs(
            listOf("reading" to "Reading", "to_read" to "To read", "finished" to "Finished", "shelf" to "Shelf", "stats" to "Stats"),
            tab,
        ) { tab = it; selected = null }
        Spacer(Modifier.height(12.dp))

        when (tab) {
            "stats" -> { StatsView(data); return@Column }
            "shelf" -> { ShelfView(data) { selected = it; tab = "reading" }; return@Column }
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(input, { input = it }, modifier = Modifier.weight(1f), singleLine = true, placeholder = { Text("New book") })
            Spacer(Modifier.width(10.dp))
            Button(onClick = {
                val t = input.trim().replace("\n", " ")
                if (t.isNotEmpty()) { save(data.copy(books = data.books + Book(freshId(), t, status = tab))); input = "" }
            }) { Text("Add") }
        }
        Spacer(Modifier.height(12.dp))

        val filtered = data.books.filter { (it.status.ifBlank { "to_read" }) == tab }
        if (filtered.isEmpty()) { Muted("No books here yet."); return@Column }
        LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(filtered, key = { it.id }) { book ->
                Column {
                    BookCard(book) { selected = if (selected == book.id) null else book.id }
                    if (selected == book.id) BookDetail(data, ::save, ::freshId, book) { selected = null }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BookCard(book: Book, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant).clickable { onClick() }.padding(14.dp),
    ) {
        Text("📖", modifier = Modifier.padding(end = 10.dp))
        Column(Modifier.weight(1f)) {
            Text(book.title.ifBlank { "(untitled)" }, style = MaterialTheme.typography.bodyLarge)
            val pct = if (book.status == "reading" && (book.totalPages ?: 0) > 0)
                ((book.currentPage ?: 0) * 100 / book.totalPages!!).coerceIn(0, 100) else null
            val chips = buildList {
                if (book.author.isNotBlank()) add(book.author)
                if (book.genre.isNotBlank()) add(book.genre)
                if (pct != null) add("$pct%")
                if (book.rating > 0) add("★".repeat(book.rating))
            }
            if (chips.isNotEmpty()) FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                chips.forEach { Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary) }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BookDetail(data: BooksData, save: (BooksData) -> Unit, freshId: () -> Long, book: Book, onClose: () -> Unit) {
    fun patch(f: (Book) -> Book) = save(data.copy(books = data.books.map { if (it.id == book.id) f(it) else it }))
    var pagesToday by remember { mutableStateOf("") }
    var showSource by remember { mutableStateOf(false) }

    // Attach/replace the photo: save the new blob, drop the old one, point the
    // record at the new id.
    fun onAttach(b64: String?) {
        if (b64.isNullOrEmpty()) return
        val id = saveBlob(b64) ?: return
        deleteBlob(book.photoBlob)
        patch { it.copy(photoBlob = id) }
    }

    Panel {
        Row {
            (1..5).forEach { i ->
                Text(
                    if (i <= book.rating) "★" else "☆",
                    color = if (i <= book.rating) STAR_ON else MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.clickable { patch { it.copy(rating = if (i == it.rating) 0 else i) } }.padding(end = 4.dp),
                )
            }
        }
        Label("Title"); Field(book.title, "Title") { v -> patch { it.copy(title = v.replace("\n", " ")) } }
        Label("Author"); Field(book.author, "Author") { v -> patch { it.copy(author = v.replace("\n", " ")) } }
        Label("Genre"); Field(book.genre, "Genre") { v -> patch { it.copy(genre = v.replace("\n", " ")) } }
        Label("Status")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            BOOK_STATUSES.forEach { (v, lbl) ->
                FilterChip(selected = book.status == v, onClick = {
                    patch {
                        it.copy(
                            status = v,
                            startedDate = if (v == "reading" && it.startedDate.isBlank()) today().toString() else it.startedDate,
                            finishedDate = if (v == "finished" && it.finishedDate.isBlank()) today().toString() else it.finishedDate,
                        )
                    }
                }, label = { Text(lbl) })
            }
        }
        Row {
            Column(Modifier.weight(1f)) { Label("Total pages"); Field(book.totalPages?.toString() ?: "", "0") { v -> patch { it.copy(totalPages = v.toIntOrNull()) } } }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) { Label("Current page"); Field(book.currentPage?.toString() ?: "", "0") { v -> patch { it.copy(currentPage = v.toIntOrNull()) } } }
        }
        if ((book.totalPages ?: 0) > 0) {
            Text(
                "Est. ${estimatedWords(book.totalPages)} words total · ~${estimatedWords(book.currentPage)} read so far",
                style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Label("Notes"); Field(book.notes, "Notes", singleLine = false) { v -> patch { it.copy(notes = v) } }

        Label("Reading log")
        book.logs.sortedByDescending { it.date }.forEach { log ->
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("${log.pagesRead} pages · ${usDate(log.date).ifBlank { log.date }}", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                TextButton(onClick = { patch { it.copy(logs = it.logs.filterNot { l -> l.id == log.id }) } }) { Text("×") }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(pagesToday, { pagesToday = it }, modifier = Modifier.weight(1f), singleLine = true, placeholder = { Text("Pages read today") })
            Spacer(Modifier.width(8.dp))
            Button(onClick = {
                val p = pagesToday.toIntOrNull()
                if (p != null && p >= 1) {
                    patch {
                        val cur = (it.currentPage ?: 0) + p
                        val capped = it.totalPages?.let { tp -> minOf(cur, tp) } ?: cur
                        it.copy(logs = it.logs + ReadLog(freshId(), today().toString(), p), currentPage = capped)
                    }
                    pagesToday = ""
                }
            }) { Text("Log") }
        }

        Label("Photo")
        val img = remember(book.photoBlob) { loadBlobImage(book.photoBlob) }
        if (book.photoBlob.isNotBlank()) {
            if (img != null) {
                Image(
                    bitmap = img,
                    contentDescription = "Attached photo",
                    modifier = Modifier.fillMaxWidth().heightIn(max = 240.dp).clip(RoundedCornerShape(8.dp)),
                    contentScale = ContentScale.Fit,
                )
            } else {
                Text("Photo attached (no preview available).", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (Native.supportsCamera) TextButton(onClick = { showSource = true }) { Text("Replace") }
                TextButton(onClick = { deleteBlob(book.photoBlob); patch { it.copy(photoBlob = "") } }) { Text("Remove photo") }
            }
        } else if (Native.supportsCamera) {
            OutlinedButton(onClick = { showSource = true }) { Text("📷 Attach photo") }
        } else {
            Text("Photo attachments need a camera.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        if (showSource) {
            androidx.compose.material3.AlertDialog(
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
                deleteBlob(book.photoBlob)
                save(data.copy(books = data.books.filterNot { it.id == book.id }))
                onClose()
            }) { Text("Delete book", color = DANGER) }
        }
    }
}

// ---------- Shelf ----------

@Composable
private fun ShelfView(data: BooksData, onSelect: (Long) -> Unit) {
    if (data.books.isEmpty()) { Muted("No books yet — add some and they'll line up on the shelf."); return }
    LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        BOOK_STATUSES.forEach { (statusVal, statusLabel) ->
            val shelf = data.books.filter { (it.status.ifBlank { "to_read" }) == statusVal }
            if (shelf.isNotEmpty()) {
                item { Text(statusLabel, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                item {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(shelf, key = { it.id }) { b -> Spine(b) { onSelect(b.id) } }
                    }
                }
            }
        }
    }
}

@Composable
private fun Spine(book: Book, onClick: () -> Unit) {
    val h = (130 + (book.totalPages ?: 0) / 6).coerceIn(130, 230)
    Box(
        Modifier.width(46.dp).height(h.dp).clip(RoundedCornerShape(3.dp)).background(spineColor(book)).clickable { onClick() }.padding(6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            book.title.ifBlank { "(untitled)" }, color = Color.White,
            style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.Center, maxLines = 6,
        )
    }
}

// ---------- Stats ----------

@Composable
private fun StatsView(data: BooksData) {
    val allDates = data.books.flatMap { it.logs }.map { it.date }.toSet()
    val streak = readingStreak(allDates)
    val year = today().toString().take(4)
    val pagesThisYear = data.books.flatMap { it.logs }.filter { it.date.startsWith(year) }.sumOf { it.pagesRead }
    val genre = data.books.filter { it.genre.isNotBlank() }.groupingBy { it.genre }.eachCount().entries.sortedByDescending { it.value }
    val author = data.books.filter { it.author.isNotBlank() }.groupingBy { it.author }.eachCount().entries.sortedByDescending { it.value }

    Column(Modifier.fillMaxSize()) {
        Text("$streak-day reading streak", style = MaterialTheme.typography.titleMedium)
        Muted("$pagesThisYear pages read in $year (~${estimatedWords(pagesThisYear)} words)")
        Spacer(Modifier.height(14.dp))
        Text("By genre", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (genre.isEmpty()) Muted("No genres tagged yet.") else genre.forEach { Breakdown(it.key, it.value) }
        Spacer(Modifier.height(12.dp))
        Text("By author", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (author.isEmpty()) Muted("No authors tagged yet.") else author.forEach { Breakdown(it.key, it.value) }
    }
}

@Composable
private fun Breakdown(label: String, count: Int) {
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
        Text(label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        Text("$count", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

// ---------- shared ----------

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FlowRowTabs(tabs: List<Pair<String, String>>, current: String, onSelect: (String) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        tabs.forEach { (v, lbl) -> FilterChip(selected = current == v, onClick = { onSelect(v) }, label = { Text(lbl) }) }
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
    Spacer(Modifier.height(8.dp))
    Text(text, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Spacer(Modifier.height(4.dp))
}

@Composable
private fun Field(value: String, placeholder: String, singleLine: Boolean = true, onChange: (String) -> Unit) {
    OutlinedTextField(value = value, onValueChange = onChange, modifier = Modifier.fillMaxWidth(), singleLine = singleLine, placeholder = { Text(placeholder) })
}

@Composable
private fun Muted(text: String) {
    Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}
