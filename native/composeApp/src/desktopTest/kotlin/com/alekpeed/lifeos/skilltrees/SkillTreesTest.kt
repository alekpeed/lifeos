package com.alekpeed.lifeos.skilltrees

import com.alekpeed.lifeos.Storage
import com.alekpeed.lifeos.TestHome
import com.alekpeed.lifeos.data.plusDays
import com.alekpeed.lifeos.data.today
import com.alekpeed.lifeos.habits.Habit
import com.alekpeed.lifeos.habits.saveHabits
import com.alekpeed.lifeos.history.History
import com.alekpeed.lifeos.tasks.Task
import com.alekpeed.lifeos.tasks.saveTasks
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

// §5.2. The whole design rests on one wall: activity feeds a Standing's RANK, and
// nothing feeds a Skill's LEVEL except a benchmark being met. Most of what is worth
// testing is that the wall holds, that the seeded three still compute what they always
// did, and that linked habits contribute hours without double-counting a day.
class SkillTreesTest {

    @BeforeTest
    fun setUp() {
        TestHome.clear()
        History.forget()
    }

    @AfterTest
    fun tearDown() {
        TestHome.clear()
    }

    // ---- Tier 1 ----

    @Test
    fun `the rank curve is the one the module always used`() {
        assertEquals(1, rankOf(0))
        assertEquals(1, rankOf(9))
        assertEquals(2, rankOf(10))
        assertEquals(4, rankOf(90))
        assertEquals(0, xpForRank(1))
        assertEquals(10, xpForRank(2))
        assertEquals(90, xpForRank(4))
    }

    @Test
    fun `an empty store still shows the three standings it always had`() {
        val data = loadStandings()
        assertEquals(listOf("Executor", "Discipline", "Scholar"), data.standings.map { it.name })
    }

    @Test
    fun `the seeded standings compute what the old hardcoded ones did`() {
        saveTasks(List(4) { Task(it + 1L, "t$it", status = "done") })
        saveHabits(
            listOf(Habit("Practice", setOf(today(), today().plusDays(-1), today().plusDays(-2)))),
        )

        val counts = activityCounts()
        val executor = loadStandings().standings.first { it.name == "Executor" }
        val discipline = loadStandings().standings.first { it.name == "Discipline" }

        assertEquals(40, standingXp(executor, counts), "4 tasks at 10 XP, as before")
        assertEquals(15, standingXp(discipline, counts), "3 check-ins at 5 XP, as before")
    }

    @Test
    fun `deleting a standing does not bring it back on the next open`() {
        val data = loadStandings()
        saveStandings(data.copy(standings = data.standings.filterNot { it.name == "Scholar" }))
        assertEquals(listOf("Executor", "Discipline"), loadStandings().standings.map { it.name })
    }

    @Test
    fun `a standing says what its number is made of, and skips what it isn't`() {
        saveTasks(List(2) { Task(it + 1L, "t$it", status = "done") })
        val counts = activityCounts()
        val executor = loadStandings().standings.first { it.name == "Executor" }

        val blurb = standingBlurb(executor, counts)
        assertTrue(blurb.contains("2 tasks completed"))
        assertFalse(blurb.contains("assignments"), "a source contributing nothing is not listed as zero")
    }

    @Test
    fun `a rung can be named, and falls back to a number past the end of the list`() {
        val s = Standing(1, "Cook", rankNames = listOf("Line", "Sous"))
        assertEquals("Line", rankLabel(s, 1))
        assertEquals("Sous", rankLabel(s, 2))
        assertEquals("Rank 3", rankLabel(s, 3))
    }

    // ---- Tier 2 ----

    @Test
    fun `nothing but a benchmark moves a level`() {
        val skill = Skill(1, "Guitar", levelScale = listOf("Beginner", "Intermediate"))
        var data = SkillsData(
            skills = listOf(skill),
            benchmarks = listOf(Benchmark(1, 1, "F barre at 80bpm", targetLevel = 1)),
        )
        // Piling up practice does not touch it.
        data = data.copy(
            logs = List(50) { PracticeLog(it + 1L, 1, today().toString(), 60) },
        )
        assertEquals(0, data.skills.single().currentLevel)

        data = achieveBenchmark(data, 1)
        assertEquals(1, data.skills.single().currentLevel)
        assertTrue(data.benchmarks.single().achieved)
        assertEquals(today().toString(), data.benchmarks.single().achievedDate)
    }

    @Test
    fun `a benchmark never drags a level backwards`() {
        var data = SkillsData(
            skills = listOf(Skill(1, "Guitar", currentLevel = 4)),
            benchmarks = listOf(Benchmark(1, 1, "an old one", targetLevel = 2)),
        )
        data = achieveBenchmark(data, 1)
        assertEquals(4, data.skills.single().currentLevel, "meeting an earlier benchmark is not a demotion")
    }

    @Test
    fun `a level reads by the skill's own scale`() {
        val s = Skill(1, "Japanese", currentLevel = 2, levelScale = listOf("N5", "N4", "N3"))
        assertEquals("N3", s.levelName())
        assertEquals("Level 9", Skill(2, "Welding", currentLevel = 9).levelName())
    }

