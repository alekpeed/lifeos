package com.alekpeed.lifeos.recipes

import com.alekpeed.lifeos.Storage
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

// Recipes — ported from the web app's Recipes view: a recipe carries a title,
// base servings, tags, notes, an ingredient list (name / qty / unit) that scales
// to a chosen serving count, ordered steps, and a cook log ("Made it!" with a
// date + optional notes). A second tab combines ingredients across chosen
// recipes into one grocery list. Persists as one JSON blob under "Recipes"; old
// plain-line stubs migrate to titled recipes.

@Serializable
data class Ingredient(val id: Long, val name: String, val qty: String = "", val unit: String = "")

@Serializable
data class Step(val id: Long, val text: String)

@Serializable
data class CookLog(val id: Long, val date: String, val notes: String = "")

@Serializable
data class Recipe(
    val id: Long,
    val title: String,
    val baseServings: Int = 4,
    val tags: List<String> = emptyList(),
    val notes: String = "",
    val ingredients: List<Ingredient> = emptyList(),
    val steps: List<Step> = emptyList(),
    val cookLogs: List<CookLog> = emptyList(),
)

@Serializable
data class RecipesData(val recipes: List<Recipe> = emptyList())

// Round to 2 decimals, drop trailing zeros: 1.50 -> "1.5", 2.00 -> "2".
fun formatQty(n: Double): String {
    val scaled = kotlin.math.round(n * 100) / 100.0
    return if (scaled % 1.0 == 0.0) scaled.toLong().toString() else scaled.toString()
}

// Scaled quantity string for an ingredient at a factor; non-numeric qty passes through.
fun scaleQty(qty: String, factor: Double): String {
    val n = qty.trim().toDoubleOrNull() ?: return qty
    return formatQty(n * factor)
}

private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

fun loadRecipes(): RecipesData {
    val raw = Storage.read("Recipes")
    if (raw.isNullOrBlank()) return RecipesData()
    if (raw.trimStart().startsWith("{")) {
        return runCatching { json.decodeFromString<RecipesData>(raw) }.getOrElse { RecipesData() }
    }
    // Migrate the old NoteListScreen stub ("<title>\t<note>" per line).
    val recipes = raw.lines().filter { it.isNotBlank() }.mapIndexed { i, line ->
        val parts = line.split("\t", limit = 2)
        Recipe(id = i + 1L, title = parts[0].trim(), notes = parts.getOrElse(1) { "" })
    }
    return RecipesData(recipes)
}

fun saveRecipes(data: RecipesData) {
    Storage.write("Recipes", json.encodeToString(data))
}
