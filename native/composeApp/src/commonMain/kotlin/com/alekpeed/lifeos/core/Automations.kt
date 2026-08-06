package com.alekpeed.lifeos.core

import com.alekpeed.lifeos.Storage
import com.alekpeed.lifeos.data.today
import com.alekpeed.lifeos.documents.ExpiryState
import com.alekpeed.lifeos.documents.expiryState
import com.alekpeed.lifeos.documents.loadDocuments
import com.alekpeed.lifeos.habits.loadHabits
import com.alekpeed.lifeos.milestones.Milestone
import com.alekpeed.lifeos.milestones.loadMilestones
import com.alekpeed.lifeos.milestones.saveMilestones
import com.alekpeed.lifeos.tasks.Task
import com.alekpeed.lifeos.tasks.loadTasks
import com.alekpeed.lifeos.tasks.saveTasks

// Rules & automation engine — a small fixed set of built-in rules, off by default
// (Settings > Automations), run on app open. Both mutate your own data on your
// behalf, so both are idempotent — re-running never double-fires, because each
// checks for the record it would create before creating it.
//   1. Habit streak crosses 7 / 30 / 100 / 365 days  → log a Milestone.
//   2. A Document is expiring or expired             → create a "Renew: <title>" Task.
private val STREAK_THRESHOLDS = listOf(365, 100, 30, 7)

fun automationsEnabled(): Boolean = Storage.read("AutomationsOn") == "1"

fun runAutomations() {
    if (!automationsEnabled()) return

    // Rule 1 — habit streak milestones.
    val habits = loadHabits()
    val md = loadMilestones()
    val existing = md.milestones.map { it.title }.toMutableSet()
    val newMilestones = mutableListOf<Milestone>()
    var mid = md.milestones.maxOfOrNull { it.id } ?: 0L
    habits.forEach { h ->
        STREAK_THRESHOLDS.forEach { thr ->
            if (h.streak >= thr) {
                val title = "🔥 ${h.name}: $thr-day streak"
                if (existing.add(title)) { mid += 1; newMilestones.add(Milestone(mid, title, today().toString(), "Habit")) }
            }
        }
    }
    if (newMilestones.isNotEmpty()) saveMilestones(md.copy(milestones = md.milestones + newMilestones))

    // Rule 2 — document-renewal tasks.
    val docs = loadDocuments().documents
    val tasks = loadTasks()
    val taskTitles = tasks.map { it.title }.toMutableSet()
    val newTasks = mutableListOf<Task>()
    var tid = tasks.maxOfOrNull { it.id } ?: 0L
    docs.forEach { d ->
        val st = expiryState(d)
        if (st == ExpiryState.EXPIRED || st == ExpiryState.SOON) {
            val title = "Renew: ${d.title.ifBlank { "document" }}"
            if (taskTitles.add(title)) { tid += 1; newTasks.add(Task(tid, title)) }
        }
    }
    if (newTasks.isNotEmpty()) saveTasks(tasks + newTasks)
}
