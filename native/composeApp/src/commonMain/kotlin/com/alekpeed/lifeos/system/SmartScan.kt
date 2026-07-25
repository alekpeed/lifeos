package com.alekpeed.lifeos.system

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.alekpeed.lifeos.Nav
import com.alekpeed.lifeos.ai.AiClient
import com.alekpeed.lifeos.books.Book
import com.alekpeed.lifeos.books.loadBooks
import com.alekpeed.lifeos.books.saveBooks
import com.alekpeed.lifeos.data.today
import com.alekpeed.lifeos.documents.Document
import com.alekpeed.lifeos.documents.loadDocuments
import com.alekpeed.lifeos.documents.saveDocuments
import com.alekpeed.lifeos.ideas.Idea
import com.alekpeed.lifeos.ideas.loadIdeas
import com.alekpeed.lifeos.ideas.saveIdeas
import com.alekpeed.lifeos.links.Link
import com.alekpeed.lifeos.links.loadLinks
import com.alekpeed.lifeos.links.parseYouTubeId
import com.alekpeed.lifeos.links.saveLinks
import com.alekpeed.lifeos.net.httpGet
import com.alekpeed.lifeos.net.httpGetImageBase64
import com.alekpeed.lifeos.people.Contact
import com.alekpeed.lifeos.people.loadContacts
import com.alekpeed.lifeos.people.saveContacts
import com.alekpeed.lifeos.platform.Native
import com.alekpeed.lifeos.platform.saveBlob
import com.alekpeed.lifeos.quartermaster.InventoryItem
import com.alekpeed.lifeos.quartermaster.loadInventory
import com.alekpeed.lifeos.quartermaster.saveInventory
import com.alekpeed.lifeos.recipes.Ingredient
import com.alekpeed.lifeos.recipes.Recipe
import com.alekpeed.lifeos.recipes.loadRecipes
import com.alekpeed.lifeos.recipes.saveRecipes
import com.alekpeed.lifeos.tasks.Task
import com.alekpeed.lifeos.tasks.loadTasks
import com.alekpeed.lifeos.tasks.saveTasks
import com.alekpeed.lifeos.ui.SaveToast
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

// The scanner: point the camera at anything and Life OS reads what's actually on it,
// then proposes where it goes — and you confirm. The intelligence is in the extraction,
// not just the filing: a list of to-dos comes back as its ITEMS, so seven jobs on your
// mum's note become seven tasks, not one document. Same for a shopping list, a recipe's
// ingredients, or the details on a business card.
//
// Nothing is written until you accept, and you can send it somewhere else in one tap —
// so a wrong guess costs a moment, never a misfiled record.

private val json = Json { ignoreUnknownKeys = true }

// Where a scan can land. `label` is what the confirm sheet offers.
enum class ScanDest(val id: String, val label: String) {
    TASKS("tasks", "Tasks"),
    QUARTERMASTER("quartermaster", "Quartermaster"),
    RECIPES("recipes", "Recipes"),
    CONTACTS("contacts", "Contacts"),
    BOOKS("books", "Books"),
    DOCUMENTS("documents", "Documents"),
    IDEAS("ideas", "Ideas"),
}

// What the model saw, structured. `items` is the payload for anything list-shaped.
data class ScanProposal(
    val kind: String,
    val title: String,
    val items: List<String>,
    val text: String,
    val summary: String,
    val fields: Map<String, String>,
    val photoB64: String,
    val suggested: ScanDest,
)

// Drives the confirm sheet. The scan runs in the background and parks its proposal here.
object ScanFlow {
    var proposal by mutableStateOf<ScanProposal?>(null)
        private set
    var busy by mutableStateOf(false)
        private set

    internal fun propose(p: ScanProposal) { proposal = p; busy = false }
    internal fun working(on: Boolean) { busy = on }
    fun dismiss() { proposal = null }
}

