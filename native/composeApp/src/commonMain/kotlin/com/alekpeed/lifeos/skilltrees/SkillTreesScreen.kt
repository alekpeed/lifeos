package com.alekpeed.lifeos.skilltrees

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.alekpeed.lifeos.books.loadBooks
import com.alekpeed.lifeos.education.loadEducation
import com.alekpeed.lifeos.habits.loadHabits
import com.alekpeed.lifeos.tasks.loadTasks
import kotlin.math.floor
import kotlin.math.sqrt

// Skill Trees — an RPG character sheet computed entirely from activity elsewhere
// (tasks/assignments, habits, books). No storage of its own; ported from the web
// view. Level curve: level = floor(sqrt(xp/10)) + 1 — fast early, slower later.

private data class Skill(val name: String, val icon: String, val xp: Int, val blurb: String)

private fun levelOf(xp: Int): Int = floor(sqrt(xp / 10.0)).toInt() + 1
private fun xpForLevel(level: Int): Int = 10 * (level - 1) * (level - 1)

@Composable
fun SkillTreesScreen() {
    val skills = remember {
        val doneTasks = loadTasks().count { it.status == "done" }
        val doneAssignments = loadEducation().assignments.count { it.done }
        val done = doneTasks + doneAssignments
        val habits = loadHabits()
        val checkIns = habits.sumOf { it.checkins.size }
        val booksFinished = loadBooks().books.count { it.status == "finished" }
        fun plural(n: Int, w: String) = "$n $w${if (n == 1) "" else "s"}"
        listOf(
            Skill("Executor", "✅", done * 10, "${plural(done, "task/assignment")} completed"),
            Skill("Discipline", "🔥", checkIns * 5, "${plural(habits.size, "habit")}, ${plural(checkIns, "check-in")} logged"),
            Skill("Scholar", "📖", booksFinished * 40, "${plural(booksFinished, "book")} finished"),
        )
    }
    val totalLevel = skills.sumOf { levelOf(it.xp) }

    Column(Modifier.fillMaxSize().padding(20.dp)) {
        Text("Skill Trees", style = MaterialTheme.typography.headlineMedium)
        Text("Character level $totalLevel — computed from what you've actually been doing.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(16.dp))
        skills.forEach { SkillBar(it) }
    }
}

@Composable
private fun SkillBar(skill: Skill) {
    val level = levelOf(skill.xp)
    val thisLevel = xpForLevel(level)
    val nextLevel = xpForLevel(level + 1)
    val span = nextLevel - thisLevel
    val pct = if (span > 0) (((skill.xp - thisLevel).toFloat() / span).coerceIn(0f, 1f)) else 1f

    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(skill.icon, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(end = 12.dp))
        Column(Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(skill.name, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                Text("Lv. $level", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            }
            Spacer(Modifier.height(4.dp))
            Box(Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)).background(MaterialTheme.colorScheme.surfaceVariant)) {
                Box(Modifier.fillMaxWidth(pct).height(8.dp).clip(RoundedCornerShape(4.dp)).background(MaterialTheme.colorScheme.primary))
            }
            Spacer(Modifier.height(2.dp))
            Text(skill.blurb, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
