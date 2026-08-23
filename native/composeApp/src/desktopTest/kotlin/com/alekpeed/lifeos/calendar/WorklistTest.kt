package com.alekpeed.lifeos.calendar

import com.alekpeed.lifeos.Storage
import com.alekpeed.lifeos.TestHome
import com.alekpeed.lifeos.data.plusDays
import com.alekpeed.lifeos.data.today
import com.alekpeed.lifeos.documents.Document
import com.alekpeed.lifeos.documents.DocumentsData
import com.alekpeed.lifeos.documents.saveDocuments
import com.alekpeed.lifeos.history.History
import com.alekpeed.lifeos.tasks.Task
import com.alekpeed.lifeos.tasks.saveTasks
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

// §12.1.1. The point of one query is that Briefing, Daily Paper and Today cannot
// disagree about the same record — so what is worth pinning down is the shared rule:
// which horizon applies to what, and what counts as owed at all.
class WorklistTest {

    @BeforeTest
    fun setUp() {
        TestHome.clear()
        History.forget()
    }

    @AfterTest
    fun tearDown() {
        TestHome.clear()
    }

    @Test
    fun `an overdue item never falls out of the window`() {
        saveTasks(listOf(Task(1, "Ancient", due = today().plusDays(-400).toString())))
        val list = datedWorklist(days = 7)
        assertEquals(listOf("Ancient"), list.map { it.title })
        assertTrue(list.single().isOverdue())
    }

    @Test
    fun `overdue sorts above due-soon regardless of module`() {
        saveTasks(
            listOf(
                Task(1, "Due Friday", due = today().plusDays(3).toString()),
                Task(2, "Was due Monday", due = today().plusDays(-2).toString()),
            ),
        )
        assertEquals(listOf("Was due Monday", "Due Friday"), datedWorklist().map { it.title })
    }

    @Test
    fun `a finished task is not owed`() {
        saveTasks(
            listOf(
                Task(1, "Done", due = today().toString(), status = "done"),
                Task(2, "Not done", due = today().toString()),
            ),
        )
        assertEquals(listOf("Not done"), datedWorklist().map { it.title })
    }

    @Test
    fun `documents use their own horizon, not the general one`() {
        Storage.write("DocExpiryDays", "60")
        saveDocuments(
            DocumentsData(
                listOf(
                    Document(1, "Passport", expiryDate = today().plusDays(45).toString()),
                    Document(2, "Warranty", expiryDate = today().plusDays(120).toString()),
                ),
            ),
        )
        // 45 days out is inside the document window even though it is well past the
        // seven-day general one; 120 is outside both.
        assertEquals(listOf("Passport"), datedWorklist(days = 7).map { it.title })
    }

    @Test
    fun `birthdays and milestones stay off the worklist`() {
        Storage.write(
            "Contacts",
            """{"contacts":[{"id":1,"name":"Sara","birthday":"${today().toString().substring(5)}"}]}""",
        )
        Storage.write("Milestones", """{"milestones":[{"id":1,"title":"Anniversary","date":"${today()}"}]}""")

        assertTrue(datedWorklist().isEmpty(), "a birthday is not owed")
        // They are still on the calendar itself, which is the point of the split.
        val cal = datedItems(today(), today())
        assertTrue(cal.any { it.moduleId == "contacts" })
        assertTrue(cal.any { it.moduleId == "milestones" })
    }

    @Test
    fun `every source that has a record id reports it`() {
        saveTasks(listOf(Task(9, "A task", due = today().toString())))
        saveDocuments(DocumentsData(listOf(Document(4, "A doc", expiryDate = today().toString()))))

        val byModule = datedWorklist().associateBy { it.moduleId }
        assertEquals(9L, byModule["tasks"]?.recordId)
        assertEquals(4L, byModule["documents"]?.recordId)
    }
}