private const val READ_SYSTEM =
    "You look at one photo and extract what is on it so an app can file it. Respond with " +
        "ONLY a JSON object, no prose and no code fences:\n" +
        "{\"kind\":\"tasklist|shoppinglist|recipe|contact|book|receipt|document|note|other\"," +
        "\"title\":\"short label, max 60 chars\"," +
        "\"items\":[\"one entry per line item, in order\"]," +
        "\"text\":\"all readable text, verbatim\"," +
        "\"summary\":\"one or two plain sentences\"," +
        "\"fields\":{\"name\":\"\",\"phone\":\"\",\"email\":\"\",\"company\":\"\",\"author\":\"\"," +
        "\"merchant\":\"\",\"total\":\"\",\"date\":\"\"}}\n" +
        "Rules: use \"tasklist\" for anything that reads as jobs to do, chores, or a " +
        "checklist of actions — put EACH job in items, cleaned up into a short " +
        "imperative (\"Call the plumber\"), and do not include headings or numbering. " +
        "Use \"shoppinglist\" for things to buy, each item in items. Use \"recipe\" with " +
        "the ingredients in items. Use \"contact\" for a business card and fill fields. " +
        "Use \"receipt\" for a shop or restaurant receipt and fill merchant/total/date. " +
        "Use \"document\" for official paper (letter, bill, policy, ID, form, contract). " +
        "Use \"note\" for handwriting or a whiteboard that isn't a list. Leave items empty " +
        "when nothing is list-shaped."

private fun destFor(kind: String): ScanDest = when (kind) {
    "tasklist" -> ScanDest.TASKS
    "shoppinglist" -> ScanDest.QUARTERMASTER
    "recipe" -> ScanDest.RECIPES
    "contact" -> ScanDest.CONTACTS
    "book" -> ScanDest.BOOKS
    "receipt", "document" -> ScanDest.DOCUMENTS
    else -> ScanDest.IDEAS
}

// ---- the camera path (what the big button does) ----

fun scanWithCamera(scope: CoroutineScope) {
    Native.takePhoto { b64 ->
        if (b64.isNullOrBlank()) return@takePhoto
        ScanFlow.working(true)
        SaveToast.show("Reading…")
        scope.launch {
            val p = runCatching { read(b64) }.getOrNull()
            if (p == null) {
                ScanFlow.working(false)
                SaveToast.show("Couldn't read that")
            } else {
                ScanFlow.propose(p)
            }
        }
    }
}

private suspend fun read(b64: String): ScanProposal {
    val reply = AiClient.askWithImage(READ_SYSTEM, "What is on this? Extract it.", b64, 1200)
    if (reply.isError) {
        return ScanProposal(
            kind = "document", title = "Scan", items = emptyList(), text = "",
            summary = "", fields = emptyMap(), photoB64 = b64, suggested = ScanDest.DOCUMENTS,
        )
    }
    val raw = reply.text.trim()
        .removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
    val obj = runCatching { json.parseToJsonElement(raw).jsonObject }.getOrNull()
    fun str(name: String) = obj?.get(name)?.jsonPrimitive?.content?.trim().orEmpty()
    val items = runCatching {
        (obj?.get("items") as? JsonArray)
            ?.mapNotNull { it.jsonPrimitive.content.trim().ifBlank { null } }
            ?.take(60).orEmpty()
    }.getOrDefault(emptyList())
    val fields = runCatching {
        obj?.get("fields")?.jsonObject
            ?.mapValues { it.value.jsonPrimitive.content.trim() }
            ?.filterValues { it.isNotBlank() }.orEmpty()
    }.getOrDefault(emptyMap())

    val kind = str("kind").lowercase().ifBlank { "other" }
    return ScanProposal(
        kind = kind,
        title = str("title").ifBlank { "Scan" },
        items = items,
        text = str("text"),
        summary = str("summary"),
        fields = fields,
        photoB64 = b64,
        suggested = destFor(kind),
    )
}

// ---- filing, once you've accepted ----

