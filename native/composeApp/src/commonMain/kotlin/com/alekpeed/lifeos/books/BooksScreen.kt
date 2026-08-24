package com.alekpeed.lifeos.books

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import com.alekpeed.lifeos.Storage
import com.alekpeed.lifeos.data.today
import com.alekpeed.lifeos.net.httpGet
import com.alekpeed.lifeos.net.httpGetImageBase64
import com.alekpeed.lifeos.platform.Native
import androidx.compose.runtime.DisposableEffect
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.ui.layout.onSizeChanged
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.ui.graphics.ImageBitmap
import com.alekpeed.lifeos.platform.PdfReader
import com.alekpeed.lifeos.platform.readBlobBase64
import com.alekpeed.lifeos.platform.deleteBlob
import com.alekpeed.lifeos.platform.loadBlobImage
import com.alekpeed.lifeos.platform.readTextBlob
import com.alekpeed.lifeos.platform.saveBlob
import com.alekpeed.lifeos.platform.saveTextBlob
import com.alekpeed.lifeos.ui.BulkBar
import com.alekpeed.lifeos.ui.BulkTick
import com.alekpeed.lifeos.ui.bulkClickable
import com.alekpeed.lifeos.ui.rememberBulk
import com.alekpeed.lifeos.ui.BulkState
import com.alekpeed.lifeos.ui.SaveToast
import com.alekpeed.lifeos.ui.usDate
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

private val obJson = Json { ignoreUnknownKeys = true }

// Look an ISBN up on Open Library (keyless, public) → a draft Book (title, author,
// pages). Returns null if not found / offline. id is assigned by the caller.
private suspend fun lookupIsbn(isbn: String): Book? {
    val clean = isbn.trim().filter { it.isDigit() || it == 'X' || it == 'x' }
    if (clean.length < 10) return null
    val resp = httpGet("https://openlibrary.org/api/books?bibkeys=ISBN:$clean&jscmd=data&format=json")
    if (!resp.ok) return null
    return try {
        val entry = obJson.parseToJsonElement(resp.body).jsonObject["ISBN:$clean"]?.jsonObject ?: return null
        val title = entry["title"]?.jsonPrimitive?.content?.takeIf { it.isNotBlank() } ?: return null
        val author = entry["authors"]?.jsonArray?.firstOrNull()?.jsonObject?.get("name")?.jsonPrimitive?.content.orEmpty()
        val pages = entry["number_of_pages"]?.jsonPrimitive?.content?.toIntOrNull()
        Book(id = 0L, title = title, author = author, totalPages = pages)
    } catch (e: Exception) {
        null
    }
}

