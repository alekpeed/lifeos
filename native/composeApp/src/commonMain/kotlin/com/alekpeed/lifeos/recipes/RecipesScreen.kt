package com.alekpeed.lifeos.recipes

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.alekpeed.lifeos.data.today
import com.alekpeed.lifeos.platform.Native
import com.alekpeed.lifeos.ui.SaveToast
import com.alekpeed.lifeos.ui.usDate

private val DANGER = Color(0xFFD64545)

@Composable
fun RecipesScreen() {
    var data by remember { mutableStateOf(loadRecipes()) }
    var counter by remember {
        mutableStateOf(
            maxOf(
                data.recipes.maxOfOrNull { it.id } ?: 0L,
                data.recipes.flatMap { it.ingredients }.maxOfOrNull { it.id } ?: 0L,
                data.recipes.flatMap { it.steps }.maxOfOrNull { it.id } ?: 0L,
                data.recipes.flatMap { it.cookLogs }.maxOfOrNull { it.id } ?: 0L,
            ),
        )
    }
    fun freshId(): Long { counter += 1; return counter }
    fun save(d: RecipesData) { data = d; saveRecipes(d); SaveToast.show() }

    var tab by remember { mutableStateOf("recipes") }
    var selected by remember { mutableStateOf<Long?>(null) }
    var input by remember { mutableStateOf("") }

    Column(Modifier.fillMaxSize().padding(20.dp)) {
        Text("Recipes", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = tab == "recipes", onClick = { tab = "recipes" }, label = { Text("Recipes") })
            FilterChip(selected = tab == "grocery", onClick = { tab = "grocery"; selected = null }, label = { Text("Grocery list") })
        }
        Spacer(Modifier.height(12.dp))

        if (tab == "grocery") {
            GroceryList(data)
            return@Column
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(input, { input = it }, modifier = Modifier.weight(1f), singleLine = true, placeholder = { Text("New recipe") })
            Spacer(Modifier.width(10.dp))
            Button(onClick = {
                val t = input.trim().replace("\n", " ")
                if (t.isNotEmpty()) { save(data.copy(recipes = data.recipes + Recipe(freshId(), t))); input = "" }
            }) { Text("Add") }
        }
        Spacer(Modifier.height(12.dp))

        if (data.recipes.isEmpty()) { Muted("No recipes yet."); return@Column }
        LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(data.recipes, key = { it.id }) { r ->
                Column {
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .clickable { selected = if (selected == r.id) null else r.id }.padding(14.dp),
                    ) {
                        Text("🍳", modifier = Modifier.padding(end = 10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(r.title.ifBlank { "(untitled)" }, style = MaterialTheme.typography.bodyLarge)
                            FlowRowChips(buildList {
                                add("Serves ${r.baseServings}")
                                if (r.cookLogs.isNotEmpty()) add("Cooked ${r.cookLogs.size}×")
                                r.tags.forEach { add("#$it") }
                            })
                        }
                    }
                    if (selected == r.id) RecipeDetail(data, ::save, ::freshId, r) { selected = null }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RecipeDetail(
    data: RecipesData,
    save: (RecipesData) -> Unit,
    freshId: () -> Long,
    recipe: Recipe,
    onClose: () -> Unit,
) {
    fun patch(f: (Recipe) -> Recipe) = save(data.copy(recipes = data.recipes.map { if (it.id == recipe.id) f(it) else it }))
    var scale by remember(recipe.id) { mutableStateOf(recipe.baseServings) }
    var cooking by remember { mutableStateOf(false) }
    var logNotes by remember { mutableStateOf("") }
    var newIngName by remember { mutableStateOf("") }
    var newIngQty by remember { mutableStateOf("") }
    var newIngUnit by remember { mutableStateOf("") }
    var newStep by remember { mutableStateOf("") }
    val factor = if (recipe.baseServings > 0) scale.toDouble() / recipe.baseServings else 1.0

    Panel {
        if (Native.supportsKeepAwake) {
            OutlinedButton(onClick = { cooking = !cooking; Native.keepScreenAwake(cooking) }) {
                Text(if (cooking) "🍳 Cooking mode: on" else "🍳 Cooking mode")
            }
        }
        Label("Title")
        Field(recipe.title, "Title") { v -> patch { it.copy(title = v.replace("\n", " ")) } }
        Label("Base servings")
        Field(recipe.baseServings.toString(), "4") { v -> v.toIntOrNull()?.let { n -> patch { it.copy(baseServings = n) } } }
        Label("Tags (comma separated)")
        Field(recipe.tags.joinToString(", "), "comfort, quick") { v ->
            patch { it.copy(tags = v.split(",").map { t -> t.trim() }.filter { t -> t.isNotEmpty() }) }
        }
        Label("Notes")
        Field(recipe.notes, "Notes", singleLine = false) { v -> patch { it.copy(notes = v) } }

        Label("Ingredients — scale to servings")
        Row(verticalAlignment = Alignment.CenterVertically) {
            AssistChip(onClick = { if (scale > 1) scale -= 1 }, label = { Text("−") })
            Spacer(Modifier.width(8.dp))
            Text("$scale", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.width(8.dp))
            AssistChip(onClick = { scale += 1 }, label = { Text("+") })
            if (scale != recipe.baseServings) {
                Spacer(Modifier.width(8.dp))
                TextButton(onClick = { scale = recipe.baseServings }) { Text("Reset") }
            }
        }
        recipe.ingredients.forEach { ing ->
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(ing.name, style = MaterialTheme.typography.bodyMedium)
                    val base = listOf(ing.qty, ing.unit).filter { it.isNotBlank() }.joinToString(" ")
                    val scaled = if (factor != 1.0) "  →  ${scaleQty(ing.qty, factor)} ${ing.unit}".trimEnd() else ""
                    if (base.isNotBlank() || scaled.isNotBlank()) {
                        Text(
                            base + scaled, style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                TextButton(onClick = { patch { it.copy(ingredients = it.ingredients.filterNot { x -> x.id == ing.id }) } }) { Text("×") }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(newIngName, { newIngName = it }, modifier = Modifier.weight(2f), singleLine = true, placeholder = { Text("Ingredient") })
            Spacer(Modifier.width(6.dp))
            OutlinedTextField(newIngQty, { newIngQty = it }, modifier = Modifier.weight(1f), singleLine = true, placeholder = { Text("Qty") })
            Spacer(Modifier.width(6.dp))
            OutlinedTextField(newIngUnit, { newIngUnit = it }, modifier = Modifier.weight(1f), singleLine = true, placeholder = { Text("Unit") })
        }
        Button(onClick = {
            val n = newIngName.trim()
            if (n.isNotEmpty()) {
                patch { it.copy(ingredients = it.ingredients + Ingredient(freshId(), n, newIngQty.trim(), newIngUnit.trim())) }
                newIngName = ""; newIngQty = ""; newIngUnit = ""
            }
        }) { Text("Add ingredient") }

        Label("Steps")
        recipe.steps.forEachIndexed { i, step ->
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("${i + 1}. ${step.text}", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                TextButton(onClick = { patch { it.copy(steps = it.steps.filterNot { x -> x.id == step.id }) } }) { Text("×") }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(newStep, { newStep = it }, modifier = Modifier.weight(1f), singleLine = true, placeholder = { Text("Add a step") })
            Spacer(Modifier.width(8.dp))
            Button(onClick = {
                val t = newStep.trim()
                if (t.isNotEmpty()) { patch { it.copy(steps = it.steps + Step(freshId(), t)) }; newStep = "" }
            }) { Text("Add") }
        }

        Label("Cook log — cooked ${recipe.cookLogs.size}×")
        recipe.cookLogs.sortedByDescending { it.date }.forEach { log ->
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(usDate(log.date).ifBlank { log.date }, style = MaterialTheme.typography.bodyMedium)
                    if (log.notes.isNotBlank()) Text(log.notes, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                TextButton(onClick = { patch { it.copy(cookLogs = it.cookLogs.filterNot { x -> x.id == log.id }) } }) { Text("×") }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(logNotes, { logNotes = it }, modifier = Modifier.weight(1f), singleLine = true, placeholder = { Text("Notes (optional)") })
            Spacer(Modifier.width(8.dp))
            Button(onClick = {
                patch { it.copy(cookLogs = it.cookLogs + CookLog(freshId(), today().toString(), logNotes.trim())) }
                logNotes = ""
            }) { Text("Made it!") }
        }

        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onClose) { Text("Done") }
            Spacer(Modifier.weight(1f))
            TextButton(onClick = { save(data.copy(recipes = data.recipes.filterNot { it.id == recipe.id })); onClose() }) {
                Text("Delete recipe", color = DANGER)
            }
        }
    }
}

// ---------- Grocery list ----------

private data class GroceryTotal(val name: String, val unit: String, var qty: Double, var exact: Boolean, var raw: String)

@Composable
private fun GroceryList(data: RecipesData) {
    val checked = remember { mutableStateMapOf<Long, Boolean>() }
    val servings = remember { mutableStateMapOf<Long, Int>() }

    Muted("Check the recipes to shop for and set servings — the list below combines every ingredient.")
    Spacer(Modifier.height(10.dp))

    if (data.recipes.isEmpty()) { Spacer(Modifier.height(6.dp)); Muted("No recipes yet."); return }

    Column(Modifier.fillMaxWidth()) {
        data.recipes.forEach { r ->
            val isOn = checked[r.id] ?: false
            val serv = servings[r.id] ?: r.baseServings
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = isOn, onCheckedChange = { checked[r.id] = it })
                Text(r.title.ifBlank { "(untitled)" }, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                AssistChip(onClick = { if (serv > 1) servings[r.id] = serv - 1 }, label = { Text("−") })
                Spacer(Modifier.width(6.dp))
                Text("$serv", style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.width(6.dp))
                AssistChip(onClick = { servings[r.id] = serv + 1 }, label = { Text("+") })
            }
        }
    }

    // Combine ingredients from every checked recipe, scaled to its chosen servings.
    val totals = LinkedHashMap<String, GroceryTotal>()
    data.recipes.forEach { r ->
        if (checked[r.id] != true) return@forEach
        val factor = if (r.baseServings > 0) (servings[r.id] ?: r.baseServings).toDouble() / r.baseServings else 1.0
        r.ingredients.forEach { ing ->
            val key = "${ing.name.lowercase()}|${ing.unit.lowercase()}"
            val n = ing.qty.trim().toDoubleOrNull()
            val existing = totals[key]
            if (n != null) {
                if (existing != null && existing.exact) existing.qty += n * factor
                else totals[key] = GroceryTotal(ing.name, ing.unit, n * factor, true, "")
            } else if (existing == null) {
                totals[key] = GroceryTotal(ing.name, ing.unit, 0.0, false, ing.qty)
            }
        }
    }

    Spacer(Modifier.height(12.dp))
    Text("Combined grocery list", style = MaterialTheme.typography.titleSmall)
    Spacer(Modifier.height(6.dp))
    if (totals.isEmpty()) {
        Muted("Select at least one recipe above.")
    } else {
        LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            items(totals.values.toList()) { t ->
                Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(t.name, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                    Text(
                        if (t.exact) listOf(formatQty(t.qty), t.unit).filter { it.isNotBlank() }.joinToString(" ") else t.raw,
                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

// ---------- shared ----------

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FlowRowChips(chips: List<String>) {
    if (chips.isEmpty()) return
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        chips.forEach { Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary) }
    }
}

@Composable
private fun Panel(content: @Composable () -> Unit) {
    Column(
        Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 8.dp)
            .clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.surfaceVariant).padding(12.dp),
    ) { content() }
}

@Composable
private fun Label(text: String) {
    Spacer(Modifier.height(10.dp))
    Text(text, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Spacer(Modifier.height(4.dp))
}

@Composable
private fun Field(value: String, placeholder: String, singleLine: Boolean = true, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value, onValueChange = onChange, modifier = Modifier.fillMaxWidth(),
        singleLine = singleLine, placeholder = { Text(placeholder) },
    )
}

@Composable
private fun Muted(text: String) {
    Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}