fun commitScan(p: ScanProposal, dest: ScanDest) {
    runCatching {
        when (dest) {
            ScanDest.TASKS -> {
                val existing = loadTasks()
                var next = (existing.maxOfOrNull { it.id } ?: 0L) + 1
                val titles = p.items.ifEmpty { listOf(p.title) }
                val added = titles.map { t ->
                    Task(id = next++, title = t, due = p.fields["date"].orEmpty())
                }
                saveTasks(existing + added)
                SaveToast.show(if (added.size == 1) "Added 1 task" else "Added ${added.size} tasks")
            }

            ScanDest.QUARTERMASTER -> {
                val data = loadInventory()
                var next = (data.items.maxOfOrNull { it.id } ?: 0L) + 1
                val names = p.items.ifEmpty { listOf(p.title) }
                val added = names.map { n ->
                    InventoryItem(id = next++, name = n, stockStatus = "Out", stockCheckedAt = today().toString())
                }
                saveInventory(data.copy(items = data.items + added))
                SaveToast.show("Added ${added.size} to Quartermaster")
            }

            ScanDest.RECIPES -> {
                val data = loadRecipes()
                val next = (data.recipes.maxOfOrNull { it.id } ?: 0L) + 1
                var ing = 0L
                saveRecipes(
                    data.copy(
                        recipes = data.recipes + Recipe(
                            id = next, title = p.title,
                            ingredients = p.items.map { Ingredient(id = ++ing, name = it) },
                            notes = p.text,
                            photoBlob = saveBlob(p.photoB64).orEmpty(),
                        ),
                    ),
                )
                SaveToast.show("Saved a recipe")
            }

            ScanDest.CONTACTS -> {
                val data = loadContacts()
                val next = (data.contacts.maxOfOrNull { it.id } ?: 0L) + 1
                saveContacts(
                    data.copy(
                        contacts = data.contacts + Contact(
                            id = next,
                            name = p.fields["name"] ?: p.title,
                            phones = listOfNotNull(p.fields["phone"]),
                            emails = listOfNotNull(p.fields["email"]),
                            company = p.fields["company"].orEmpty(),
                            notes = p.text,
                            photoBlob = saveBlob(p.photoB64).orEmpty(),
                        ),
                    ),
                )
                SaveToast.show("Saved a contact")
            }

            ScanDest.BOOKS -> {
                val data = loadBooks()
                val next = (data.books.maxOfOrNull { it.id } ?: 0L) + 1
                saveBooks(
                    data.copy(
                        books = data.books + Book(
                            id = next, title = p.title,
                            author = p.fields["author"].orEmpty(),
                            notes = p.summary,
                            photoBlob = saveBlob(p.photoB64).orEmpty(),
                        ),
                    ),
                )
                SaveToast.show("Added a book")
            }

            ScanDest.DOCUMENTS -> {
                val data = loadDocuments()
                val next = (data.documents.maxOfOrNull { it.id } ?: 0L) + 1
                val extra = listOfNotNull(
                    p.fields["merchant"]?.let { "Merchant: $it" },
                    p.fields["total"]?.let { "Total: $it" },
                ).joinToString(" · ")
                saveDocuments(
                    data.copy(
                        documents = data.documents + Document(
                            id = next, title = p.title,
                            category = if (p.kind == "receipt") "Receipt" else "",
                            transcription = p.text,
                            summary = listOf(p.summary, extra).filter { it.isNotBlank() }.joinToString(" — "),
                            expiryDate = p.fields["date"].orEmpty(),
                            photoBlob = saveBlob(p.photoB64).orEmpty(),
                        ),
                    ),
                )
                SaveToast.show(if (p.kind == "receipt") "Filed a receipt" else "Filed a document")
            }

            ScanDest.IDEAS -> {
                val data = loadIdeas()
                var next = (data.ideas.maxOfOrNull { it.id } ?: 0L) + 1
                val lines = p.items.ifEmpty {
                    listOf(p.summary.ifBlank { p.text }.ifBlank { p.title })
                }
                val added = lines.filter { it.isNotBlank() }
                    .map { Idea(id = next++, text = it, created = today().toString()) }
                saveIdeas(data.copy(ideas = data.ideas + added))
                SaveToast.show("Saved to Ideas")
            }
        }
    }.onFailure { SaveToast.show("Couldn't save that") }
    ScanFlow.dismiss()
    Nav.open(dest.id)
}

