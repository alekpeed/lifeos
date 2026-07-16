package com.alekpeed.lifeos.ideas

// A free-form capture note. Persistence + archive/tags land with the data layer.
data class Idea(
    val id: Long,
    val text: String,
)
