package com.alekpeed.lifeos.calendar

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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.alekpeed.lifeos.Nav
import com.alekpeed.lifeos.data.plusDays
import com.alekpeed.lifeos.data.relativeLabel
import com.alekpeed.lifeos.data.today
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.isoDayNumber

// The agenda. One month at a time, every dated record in the app on it, and the picked
// day's items underneath — all of it read through `datedItems`, so this is a view over
// the shared query rather than a fifth walk over the same modules (§12.1.1).

private val MONTHS = listOf(
    "January", "February", "March", "April", "May", "June",
    "July", "August", "September", "October", "November", "December",
)
private val DOW = listOf("M", "T", "W", "T", "F", "S", "S")

private fun daysInMonth(year: Int, month: Int): Int = when (month) {
    1, 3, 5, 7, 8, 10, 12 -> 31
    4, 6, 9, 11 -> 30
    else -> if (year % 4 == 0 && (year % 100 != 0 || year % 400 == 0)) 29 else 28
}

@Composable
fun CalendarScreen() {
    val now = today()
    var year by remember { mutableStateOf(now.year) }
    var month by remember { mutableStateOf(now.monthNumber) }
    var selected by remember { mutableStateOf(now) }

    val first = LocalDate(year, month, 1)
    val length = daysInMonth(year, month)
    val last = LocalDate(year, month, length)

    // Re-read on every month change so edits made elsewhere show up on return.
    val items = remember(year, month) { datedItems(first, last) }
    val byDay = remember(items) { items.groupBy { it.date.dayOfMonth } }

    fun shift(by: Int) {
        val m = month + by
        when {
            m < 1 -> { month = 12; year -= 1 }
            m > 12 -> { month = 1; year += 1 }
            else -> month = m
        }
    }

    Column(Modifier.fillMaxWidth().padding(16.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            NavArrow("‹") { shift(-1) }
            Text(
                "${MONTHS[month - 1]} $year",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.weight(1f).padding(horizontal = 12.dp),
                textAlign = TextAlign.Center,
            )
            NavArrow("›") { shift(1) }
        }

        if (year != now.year || month != now.monthNumber) {
            TextButton(onClick = { year = now.year; month = now.monthNumber; selected = now }) { Text("Back to today") }
        }

        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth()) {
            DOW.forEach { d ->
                Text(
                    d,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f),
                )
            }
        }
        Spacer(Modifier.height(4.dp))

        // Weeks start Monday, so a month beginning on Thursday needs three blanks.
        val lead = first.dayOfWeek.isoDayNumber - 1
        val cells = lead + length
        val rows = (cells + 6) / 7
        for (r in 0 until rows) {
            Row(Modifier.fillMaxWidth()) {
                for (c in 0 until 7) {
                    val dayNum = r * 7 + c - lead + 1
                    if (dayNum < 1 || dayNum > length) {
                        Box(Modifier.weight(1f).aspectRatio(1f))
                    } else {
                        val date = LocalDate(year, month, dayNum)
                        DayCell(
                            day = dayNum,
                            count = byDay[dayNum].orEmpty().size,
                            overdue = byDay[dayNum].orEmpty().any { it.isOverdue(now) },
                            isToday = date == now,
                            isSelected = date == selected,
                            modifier = Modifier.weight(1f),
                        ) { selected = date }
                    }
                }
            }
        }

        Spacer(Modifier.height(16.dp))
        Text(
            "${MONTHS[selected.monthNumber - 1]} ${selected.dayOfMonth} · ${relativeLabel(selected, now)}",
            style = MaterialTheme.typography.titleMedium,
        )
        Spacer(Modifier.height(6.dp))

        val dayItems = byDay[selected.dayOfMonth].orEmpty().takeIf { selected.monthNumber == month }.orEmpty()
        if (dayItems.isEmpty()) {
            Text(
                "Nothing on this day.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                items(dayItems, key = { it.key }) { item -> AgendaRow(item, now) }
            }
        }
    }
}

@Composable
private fun NavArrow(glyph: String, onClick: () -> Unit) {
    Box(
        Modifier.clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
    ) {
        Text(glyph, style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
private fun DayCell(
    day: Int,
    count: Int,
    overdue: Boolean,
    isToday: Boolean,
    isSelected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val bg = when {
        isSelected -> MaterialTheme.colorScheme.primary
        isToday -> MaterialTheme.colorScheme.surfaceVariant
        else -> MaterialTheme.colorScheme.surface
    }
    val fg = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
    Box(
        modifier.aspectRatio(1f).padding(2.dp).clip(RoundedCornerShape(8.dp))
            .background(bg).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                day.toString(),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal,
                color = fg,
            )
            // Density, not a count of dots — a busy day reads as busy without becoming
            // a row of specks that mean nothing individually.
            if (count > 0) {
                Spacer(Modifier.height(2.dp))
                Box(
                    Modifier.size(if (count > 2) 7.dp else 5.dp).clip(CircleShape).background(
                        when {
                            isSelected -> MaterialTheme.colorScheme.onPrimary
                            overdue -> MaterialTheme.colorScheme.error
                            else -> MaterialTheme.colorScheme.primary
                        },
                    ),
                )
            }
        }
    }
}

@Composable
private fun AgendaRow(item: DatedItem, now: LocalDate) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp))
            .clickable { Nav.open(item.moduleId) }.padding(vertical = 8.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(item.icon, style = MaterialTheme.typography.bodyLarge)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(
                item.title.ifBlank { "(untitled)" },
                style = MaterialTheme.typography.bodyLarge,
                color = if (item.done) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
            )
            val meta = item.meta()
            if (meta.isNotBlank()) {
                Text(
                    meta,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (item.isOverdue(now)) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (item.isOverdue(now)) {
            Text("overdue", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
        }
    }
}
