package com.alekpeed.lifeos.tags

import com.alekpeed.lifeos.TestHome
import com.alekpeed.lifeos.history.History
import com.alekpeed.lifeos.links.Link
import com.alekpeed.lifeos.links.LinksData
import com.alekpeed.lifeos.links.loadLinks
import com.alekpeed.lifeos.links.saveLinks
import com.alekpeed.lifeos.people.Contact
import com.alekpeed.lifeos.people.ContactsData
import com.alekpeed.lifeos.people.loadContacts
import com.alekpeed.lifeos.people.saveContacts
import com.alekpeed.lifeos.tasks.Task
import com.alekpeed.lifeos.tasks.loadTasks
import com.alekpeed.lifeos.tasks.saveTasks
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

// A rename here rewrites seven modules at once, so the thing worth proving is that it
// touches exactly what it should: the right records, in every module, without losing a
// tag that was already there or leaving the same tag on a record twice.
class TagsTest {

    @BeforeTest
    fun setUp() {
        TestHome.clear()
        History.forget()
    }

    @AfterTest
    fun tearDown() {
        TestHome.clear()
    }

    private fun seed() {
        saveTasks(
            listOf(
                Task(1, "Book flights", tags = listOf("travel", "iceland")),
                Task(2, "Renew passport", tags = listOf("travel", "admin")),
            ),
        )
        saveLinks(
            LinksData(
                listOf(
                    Link(1, "https://example.com/a", title = "Ring road", tags = listOf("iceland")),
                    Link(2, "https://example.com/b", title = "Tax guide", tags = listOf("Admin")),
                ),
            ),
        )
        saveContacts(ContactsData(listOf(Contact(1, "Sara", tags = listOf("iceland")))))
    }

    @Test
    fun `a tag is cleaned up the same way wherever it is typed`() {
        assertEquals("work", canonicalTag("  #work  "))
        assertEquals("side project", canonicalTag("side   project"))
        assertEquals("a b", canonicalTag("a,b"))
        assertEquals("", canonicalTag("   "))
    }

    @Test
    fun `one record never carries the same tag twice`() {
        assertEquals(listOf("work", "gym"), parseTags("work, gym"))
        assertEquals(listOf("work"), parseTags("work, Work, #work"))
        assertEquals(listOf("work", "gym"), parseTags("  work ,, gym , "))
    }

    @Test
    fun `the vocabulary counts across every module that has tags`() {
        seed()
        val index = tagIndex()

        val iceland = index.first { it.tag == "iceland" }
        assertEquals(3, iceland.count)
        assertEquals(listOf("Tasks", "Links", "Contacts"), iceland.sources)
        assertTrue(iceland.crossModule)

        // Most used first.
        assertEquals("iceland", index.first().tag)
    }

    @Test
    fun `a tag lists everything carrying it regardless of module`() {
        seed()
        val hits = taggedWith("iceland")
        assertEquals(3, hits.size)
        assertEquals(
            listOf("Book flights", "Ring road", "Sara"),
            hits.map { it.label },
        )
    }

    @Test
    fun `two spellings of one tag are reported rather than silently folded`() {
        seed()
        val clashes = tagClashes()
        assertEquals(1, clashes.size)
        assertEquals(setOf("admin", "Admin"), clashes[0].spellings.map { it.tag }.toSet())
        // Both are used once here, so either could win — what matters is that merging
        // leaves exactly one spelling behind.
        val keep = clashes[0].preferred.tag
        clashes[0].spellings.filter { it.tag != keep }.forEach { renameTag(it.tag, keep) }
        assertTrue(tagClashes().isEmpty())
        assertEquals(2, tagIndex().first { it.tag == keep }.count)
    }

    @Test
    fun `renaming rewrites every module at once`() {
        seed()
        assertEquals(3, renameTag("iceland", "Iceland 2027"))

        assertTrue(loadTasks().first { it.id == 1L }.tags.contains("Iceland 2027"))
        assertTrue(loadLinks().links.first { it.id == 1L }.tags.contains("Iceland 2027"))
        assertTrue(loadContacts().contacts.first().tags.contains("Iceland 2027"))
        assertTrue(taggedWith("iceland").isEmpty())
        // The record's other tags are untouched.
        assertEquals(listOf("travel", "Iceland 2027"), loadTasks().first { it.id == 1L }.tags)
    }

    @Test
    fun `renaming onto an existing tag merges without duplicating it`() {
        saveTasks(listOf(Task(1, "Both", tags = listOf("errand", "chores"))))
        assertEquals(1, renameTag("errand", "chores"))
        assertEquals(listOf("chores"), loadTasks().first().tags)
    }

    @Test
    fun `deleting a tag leaves the records alone`() {
        seed()
        assertEquals(2, deleteTag("travel"))

        assertEquals(2, loadTasks().size)
        assertEquals(listOf("iceland"), loadTasks().first { it.id == 1L }.tags)
        assertTrue(tagIndex().none { it.tag == "travel" })
    }

    @Test
    fun `renaming to nothing is refused rather than wiping the tag`() {
        seed()
        assertEquals(0, renameTag("travel", "   "))
        assertEquals(2, taggedWith("travel").size)
    }

    @Test
    fun `suggestions prefer a prefix match and skip what is already on the record`() {
        seed()
        assertEquals(listOf("iceland"), suggestTags("ice"))
        // Substring matches come after prefix matches.
        assertTrue(suggestTags("a").indexOf("admin") < suggestTags("a").indexOf("travel"))
        // Already applied to this record, so not offered again.
        assertTrue(suggestTags("ice", exclude = listOf("Iceland")).isEmpty())
        // Nothing typed yet: the most-used tags, as an offer.
        assertEquals("iceland", suggestTags("").first())
    }

    @Test
    fun `a rename is recorded in history so it can be undone`() {
        seed()
        renameTag("iceland", "reykjavik")
        val edits = History.recent(key = "Tasks").filter { it.change == com.alekpeed.lifeos.history.Change.UPDATE }
        assertTrue(edits.any { it.before["tags"]?.contains("iceland") == true })
    }
}
