package com.alekpeed.lifeos.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.AssistChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.alekpeed.lifeos.tags.formatTags
import com.alekpeed.lifeos.tags.parseTags
import com.alekpeed.lifeos.tags.suggestTags

// The one tag input (W-03). Seven modules had seven comma boxes and no shared
// vocabulary; this replaces all of them.
//
// Still a comma-separated box, because that is what everything already stores and what
// the modules' own edit forms look like. What it adds is the vocabulary: as you type the
// last tag, the tags already used anywhere in the app appear as chips, and tapping one
// completes it. That is what actually stops "work", "Work" and "wrok" from becoming
// three tags — not validation after the fact.
@Composable
fun TagField(
    tags: List<String>,
    placeholder: String = "comma, separated",
    modifier: Modifier = Modifier,
    onChange: (List<String>) -> Unit,
) {
    // Held as text while editing so a trailing comma survives — reformatting from the
    // parsed list on every keystroke would delete the separator as you typed it.
    var text by remember(tags) { mutableStateOf(formatTags(tags)) }

    // The fragment after the last comma is the tag being typed.
    val fragment = text.substringAfterLast(",")
    val committed = parseTags(text.substringBeforeLast(",", ""))
    // Only suggest once there is something to go on, or when the box is empty and the
    // most-used tags are a genuine offer rather than noise under every form field.
    val suggestions = remember(fragment, committed, text) {
        if (fragment.isBlank() && text.isNotBlank()) emptyList()
        else suggestTags(fragment, exclude = committed)
    }

    Column(modifier) {
        OutlinedTextField(
            value = text,
            onValueChange = {
                text = it
                onChange(parseTags(it))
            },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            placeholder = { Text(placeholder) },
        )
        if (suggestions.isNotEmpty()) {
            Spacer(Modifier.height(4.dp))
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                suggestions.forEach { s ->
                    AssistChip(
                        onClick = {
                            val next = committed + s
                            text = formatTags(next) + ", "
                            onChange(next)
                        },
                        label = { Text(s, style = MaterialTheme.typography.labelMedium) },
                    )
                }
            }
        }
    }
}
