package com.alekpeed.lifeos.history

import com.alekpeed.lifeos.Storage
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

// The mutation log is the one piece of this build that has to be right without anyone
// watching: it decides whether a deleted record can come back. Storage on desktop writes
// under ~/.lifeos, so pointing user.home at a scratch directory gives the real objects a
// real disk to work against — no mocks, and the same code path the app runs.
class HistoryTest {

    companion object {
        // Storage fixes its directory the first time anything touches it, so user.home
        // has to be redirected before that — class init is the only moment early enough.
        private val home: java.io.File =
            java.io.File(System.getProperty("java.io.tmpdir"), "lifeos-history-test")
                .apply { deleteRecursively(); mkdirs() }

        init {
            System.setProperty("user.home", home.absolutePath)
        }
    }

    @BeforeTest
    fun setUp() {
        java.io.File(home, ".lifeos").listFiles()?.forEach { it.delete() }
        History.forget()
    }

    @AfterTest
    fun tearDown() {
        java.io.File(home, ".lifeos").listFiles()?.forEach { it.delete() }
    }

    // Modules store an object whose array fields hold the records, which is the shape
    // Travel, Tasks and most others use.
    private fun tasks(vararg rows: String) = """{"items":[${rows.joinToString(",")}]}"""

    private fun task(id: Int, title: String, done: Boolean = false) =
        """{"id":$id,"title":"$title","done":$done}"""

    private fun read(key: String): JsonObject =
        Json.parseToJsonElement(Storage.read(key)!!).jsonObject

    private fun titles(key: String): List<String> =
        read(key)["items"]!!.jsonArray.map { it.jsonObject["title"]!!.jsonPrimitive.content }

    @Test
    fun `adding a record logs a create`() {
        Storage.write("Tasks", tasks(task(1, "Renew passport")))

        val events = History.recent(key = "Tasks")
        assertEquals(1, events.size)
        assertEquals(Change.CREATE, events[0].change)
        assertEquals("1", events[0].rec)
        assertEquals("items", events[0].coll)
        assertEquals("Renew passport", events[0].label)
    }

    @Test
    fun `editing one field logs only that field`() {
        Storage.write("Tasks", tasks(task(1, "Renew passport")))
        Storage.write("Tasks", tasks(task(1, "Renew passport", done = true)))

        val edit = History.recent(key = "Tasks").first()
        assertEquals(Change.UPDATE, edit.change)
        assertEquals(setOf("done"), edit.before.keys)
        assertEquals("false", edit.before["done"])
        assertEquals("true", edit.after["done"])
    }

    @Test
    fun `a burst of edits collapses to one event that still knows the original`() {
        Storage.write("Notes", """[{"id":"a","text":""}]""")
        listOf("H", "He", "Hel", "Hell", "Hello").forEach {
            Storage.write("Notes", """[{"id":"a","text":"$it"}]""")
        }

        val edits = History.recent(key = "Notes").filter { it.change == Change.UPDATE }
        assertEquals(1, edits.size, "five keystrokes should be one entry, not five")
        assertEquals("\"\"", edits[0].before["text"], "undo must go back to the start of the burst")
        assertEquals("\"Hello\"", edits[0].after["text"])
        // A root-level array records an empty collection name.
        assertEquals("", edits[0].coll)
    }

    @Test
    fun `two records edited in the same save still collapse to one entry each`() {
        Storage.write("Tasks", tasks(task(1, "a"), task(2, "b")))
        (1..4).forEach { n ->
            Storage.write("Tasks", tasks(task(1, "a$n"), task(2, "b$n")))
        }

        val edits = History.recent(key = "Tasks").filter { it.change == Change.UPDATE }
        assertEquals(2, edits.size, "one entry per record, not one per save")
        assertEquals(setOf("1", "2"), edits.map { it.rec }.toSet())
        assertTrue(edits.all { it.before["title"] in setOf("\"a\"", "\"b\"") })
    }

    @Test
    fun `undo puts an edited field back`() {
        Storage.write("Tasks", tasks(task(1, "Bye bank")))
        Storage.write("Tasks", tasks(task(1, "Buy bread")))

        val edit = History.recent(key = "Tasks").first()
        assertTrue(History.undo(edit))
        assertEquals(listOf("Bye bank"), titles("Tasks"))
    }

    @Test
    fun `a deleted record goes to the trash and comes back whole`() {
        Storage.write("Tasks", tasks(task(1, "Keep me"), task(2, "Delete me")))
        Storage.write("Tasks", tasks(task(1, "Keep me")))

        val trashed = History.trash()
        assertEquals(1, trashed.size)
        assertEquals("Delete me", trashed[0].label)

        assertTrue(History.restore(trashed[0]))
        assertEquals(listOf("Keep me", "Delete me"), titles("Tasks"))
        // Every field survives, not just the label.
        val back = read("Tasks")["items"]!!.jsonArray
            .first { it.jsonObject["id"]!!.jsonPrimitive.content == "2" }.jsonObject
        assertEquals(false, back["done"]!!.jsonPrimitive.content.toBoolean())

        // Restoring clears it from the trash, because the delete is no longer the
        // record's newest event.
        assertTrue(History.trash().none { it.rec == "2" })
    }

    @Test
    fun `undoing a create removes the record`() {
        Storage.write("Tasks", tasks(task(1, "First")))
        Storage.write("Tasks", tasks(task(1, "First"), task(2, "Second")))

        val created = History.recent(key = "Tasks").first { it.rec == "2" }
        assertTrue(History.undo(created))
        assertEquals(listOf("First"), titles("Tasks"))
    }

    @Test
    fun `removing a whole key logs one delete per record`() {
        Storage.write("Tasks", tasks(task(1, "One"), task(2, "Two")))
        Storage.remove("Tasks")

        assertEquals(2, History.trash().count { it.key == "Tasks" })
        // Restoring into a key that no longer exists rebuilds the blob around it.
        assertTrue(History.restore(History.trash().first { it.rec == "1" }))
        assertEquals(listOf("One"), titles("Tasks"))
    }

    @Test
    fun `blobAt replays back to what a record said earlier`() {
        Storage.write("Tasks", tasks(task(1, "Draft")))
        val mark = History.recent(key = "Tasks").first().at
        Thread.sleep(5)
        // Past the coalesce window would need a real wait; a second record is enough to
        // prove the walk, since events are reversed by timestamp not by adjacency.
        Storage.write("Tasks", tasks(task(1, "Draft"), task(2, "Later")))

        val then = History.blobAt("Tasks", mark)
        assertNotNull(then)
        val rows = then.jsonObject["items"]!!.jsonArray
        assertEquals(1, rows.size, "the second record did not exist yet")
        assertEquals("Draft", rows[0].jsonObject["title"]!!.jsonPrimitive.content)
        // The live store is untouched by looking at the past.
        assertEquals(listOf("Draft", "Later"), titles("Tasks"))
    }

    @Test
    fun `settings and secrets are not logged`() {
        Storage.write("ThemeMode", "dark")
        Storage.write("ApiKey", "sk-secret")
        Storage.write("__scratch", tasks(task(9, "internal")))

        assertTrue(History.all().none { it.key == "ThemeMode" })
        assertTrue(History.all().none { it.key == "ApiKey" })
        assertTrue(History.all().none { it.key == "__scratch" })
    }

    @Test
    fun `records with no id are left alone rather than guessed at`() {
        Storage.write("Tasks", """{"items":[{"title":"no id here"}]}""")
        assertTrue(History.all().none { it.key == "Tasks" })
        assertNull(Storage.read("__history")?.takeIf { it.contains("no id here") })
    }
}