    @Test
    fun `hours separate what was logged from what was inherited`() {
        saveHabits(listOf(Habit("Guitar daily", setOf(today().plusDays(-1), today().plusDays(-2)))))
        val skill = Skill(1, "Guitar", habitNames = listOf("Guitar daily"), minutesPerCheckin = 20)
        val data = SkillsData(
            skills = listOf(skill),
            logs = listOf(PracticeLog(1, 1, today().toString(), 45)),
        )

        val h = hoursFor(skill, data)
        assertEquals(45, h.logged)
        assertEquals(40, h.habits, "two check-ins at 20 minutes")
        assertEquals(85, h.total)
    }

    @Test
    fun `a day with a written session ignores the habit check-in for the same day`() {
        val day = today().plusDays(-1)
        saveHabits(listOf(Habit("Guitar daily", setOf(day))))
        val skill = Skill(1, "Guitar", habitNames = listOf("Guitar daily"), minutesPerCheckin = 30)
        val data = SkillsData(
            skills = listOf(skill),
            logs = listOf(PracticeLog(1, 1, day.toString(), 90)),
        )

        val h = hoursFor(skill, data)
        assertEquals(90, h.total, "the same session recorded twice is what this avoids")
        assertEquals(0, h.habits)
    }

    @Test
    fun `practice hours can feed a standing, which is allowed in that direction only`() {
        saveSkills(
            SkillsData(
                skills = listOf(Skill(1, "Guitar")),
                logs = listOf(PracticeLog(1, 1, today().toString(), 180)),
            ),
        )
        assertEquals(3, activityCounts()[SourceKind.PRACTICE_HOURS], "whole hours only")

        // And the reverse never happens: a standing's XP is nowhere in a skill's level.
        val skill = loadSkills().skills.single()
        assertEquals(0, skill.currentLevel)
    }

    @Test
    fun `a skill with no practice is not cold, it is unstarted`() {
        val skill = Skill(1, "Guitar")
        val f = freshnessOf(skill, SkillsData(listOf(skill)))
        assertFalse(f.stale)
        assertNull(f.lastPracticed)
        assertNull(f.daysSince)
    }

    @Test
    fun `a long gap goes cold, and a paused skill is exempt`() {
        val long_ago = today().plusDays(-60)
        val skill = Skill(1, "Guitar")
        val data = SkillsData(listOf(skill), logs = listOf(PracticeLog(1, 1, long_ago.toString(), 60)))

        val cold = freshnessOf(skill, data)
        assertTrue(cold.stale)
        assertEquals(60, cold.daysSince)

        val paused = freshnessOf(skill.copy(active = false), data)
        assertFalse(paused.stale, "put down on purpose is not going cold")
        assertTrue(paused.exempt)
    }

    @Test
    fun `practising on consecutive days climbs the ladder`() {
        val skill = Skill(1, "Guitar")
        // Four sessions on consecutive days, ending today.
        val logs = (0..3).map { i ->
            PracticeLog(i + 1L, 1, today().plusDays(-3 + i).toString(), 30)
        }
        val f = freshnessOf(skill, SkillsData(listOf(skill), logs = logs))
        assertFalse(f.stale)
        assertTrue(f.freshness.intervalDays > PRACTICE_LADDER.first(), "the rung should have advanced")
    }

    @Test
    fun `a streak counts consecutive practice days`() {
        val skill = Skill(1, "Guitar")
        val logs = (0..2).map { i -> PracticeLog(i + 1L, 1, today().plusDays(-i).toString(), 20) }
        assertEquals(3, practiceStreak(skill, SkillsData(listOf(skill), logs = logs)))
    }

    @Test
    fun `deleting a skill takes its sessions but promotes its sub-skills`() {
        val data = SkillsData(
            skills = listOf(Skill(1, "Guitar"), Skill(2, "Barre chords", parentId = 1)),
            logs = listOf(PracticeLog(1, 1, today().toString(), 30), PracticeLog(2, 2, today().toString(), 15)),
            benchmarks = listOf(Benchmark(1, 1, "b", 1)),
        )
        val after = deleteSkill(data, 1)

        assertEquals(listOf("Barre chords"), after.skills.map { it.name })
        assertNull(after.skills.single().parentId, "a sub-skill outlives its parent")
        assertEquals(listOf(2L), after.logs.map { it.skillId })
        assertTrue(after.benchmarks.isEmpty())
    }

    @Test
    fun `the tree roots a sub-skill under its parent`() {
        val data = SkillsData(
            skills = listOf(Skill(1, "Guitar"), Skill(2, "Barre chords", parentId = 1), Skill(3, "Japanese")),
        )
        assertEquals(listOf("Guitar", "Japanese"), skillRoots(data).map { it.name })
        assertEquals(
            listOf("Barre chords"),
            skillSummaries(data).first { it.skill.id == 1L }.children.map { it.name },
        )
    }

    @Test
    fun `the two tiers store separately`() {
        saveStandings(loadStandings())
        saveSkills(SkillsData(listOf(Skill(1, "Guitar"))))
        assertTrue(Storage.read("Skill Trees")!!.contains("Executor"))
        assertTrue(Storage.read("Skills")!!.contains("Guitar"))
        assertFalse(Storage.read("Skill Trees")!!.contains("Guitar"))
    }
}
