package com.alekpeed.lifeos.tasks

// A task record. Mirrors the fields the existing app uses; persistence and the
// richer fields (due date, priority, project) get layered in as the data layer
// lands — this first pass proves the native module pattern end to end.
data class Task(
    val id: Long,
    val title: String,
    val done: Boolean = false,
)
