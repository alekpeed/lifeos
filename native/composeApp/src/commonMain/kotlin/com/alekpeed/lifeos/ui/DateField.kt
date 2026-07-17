package com.alekpeed.lifeos.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.alekpeed.lifeos.data.parseDateOrNull
import com.alekpeed.lifeos.data.today

// Dates are stored ISO (yyyy-MM-dd) everywhere for the date math, but shown and
// typed American — MM-DD-YYYY. This is the one shared date input: it formats the
// display, auto-inserts hyphens as you type, and offers a year→month→day picker
// so you jump straight to a year instead of paging months.

private val MONTHS = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")

// ISO (yyyy-MM-dd) → US display (MM-DD-YYYY). Blank/invalid → "".
fun usDate(iso: String): String {
    val d = parseDateOrNull(iso) ?: return ""
    val mm = d.monthNumber.toString().padStart(2, '0')
    val dd = d.dayOfMonth.toString().padStart(2, '0')
    return "$mm-$dd-${d.year}"
}

private fun daysInMonth(year: Int, month: Int): Int = when (month) {
    1, 3, 5, 7, 8, 10, 12 -> 31
    4, 6, 9, 11 -> 30
    else -> if (year % 4 == 0 && (year % 100 != 0 || year % 400 == 0)) 29 else 28
}

// Progressively hyphenate up to 8 digits: MM-DD-YYYY.
private fun formatDigits(d: String): String = when {
    d.length <= 2 -> d
    d.length <= 4 -> d.substring(0, 2) + "-" + d.substring(2)
    else -> d.substring(0, 2) + "-" + d.substring(2, 4) + "-" + d.substring(4)
}

// 8 digits MMDDYYYY → ISO, or null if it isn't a real date.
private fun digitsToIso(d: String): String? {
    if (d.length != 8) return null
    val mm = d.substring(0, 2).toIntOrNull() ?: return null
    val dd = d.substring(2, 4).toIntOrNull() ?: return null
    val yyyy = d.substring(4, 8).toIntOrNull() ?: return null
    if (mm !in 1..12 || dd !in 1..31) return null
    val iso = "${yyyy.toString().padStart(4, '0')}-${mm.toString().padStart(2, '0')}-${dd.toString().padStart(2, '0')}"
    return if (parseDateOrNull(iso) != null) iso else null
}

private fun buildIso(year: Int, month: Int, day: Int): String =
    "${year.toString().padStart(4, '0')}-${month.toString().padStart(2, '0')}-${day.toString().padStart(2, '0')}"

@Composable
fun DateField(
    value: String,                 // ISO (yyyy-MM-dd) or ""
    label: String? = null,
    modifier: Modifier = Modifier,
    onChange: (String) -> Unit,    // ISO or ""
) {
    var showPicker by remember { mutableStateOf(false) }
    // Re-derive the display whenever the stored value changes from outside.
    var text by remember(value) { mutableStateOf(usDate(value)) }

    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(
            value = text,
            onValueChange = { raw ->
                val digits = raw.filter { it.isDigit() }.take(8)
                text = formatDigits(digits)
                if (digits.isEmpty()) {
                    onChange("")
                } else {
                    val iso = digitsToIso(digits)
                    if (iso != null) onChange(iso)
                }
            },
            label = label?.let { { Text(it) } },
            placeholder = { Text("MM-DD-YYYY") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(8.dp))
        Box(
            Modifier.clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .clickable { showPicker = true }
                .padding(horizontal = 12.dp, vertical = 12.dp),
        ) {
            Text("📅", style = MaterialTheme.typography.titleMedium)
        }
    }

    if (showPicker) {
        DatePickerDialog(
            initial = value,
            onDismiss = { showPicker = false },
            onPick = { iso -> text = usDate(iso); onChange(iso); showPicker = false },
        )
    }
}

@Composable
private fun DatePickerDialog(initial: String, onDismiss: () -> Unit, onPick: (String) -> Unit) {
    val start = parseDateOrNull(initial) ?: today()
    var step by remember { mutableStateOf(0) } // 0=year, 1=month, 2=day
    var year by remember { mutableStateOf(start.year) }
    var month by remember { mutableStateOf(start.monthNumber) }

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
        title = {
            val label = when (step) {
                0 -> "Pick a year"
                1 -> "$year · pick a month"
                else -> "${MONTHS[month - 1]} $year · pick a day"
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (step > 0) {
                    Text(
                        "‹ ",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.clip(RoundedCornerShape(6.dp)).clickable { step -= 1 }.padding(4.dp),
                    )
                }
                Text(label, style = MaterialTheme.typography.titleMedium)
            }
        },
        text = {
            when (step) {
                0 -> YearGrid(selected = year) { year = it; step = 1 }
                1 -> MonthGrid(selected = month) { month = it; step = 2 }
                else -> DayGrid(year, month, selected = if (initial == buildIso(year, month, start.dayOfMonth)) start.dayOfMonth else -1) { day ->
                    onPick(buildIso(year, month, day))
                }
            }
        },
    )
}

@Composable
private fun YearGrid(selected: Int, onPick: (Int) -> Unit) {
    val top = today().year + 3
    val years = (top downTo 1940).toList()
    val state = rememberLazyGridState()
    LaunchedEffect(Unit) {
        val idx = years.indexOf(selected).coerceAtLeast(0)
        state.scrollToItem((idx - 4).coerceAtLeast(0))
    }
    LazyVerticalGrid(
        columns = GridCells.Fixed(4),
        state = state,
        modifier = Modifier.fillMaxWidth().height(260.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(years) { y -> Cell(y.toString(), selected = y == selected) { onPick(y) } }
    }
}

@Composable
private fun MonthGrid(selected: Int, onPick: (Int) -> Unit) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(3),
        modifier = Modifier.fillMaxWidth().height(200.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(MONTHS) { name ->
            val m = MONTHS.indexOf(name) + 1
            Cell(name, selected = m == selected) { onPick(m) }
        }
    }
}

@Composable
private fun DayGrid(year: Int, month: Int, selected: Int, onPick: (Int) -> Unit) {
    val days = (1..daysInMonth(year, month)).toList()
    LazyVerticalGrid(
        columns = GridCells.Fixed(7),
        modifier = Modifier.fillMaxWidth().height(240.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        items(days) { d -> Cell(d.toString(), selected = d == selected) { onPick(d) } }
    }
}

@Composable
private fun Cell(text: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .aspectRatio(1.3f)
            .clip(RoundedCornerShape(8.dp))
            .background(if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
        )
    }
}