// Pull the cover art for an ISBN from Open Library's keyless covers API and stash
// it in the blob store; returns "" when there's no cover or the download fails.
// `default=false` makes the endpoint 404 instead of returning a blank 1×1
// placeholder when a cover isn't on file.
private suspend fun downloadCover(isbn: String): String {
    val clean = isbn.trim().filter { it.isDigit() || it == 'X' || it == 'x' }
    if (clean.length < 10) return ""
    val b64 = httpGetImageBase64("https://covers.openlibrary.org/b/isbn/$clean-L.jpg?default=false") ?: return ""
    return saveBlob(b64) ?: ""
}

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
    var reading by remember { mutableStateOf<Pair<Long, Long>?>(null) }
    var input by remember { mutableStateOf("") }
    val bulk = rememberBulk()
    var scanBusy by remember { mutableStateOf(false) }
    var scanMsg by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    // Reading takes over the whole screen. A book can hold several files, so what's
    // open is a (book, file) pair — the reader is chosen by that file's kind.
    val readingBook = data.books.firstOrNull { it.id == reading?.first }
    val readingFile = readingBook?.files?.firstOrNull { it.id == reading?.second }
    if (readingBook != null && readingFile != null) {
        if (readingFile.kind == "pdf") PdfReaderScreen(readingBook, readingFile, data, ::save) { reading = null }
        else ReaderScreen(readingBook, readingFile, data, ::save) { reading = null }
        return
    }

    Column(Modifier.fillMaxSize().padding(20.dp)) {
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
        if (Native.supportsQrScan) {
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedButton(
                    onClick = {
                        scanMsg = null
                        Native.scanBarcode { code ->
                            if (code == null) return@scanBarcode
                            scanBusy = true
                            scope.launch {
                                val draft = lookupIsbn(code)
                                if (draft == null) {
                                    scanBusy = false
                                    scanMsg = "Couldn't find that ISBN — add it by hand."
                                    return@launch
                                }
                                // Best-effort cover art; the book still saves without it.
                                val coverBlob = downloadCover(code)
                                scanBusy = false
                                save(data.copy(books = data.books + draft.copy(id = freshId(), status = tab, photoBlob = coverBlob)))
                            }
                        }
                    },
                    enabled = !scanBusy,
                ) {
                    if (scanBusy) {
                        CircularProgressIndicator(Modifier.height(16.dp).width(16.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(8.dp))
                        Text("Looking up…")
                    } else {
                        Text("📷 Scan ISBN")
                    }
                }
                scanMsg?.let {
                    Spacer(Modifier.width(10.dp))
                    Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
                }
            }
        }
        Spacer(Modifier.height(12.dp))

        val filtered = data.books.filter { (it.status.ifBlank { "to_read" }) == tab }
        if (filtered.isEmpty()) { Muted("No books here yet."); return@Column }
        // Tick several and clear the shelf in one go — a book's cover and any files
        // it holds go with it.
        BulkBar(
            bulk = bulk,
            ids = filtered.map { it.id },
            noun = "book",
            onDelete = { ids ->
                data.books.filter { it.id in ids }.forEach { b ->
                    if (b.photoBlob.isNotBlank()) deleteBlob(b.photoBlob)
                    b.files.forEach { file -> if (file.blobId.isNotBlank()) deleteBlob(file.blobId) }
                }
                if (selected?.let { it in ids } == true) selected = null
                save(data.copy(books = data.books.filterNot { it.id in ids }))
            },
        )
        Spacer(Modifier.height(4.dp))
        LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(filtered, key = { it.id }) { book ->
                Column {
                    BookCard(book, bulk) { selected = if (selected == book.id) null else book.id }
                    if (selected == book.id && !bulk.on) {
                        BookDetail(data, ::save, ::freshId, book, onRead = { fileId -> reading = book.id to fileId }) { selected = null }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BookCard(book: Book, bulk: BulkState, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
            .background(
                if (bulk.has(book.id)) MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)
                else MaterialTheme.colorScheme.surfaceVariant,
            )
            .bulkClickable(bulk, book.id) { onClick() }.padding(14.dp),
    ) {
        BulkTick(bulk, book.id)
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
private fun BookDetail(data: BooksData, save: (BooksData) -> Unit, freshId: () -> Long, book: Book, onRead: (Long) -> Unit, onClose: () -> Unit) {
    fun patch(f: (Book) -> Book) = save(data.copy(books = data.books.map { if (it.id == book.id) f(it) else it }))
    var pagesToday by remember { mutableStateOf("") }
    var showSource by remember { mutableStateOf(false) }
    var importing by remember { mutableStateOf(false) }

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

        // §11.5, build two. The list is where a highlight is worth anything after the
        // fact; the export is the point of having captured them at all.
        Label("Highlights")
        if (book.highlights.isEmpty()) {
            Text(
                "Nothing kept yet. Tap ✎ while reading to save a passage.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            book.highlights.forEach { h ->
                Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(h.text, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                        TextButton(onClick = {
                            patch { it.copy(highlights = it.highlights.filterNot { x -> x.id == h.id }) }
                        }) { Text("✕") }
                    }
                    val meta = listOf(h.fileName, h.where, h.date).filter { it.isNotBlank() }.joinToString(" · ")
                    if (meta.isNotBlank()) {
                        Text(meta, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (h.note.isNotBlank()) {
                        Text(h.note, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedButton(onClick = { Native.shareText(exportHighlights(book)); SaveToast.show("Shared") }) {
                    Text("↗ Export highlights")
                }
                Spacer(Modifier.width(8.dp))
                TextButton(onClick = { Native.copyToClipboard(exportHighlights(book)); SaveToast.show("Copied") }) {
                    Text("Copy")
                }
            }
        }

        Label("Files")
        com.alekpeed.lifeos.attach.AttachmentsSection(book.attachments, { list -> patch { it.copy(attachments = list) } }, label = "PDF & other files")

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

        // Files. A book is often more than one file — the EPUB and the PDF of the same
        // title, a companion workbook — and each keeps its own place, so moving between
        // them doesn't lose where you were in either.
        Label("Files")
        if (book.files.isEmpty()) {
            Text(
                "No files yet — add an EPUB, text file, or PDF to read it right here.",
                style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        book.files.forEach { f ->
            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("${f.icon}  ${f.name.ifBlank { if (f.kind == "pdf") "PDF" else "Ebook" }}", style = MaterialTheme.typography.bodyMedium)
                    val where = when {
                        f.kind == "pdf" && f.page > 0 -> "page ${f.page + 1}"
                        f.kind != "pdf" && f.frac > 0f -> "${(f.frac * 100).toInt()}%"
                        else -> "not started"
                    }
                    Text(where, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                TextButton(onClick = { onRead(f.id) }) { Text("Read") }
                TextButton(onClick = {
                    if (f.kind == "pdf") deleteBlob(f.blobId)
                    patch { b -> b.copy(files = b.files.filterNot { it.id == f.id }) }
                }) { Text("×") }
            }
        }
        if (Native.supportsFilePick) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedButton(
                    onClick = {
                        importing = true
                        Native.pickEbookNamed { name, text ->
                            importing = false
                            if (text != null) {
                                saveTextBlob(text)?.let { id ->
                                    patch { b ->
                                        val nid = (b.files.maxOfOrNull { it.id } ?: 0L) + 1
                                        b.copy(files = b.files + BookFile(nid, name?.ifBlank { null } ?: "Ebook", "text", id))
                                    }
                                }
                            }
                        }
                    },
                    enabled = !importing,
                ) { Text(if (importing) "Reading file…" else "📗 Add ebook") }
                Spacer(Modifier.width(8.dp))
                OutlinedButton(onClick = {
                    Native.pickAttachment { name, mime, b64 ->
                        if (b64 != null && (mime?.contains("pdf") == true || name?.endsWith(".pdf", true) == true)) {
                            saveBlob(b64)?.let { id ->
                                patch { b ->
                                    val nid = (b.files.maxOfOrNull { it.id } ?: 0L) + 1
                                    b.copy(files = b.files + BookFile(nid, name?.ifBlank { null } ?: "PDF", "pdf", id))
                                }
                            }
                        } else if (b64 != null) SaveToast.show("Pick a PDF file")
                    }
                }) { Text("📕 Add PDF") }
            }
        } else {
            Text("Adding book files needs a file picker.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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

// ---------- Reader ----------

// Full-screen PDF reader: rasterizes one page at a time via the platform
// PdfReader, scaled to the viewport width, with prev/next paging and resume. On a
// platform with no renderer (desktop) it offers to open the file externally.
@Composable
private fun PdfReaderScreen(book: Book, file: BookFile, data: BooksData, save: (BooksData) -> Unit, onClose: () -> Unit) {
    val b64 = remember(file.blobId) { readBlobBase64(file.blobId) }
    var pageCount by remember { mutableStateOf(-1) }   // -1 loading · 0 failed/unsupported
    var page by remember { mutableStateOf(file.page.coerceAtLeast(0)) }
    var widthPx by remember { mutableStateOf(0) }
    var bmp by remember { mutableStateOf<ImageBitmap?>(null) }
    // §11.5. A PDF page is an image, so there is nothing to pre-fill — the sheet opens
    // empty with the page number already filled in as the reference.
    var highlighting by remember { mutableStateOf(false) }

    LaunchedEffect(b64) {
        pageCount = if (b64.isNullOrBlank()) 0 else withContext(Dispatchers.Default) { PdfReader.open(b64) }
    }
    LaunchedEffect(page, widthPx, pageCount) {
        if (pageCount > 0 && widthPx > 0) {
            val target = page.coerceIn(0, pageCount - 1)
            bmp = withContext(Dispatchers.Default) { PdfReader.render(target, widthPx) }
        }
    }
    DisposableEffect(file.blobId) {
        onDispose {
            val safePage = page.coerceIn(0, (pageCount - 1).coerceAtLeast(0))
            saveFilePos(data, save, book.id, file.id) { it.copy(page = safePage) }
            PdfReader.close()
        }
    }

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onClose) { Text("‹ Close") }
            Text(
                if (book.files.size > 1) "${book.title} · ${file.name}" else book.title,
                style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f), maxLines = 1,
            )
            if (pageCount > 0) TextButton(onClick = { highlighting = true }) { Text("✎") }
            if (pageCount > 0) Text("${page + 1} / $pageCount", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Box(
            Modifier.weight(1f).fillMaxWidth()
                .onSizeChanged { if (it.width > 0) widthPx = it.width }
                .verticalScroll(rememberScrollState()),
            contentAlignment = Alignment.TopCenter,
        ) {
            when {
                pageCount < 0 -> CircularProgressIndicator(Modifier.padding(40.dp))
                pageCount == 0 -> Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("This device can't render PDFs in-app.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(10.dp))
                    if (!b64.isNullOrBlank()) OutlinedButton(onClick = { Native.openAttachment(b64, file.name.ifBlank { "${book.title}.pdf" }, "application/pdf") }) { Text("Open in PDF viewer") }
                }
                bmp != null -> Image(bmp!!, contentDescription = "Page ${page + 1}", modifier = Modifier.fillMaxWidth(), contentScale = ContentScale.FillWidth)
                else -> CircularProgressIndicator(Modifier.padding(40.dp))
            }
        }
        if (pageCount > 0) {
            Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(onClick = { if (page > 0) page-- }, enabled = page > 0) { Text("‹ Prev") }
                Text("${page + 1} / $pageCount", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f), textAlign = TextAlign.Center)
                OutlinedButton(onClick = { if (page < pageCount - 1) page++ }, enabled = page < pageCount - 1) { Text("Next ›") }
            }
        }
    }

    if (highlighting) {
        HighlightSheet(initial = "", where = "p. ${page + 1}", onDismiss = { highlighting = false }) { text, note ->
            save(
                data.copy(
                    books = data.books.map {
                        if (it.id == book.id) addHighlight(it, text, note, file, "p. ${page + 1}") else it
                    },
                ),
            )
            highlighting = false
        }
    }
}

// Full-screen reader that lays the ebook out as real pages: the text is measured
// to the screen once, sliced into page-sized line ranges, and you tap the right
// or left half to turn (with a page-slide). Justified "book" text, adjustable +
// persisted type size, a page counter, and it remembers your place per book
// (per file, not per book) — restored on open, saved on every turn.
@Composable
private fun ReaderScreen(book: Book, file: BookFile, data: BooksData, save: (BooksData) -> Unit, onClose: () -> Unit) {
    val raw = remember(file.blobId) { readTextBlob(file.blobId) ?: "" }
    val chaptered = remember(raw) { parseChapters(raw) }
    val text = chaptered.first
    val chapters = chaptered.second
    var showToc by remember { mutableStateOf(false) }
    var pendingChapterStart by remember { mutableStateOf<Int?>(null) }
    var fontSize by remember { mutableStateOf(Storage.read("ReaderFontSize")?.toIntOrNull() ?: 18) }
    var page by remember { mutableStateOf(-1) }   // -1 until restored from the file's frac
    var forward by remember { mutableStateOf(true) }
    // §11.5. What is on screen right now, kept so the highlight sheet can open
    // pre-filled with it — see HighlightSheet for why that is the capture gesture.
    var visibleText by remember { mutableStateOf("") }
    var highlighting by remember { mutableStateOf(false) }

    fun persist(p: Int, count: Int) {
        val frac = if (count > 1) p.toFloat() / (count - 1) else 0f
        saveFilePos(data, save, book.id, file.id) { it.copy(frac = frac) }
    }

    val bodyStyle = MaterialTheme.typography.bodyLarge.copy(
        fontSize = fontSize.sp,
        lineHeight = (fontSize * 1.55f).sp,
        textAlign = TextAlign.Justify,
        color = MaterialTheme.colorScheme.onSurface,
    )

    Column(Modifier.fillMaxSize().padding(horizontal = 22.dp)) {
        Row(Modifier.fillMaxWidth().padding(top = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onClose) { Text("‹ Library") }
            Text(book.title.ifBlank { "Reading" }, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f), maxLines = 1)
            if (chapters.size > 1) TextButton(onClick = { showToc = true }) { Text("☰ Chapters") }
            TextButton(onClick = { highlighting = true }) { Text("✎") }
            TextButton(onClick = { fontSize = (fontSize - 1).coerceAtLeast(12); Storage.write("ReaderFontSize", fontSize.toString()) }) { Text("A−") }
            TextButton(onClick = { fontSize = (fontSize + 1).coerceAtMost(30); Storage.write("ReaderFontSize", fontSize.toString()) }) { Text("A+") }
        }

        if (text.isBlank()) {
            Spacer(Modifier.height(12.dp))
            Text("This book's text isn't available. Import an EPUB or TXT from its detail panel.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            return@Column
        }

        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth().padding(vertical = 10.dp)) {
            val measurer = rememberTextMeasurer()
            val wPx = constraints.maxWidth
            val hPx = constraints.maxHeight
            val lineHeightPx = with(LocalDensity.current) { (fontSize * 1.55f).sp.toPx() }
            val linesPerPage = max(1, (hPx / lineHeightPx).toInt())

            // One measure per (text, size, width); page turns are then just substrings.
            val layout = remember(text, fontSize, wPx) {
                measurer.measure(AnnotatedString(text), style = bodyStyle, constraints = Constraints(maxWidth = wPx))
            }
            val pageCount = max(1, ceil(layout.lineCount.toFloat() / linesPerPage).toInt())

            LaunchedEffect(pageCount) {
                page = if (page < 0) (file.frac * (pageCount - 1)).roundToInt().coerceIn(0, pageCount - 1)
                else page.coerceIn(0, pageCount - 1)
            }
            // Jump to a chapter picked from the table of contents: map its char
            // offset to a line, then to a page.
            LaunchedEffect(pendingChapterStart, pageCount) {
                val off = pendingChapterStart ?: return@LaunchedEffect
                val line = layout.getLineForOffset(off.coerceIn(0, text.length))
                page = (line / linesPerPage).coerceIn(0, pageCount - 1)
                persist(page, pageCount)
                pendingChapterStart = null
            }

            fun pageText(p: Int): String {
                val first = p * linesPerPage
                if (first >= layout.lineCount) return ""
                val last = min(first + linesPerPage - 1, layout.lineCount - 1)
                return text.substring(layout.getLineStart(first), layout.getLineEnd(last, visibleEnd = true)).trim()
            }
            fun go(delta: Int) {
                val next = (page + delta).coerceIn(0, pageCount - 1)
                if (next != page) { forward = delta > 0; page = next; persist(next, pageCount) }
            }

            if (page >= 0) {
                AnimatedContent(
                    targetState = page,
                    transitionSpec = {
                        if (forward) {
                            (slideInHorizontally { w -> w } + fadeIn()) togetherWith (slideOutHorizontally { w -> -w } + fadeOut())
                        } else {
                            (slideInHorizontally { w -> -w } + fadeIn()) togetherWith (slideOutHorizontally { w -> w } + fadeOut())
                        }
                    },
                    label = "page",
                ) { p ->
                    Text(pageText(p), style = bodyStyle, modifier = Modifier.fillMaxSize())
                }
                LaunchedEffect(page, pageCount, fontSize, wPx) { visibleText = pageText(page) }
                // Invisible tap zones: left third → back, right two-thirds → forward.
                Row(Modifier.fillMaxSize()) {
                    Box(Modifier.weight(1f).fillMaxHeight().clickable { go(-1) })
                    Box(Modifier.weight(2f).fillMaxHeight().clickable { go(1) })
                }
            }
        }

        // Footer page counter derived from the current fraction (measured inside).
        val pctPage = if (file.frac > 0f) file.frac else 0f
        Text(
            "Tap right to turn · left to go back" + if (page >= 0) "   ·   ${(pctPage * 100).roundToInt()}%" else "",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 12.dp),
        )
    }

    if (highlighting) {
        HighlightSheet(
            initial = visibleText,
            where = "${(file.frac * 100).roundToInt()}%",
            onDismiss = { highlighting = false },
        ) { text, note ->
            save(
                data.copy(
                    books = data.books.map {
                        if (it.id == book.id) addHighlight(it, text, note, file, "${(file.frac * 100).roundToInt()}%") else it
                    },
                ),
            )
            highlighting = false
        }
    }

    if (showToc) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showToc = false },
            title = { Text("Chapters") },
            text = {
                LazyColumn(Modifier.heightIn(max = 400.dp)) {
                    items(chapters) { ch ->
                        Text(
                            ch.title,
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.fillMaxWidth().clickable { pendingChapterStart = ch.start; showToc = false }.padding(vertical = 10.dp),
                        )
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { showToc = false }) { Text("Close") } },
        )
    }
}

// A book's text split into chapters at the private-use markers parseEbook writes
// (EPUB spine items). Returns the clean display text (markers replaced with the
// chapter title as a heading) and each chapter's start offset in it. No markers
// (plain TXT or an old import) → the whole book as one unnamed section.
private data class ReaderChapter(val title: String, val start: Int)

private fun parseChapters(raw: String): Pair<String, List<ReaderChapter>> {
    if (!raw.contains('\uE000')) return raw to emptyList()
    val sb = StringBuilder()
    val chapters = mutableListOf<ReaderChapter>()
    val re = Regex("\uE000([^\uE000]*)\uE000\\n?")
    var last = 0
    for (m in re.findAll(raw)) {
        sb.appendRange(raw, last, m.range.first)
        val title = m.groupValues[1].trim().ifBlank { "Chapter ${chapters.size + 1}" }
        chapters.add(ReaderChapter(title, sb.length))
        sb.append(title).append("\n\n")
        last = m.range.last + 1
    }
    sb.appendRange(raw, last, raw.length)
    return sb.toString() to chapters
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

// Write a reading position back into one file of one book, leaving everything else
// alone — both readers persist through here so they can't disagree about the shape.
private fun saveFilePos(
    data: BooksData,
    save: (BooksData) -> Unit,
    bookId: Long,
    fileId: Long,
    f: (BookFile) -> BookFile,
) {
    save(
        data.copy(
            books = data.books.map { b ->
                if (b.id != bookId) b else b.copy(files = b.files.map { if (it.id == fileId) f(it) else it })
            },
        ),
    )
}

// §11.5 — capturing a passage.
//
// The gesture is "the page you're on, trimmed", not a text selection, and that is a
// deliberate choice rather than a shortcut. This reader paints one measured page and
// puts invisible tap zones over it for page turns; a selection container underneath
// would never see a drag, and Compose gives no way to read back what a selection
// container holds anyway. So the sheet opens pre-filled with what is on screen and you
// cut it down to the sentence you meant — which is two seconds of editing against a
// gesture that would not work.
//
// A PDF page is an image, so there is nothing to pre-fill; that sheet opens empty with
// the page number already filled in as the reference.
@Composable
private fun HighlightSheet(
    initial: String,
    where: String,
    onDismiss: () -> Unit,
    onSave: (String, String) -> Unit,
) {
    var text by remember { mutableStateOf(initial) }
    var note by remember { mutableStateOf("") }
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (where.isBlank()) "Save a highlight" else "Save a highlight · $where") },
        text = {
            Column {
                Text(
                    if (initial.isBlank()) "Type or paste the passage."
                    else "Trim this down to the passage you want to keep.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    text, { text = it },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 120.dp, max = 260.dp),
                    label = { Text("Passage") },
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    note, { note = it },
                    modifier = Modifier.fillMaxWidth(), singleLine = true,
                    label = { Text("Why it matters (optional)") },
                )
            }
        },
        confirmButton = {
            TextButton(enabled = text.isNotBlank(), onClick = { onSave(text, note) }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