// ---- the code path (long-press): deterministic, files immediately ----

fun scanCode(scope: CoroutineScope) {
    Native.scanAnyCode { code ->
        if (code.isNullOrBlank()) return@scanAnyCode
        scope.launch { runCatching { fileCode(code.trim()) } }
    }
}

private fun looksLikeUrl(s: String): Boolean {
    val low = s.lowercase()
    if (low.startsWith("http://") || low.startsWith("https://")) return true
    return !s.contains(' ') && s.contains('.') && s.length > 3 && !s.all { it.isDigit() || it == '.' }
}

private fun looksLikeIsbn(s: String): Boolean {
    val core = s.filter { it.isDigit() || it == 'X' || it == 'x' }
    return core.length == s.replace("-", "").replace(" ", "").length &&
        (core.length == 8 || core.length == 10 || core.length == 12 || core.length == 13)
}

private suspend fun fileCode(code: String) {
    when {
        looksLikeUrl(code) -> {
            val url = if (code.lowercase().startsWith("http")) code else "https://$code"
            val data = loadLinks()
            val vid = parseYouTubeId(url)
            val next = (data.links.maxOfOrNull { it.id } ?: 0L) + 1
            saveLinks(
                data.copy(
                    links = data.links + Link(
                        id = next, url = url,
                        type = if (vid.isNotEmpty()) "video" else "article",
                        videoId = vid,
                    ),
                ),
            )
            SaveToast.show("Saved a link")
            Nav.open("links")
        }

        looksLikeIsbn(code) -> {
            val draft = lookupIsbn(code)
            if (draft == null) {
                fileLooseText("Scanned code: $code")
                return
            }
            val data = loadBooks()
            val next = (data.books.maxOfOrNull { it.id } ?: 0L) + 1
            saveBooks(data.copy(books = data.books + draft.copy(id = next, photoBlob = downloadCover(code))))
            SaveToast.show("Added ${draft.title}")
            Nav.open("books")
        }

        else -> fileLooseText(code)
    }
}

private fun fileLooseText(text: String) {
    if (text.isBlank()) return
    val data = loadIdeas()
    val next = (data.ideas.maxOfOrNull { it.id } ?: 0L) + 1
    saveIdeas(data.copy(ideas = data.ideas + Idea(id = next, text = text, created = today().toString())))
    SaveToast.show("Saved to Ideas")
    Nav.open("ideas")
}

// Open Library, keyless and public: an ISBN to a draft Book.
private suspend fun lookupIsbn(isbn: String): Book? {
    val clean = isbn.trim().filter { it.isDigit() || it == 'X' || it == 'x' }
    if (clean.isEmpty()) return null
    val resp = httpGet("https://openlibrary.org/api/books?bibkeys=ISBN:$clean&jscmd=data&format=json")
    if (!resp.ok) return null
    return runCatching {
        val entry = json.parseToJsonElement(resp.body).jsonObject["ISBN:$clean"]?.jsonObject ?: return null
        val title = entry["title"]?.jsonPrimitive?.content?.trim().orEmpty()
        if (title.isBlank()) return null
        val author = runCatching {
            (entry["authors"] as? JsonArray)?.firstOrNull()
                ?.jsonObject?.get("name")?.jsonPrimitive?.content
        }.getOrNull().orEmpty()
        Book(id = 0, title = title, author = author)
    }.getOrNull()
}

private suspend fun downloadCover(isbn: String): String {
    val clean = isbn.trim().filter { it.isDigit() || it == 'X' || it == 'x' }
    if (clean.isEmpty()) return ""
    val b64 = httpGetImageBase64("https://covers.openlibrary.org/b/isbn/$clean-L.jpg?default=false") ?: return ""
    return saveBlob(b64) ?: ""
}
