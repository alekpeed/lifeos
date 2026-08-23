package com.alekpeed.lifeos.projects

import com.alekpeed.lifeos.TestHome
import com.alekpeed.lifeos.calendar.datedItems
import com.alekpeed.lifeos.data.plusDays
import com.alekpeed.lifeos.data.today
import com.alekpeed.lifeos.history.History
import com.alekpeed.lifeos.tasks.Task
import com.alekpeed.lifeos.tasks.loadTasks
import com.alekpeed.lifeos.tasks.saveTasks
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

// The migration is the risky half of W-04: it rewrites every task that had a project
// name typed into it. It has to be idempotent, it has to survive a device that has not
// run it yet, and it must never take a project away.
class ProjectsTest {

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
    fun `free text project names become records and the tasks point at them`() {
        saveTasks(
            listOf(
                Task(1, "Strip wallpaper", project = "Home renovation"),
                Task(2, "Get quotes", project = "Home renovation"),
                Task(3, "Renew passport", project = "Admin"),
                Task(4, "Buy milk"),
            ),
        )

        assertEquals(3, migrateProjectStrings())

        val projects = loadProjects().projects
        assertEquals(setOf("Home renovation", "Admin"), projects.map { it.name }.toSet())

        val tasks = loadTasks()
        val reno = projects.first { it.name == "Home renovation" }
        assertEquals(listOf(reno.id, reno.id), tasks.filter { it.id in listOf(1L, 2L) }.map { it.projectId })
        // The old string is cleared, so there is only one answer to which project it is in.
        assertTrue(tasks.all { it.project.isBlank() })
        assertNull(tasks.first { it.id == 4L }.projectId)
    }

    @Test
    fun `running the migration again changes nothing`() {
        saveTasks(listOf(Task(1, "A", project = "Thing")))
        migrateProjectStrings()
        val after = loadProjects().projects

        assertEquals(0, migrateProjectStrings())
        assertEquals(after.map { it.id to it.name }, loadProjects().projects.map { it.id to it.name })
    }

    @Test
    fun `a task from a device that has not migrated joins the existing project`() {
        saveTasks(listOf(Task(1, "A", project = "Thing")))
        migrateProjectStrings()
        val id = loadProjects().projects.single().id

        // Arrives later carrying the old free-text field, and in a different case.
        saveTasks(loadTasks() + Task(2, "B", project = "thing"))
        migrateProjectStrings()

        assertEquals(1, loadProjects().projects.size, "a second record for the same name would split the project")
        assertEquals(id, loadTasks().first { it.id == 2L }.projectId)
    }

    @Test
    fun `migration never removes a project that came from elsewhere`() {
        // What a sync from the other device looks like: projects arrive, tasks do not.
        saveProjects(ProjectsData(listOf(Project(7, "From the phone")), migrated = false))
        saveTasks(listOf(Task(1, "Local", project = "Local work")))

        migrateProjectStrings()

        assertTrue(loadProjects().projects.any { it.id == 7L })
        assertEquals(2, loadProjects().projects.size)
    }

    @Test
    fun `progress counts only the project's own tasks`() {
        saveProjects(ProjectsData(listOf(Project(1, "Reno"))))
        saveTasks(
            listOf(
                Task(1, "a", projectId = 1, status = "done"),
                Task(2, "b", projectId = 1),
                Task(3, "c"),
            ),
        )

        val p = loadProjects().projects.single()
        val progress = projectProgress(p)
        assertEquals(2, progress.total)
        assertEquals(1, progress.done)
        assertEquals(0.5f, progress.fraction)
        assertFalse(progress.complete)
    }

    @Test
    fun `deleting a project releases its tasks instead of taking them along`() {
        saveProjects(ProjectsData(listOf(Project(1, "Reno"))))
        saveTasks(listOf(Task(1, "Keep me", projectId = 1), Task(2, "Me too", projectId = 1)))

        assertEquals(2, deleteProject(1))

        assertEquals(2, loadTasks().size, "the tasks must survive")
        assertTrue(loadTasks().all { it.projectId == null })
        assertTrue(loadProjects().projects.isEmpty())
    }

    @Test
    fun `finishing a project does not fake-complete its open tasks`() {
        saveProjects(ProjectsData(listOf(Project(1, "Reno"))))
        saveTasks(listOf(Task(1, "Still open", projectId = 1)))

        completeProject(1)

        val p = loadProjects().projects.single()
        assertEquals(ProjectStatus.DONE, p.status)
        assertEquals(today().toString(), p.completedDate)
        assertFalse(loadTasks().single().done, "a finished project with an open task is information, not a bug")
    }

    @Test
    fun `a target date shows up in the shared calendar query`() {
        val when0 = today().plusDays(3)
        saveProjects(ProjectsData(listOf(Project(1, "Ship it", targetDate = when0.toString()))))

        val items = datedItems(today(), today().plusDays(7))
        val hit = items.single { it.key == "project-1" }
        assertEquals("Ship it", hit.title)
        assertEquals(when0, hit.date)
        assertEquals("projects", hit.moduleId)
    }

    @Test
    fun `an archived project stays out of the calendar`() {
        saveProjects(
            ProjectsData(
                listOf(
                    Project(1, "Old", targetDate = today().toString(), status = ProjectStatus.ARCHIVED),
                ),
            ),
        )
        assertTrue(datedItems(today(), today()).none { it.key == "project-1" })
    }

    @Test
    fun `a project is late only while it is still open`() {
        val past = today().plusDays(-2).toString()
        assertTrue(Project(1, "Late", targetDate = past).overdue())
        assertFalse(Project(1, "Done", targetDate = past, status = ProjectStatus.DONE).overdue())
        assertFalse(Project(1, "No date").overdue())
    }

    @Test
    fun `a task keeps reading correctly before the migration reaches it`() {
        val projects = listOf(Project(1, "Reno"))
        assertEquals("Reno", projectNameOf(Task(1, "a", projectId = 1), projects))
        // Not migrated yet: the legacy string is still the answer.
        assertEquals("Old name", projectNameOf(Task(2, "b", project = "Old name"), projects))
        assertEquals("", projectNameOf(Task(3, "c"), projects))
    }
}
