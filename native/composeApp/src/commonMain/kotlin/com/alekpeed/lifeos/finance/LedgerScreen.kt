package com.alekpeed.lifeos.finance

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.alekpeed.lifeos.Storage
import com.alekpeed.lifeos.ai.AiClient
import com.alekpeed.lifeos.data.epochMillisAt
import com.alekpeed.lifeos.data.minusDays
import com.alekpeed.lifeos.data.parseDateOrNull
import com.alekpeed.lifeos.data.plusDays
import com.alekpeed.lifeos.data.relativeLabel
import com.alekpeed.lifeos.data.today
import com.alekpeed.lifeos.integrations.CoinPrice
import com.alekpeed.lifeos.integrations.MarketsClient
import com.alekpeed.lifeos.platform.Native
import com.alekpeed.lifeos.platform.deleteBlob
import com.alekpeed.lifeos.platform.loadBlobImage
import com.alekpeed.lifeos.platform.saveBlob
import androidx.compose.ui.layout.ContentScale
import com.alekpeed.lifeos.ui.DateField
import com.alekpeed.lifeos.ui.usDate
import com.alekpeed.lifeos.ui.SaveToast
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.plus
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.math.abs

private val CATEGORIES = listOf("General", "Bills", "Food", "Transport", "Fun", "Income")

@Serializable
private data class Entry(
    val id: Long,
    val desc: String,
    val amount: Double,
    val category: String = "General",
    val recurring: Boolean = false,
    val date: String = "",
    val photoBlob: String = "",   // blob-store id of the scanned receipt, if any
)

@Serializable
private data class Payment(val date: String, val amount: Double)

@Serializable
private data class Bill(
    val id: Long,
    val name: String,
    val amount: Double,
    val dueDate: String = "",
    val cadence: String = "monthly", // weekly | monthly | yearly | one-time
    val autopay: Boolean = false,
    val remindDays: Int = 3,
    val category: String = "Bills",
    val paymentHistory: List<Payment> = emptyList(),
    val contact: String = "",
    val attachments: List<com.alekpeed.lifeos.attach.Attachment> = emptyList(), // bill PDFs, statements
)

@Serializable
private data class Subscription(
    val id: Long,
    val name: String,
    val amount: Double,
    val cycle: String = "monthly", // weekly | monthly | yearly
    val active: Boolean = true,
    val category: String = "Subscriptions",
    val renewalDate: String = "",  // next renewal / bill date, ISO
    val notes: String = "",
)

@Serializable
private data class FinanceData(
    val entries: List<Entry> = emptyList(),
    val bills: List<Bill> = emptyList(),
    val subscriptions: List<Subscription> = emptyList(),
)

private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

private fun fmt(amount: Double): String {
    val sign = if (amount < 0) "-" else ""
    val cents = (abs(amount) * 100).toLong()
    return "$sign$${cents / 100}.${(cents % 100).toString().padStart(2, '0')}"
}

private fun loadData(): FinanceData {
    val raw = Storage.read("Finance")
    if (raw.isNullOrBlank()) return FinanceData()
    if (raw.trimStart().startsWith("{")) {
        return runCatching { json.decodeFromString<FinanceData>(raw) }.getOrElse { FinanceData() }
    }
    // Migrate old "desc\tamount\tcategory\trecurring" lines (undated) into entries.
    val entries = raw.lines().filter { it.isNotBlank() }.mapIndexed { i, line ->
        val p = line.split("\t")
        Entry(
            id = i + 1L,
            desc = p.getOrElse(0) { line },
            amount = p.getOrElse(1) { "0" }.toDoubleOrNull() ?: 0.0,
            category = p.getOrElse(2) { "General" }.ifBlank { "General" },
            recurring = p.getOrElse(3) { "0" } == "1",
        )
    }
    return FinanceData(entries = entries)
}

private fun saveData(data: FinanceData) {
    Storage.write("Finance", json.encodeToString(data))
    SaveToast.show()
}

// Public read-only accessor for the stats layer (The Almanac): each ledger entry
// as (amount, category, recurring, date) without leaking the private model.
data class FinancePoint(val desc: String, val amount: Double, val category: String, val recurring: Boolean, val date: String)
fun financeSeries(): List<FinancePoint> = loadData().entries.map { FinancePoint(it.desc, it.amount, it.category, it.recurring, it.date) }

// Public read-only accessor for the attention feed (Notifications): each bill's
// due facts without leaking the private model. `settled` = a one-time bill that
// has already been paid (recurring bills advance their due date instead).
data class BillPoint(val name: String, val amount: Double, val dueDate: String, val autopay: Boolean, val settled: Boolean)
fun financeBills(): List<BillPoint> = loadData().bills.map {
    BillPoint(it.name, it.amount, it.dueDate, it.autopay, it.cadence == "one-time" && it.paymentHistory.isNotEmpty())
}

// Every logged bill payment as (date, amount) — the yearly recap's "bills paid".
data class PaymentPoint(val date: String, val amount: Double)
fun financeBillPayments(): List<PaymentPoint> =
    loadData().bills.flatMap { b -> b.paymentHistory.map { PaymentPoint(it.date, it.amount) } }

// Create a bill from outside (the Command bar). Monthly by default; schedules
// the due reminder like the Bills tab does.
fun financeAddBill(name: String, amount: Double, dueDate: String) {
    val data = loadData()
    val bill = Bill((data.bills.maxOfOrNull { it.id } ?: 0L) + 1, name.trim(), amount, dueDate)
    saveData(data.copy(bills = listOf(bill) + data.bills))
    scheduleBill(bill)
}

private val CADENCES = listOf("weekly", "monthly", "yearly", "one-time")
private val CYCLES = listOf("weekly", "monthly", "yearly")
private val REMIND_OPTIONS = listOf(0, 1, 3, 7)

private fun advanceDue(dateStr: String, cadence: String): String {
    val d = parseDateOrNull(dateStr) ?: today()
    val next = when (cadence) {
        "weekly" -> d.plusDays(7)
        "yearly" -> d.plus(1, DateTimeUnit.YEAR)
        "one-time" -> return dateStr
        else -> d.plus(1, DateTimeUnit.MONTH)
    }
    return next.toString()
}

private fun monthlyEquiv(amount: Double, cycle: String): Double = when (cycle) {
    "weekly" -> amount * 52.0 / 12.0
    "yearly" -> amount / 12.0
    else -> amount
}

private fun yearlyEquiv(amount: Double, cycle: String): Double = when (cycle) {
    "weekly" -> amount * 52.0
    "monthly" -> amount * 12.0
    else -> amount
}

private fun billReminderId(name: String): Int = (name + "bill").hashCode()

private fun scheduleBill(bill: Bill) {
    if (!Native.supportsNotifications) return
    val due = parseDateOrNull(bill.dueDate) ?: return
    if (bill.cadence == "one-time" && bill.paymentHistory.isNotEmpty()) return
    Native.scheduleReminder(
        id = billReminderId(bill.name),
        title = "Bill due: ${bill.name}",
        body = "${fmt(bill.amount)} due ${bill.dueDate}" + if (bill.autopay) " (autopay)" else "",
        atEpochMillis = epochMillisAt(due.minusDays(bill.remindDays), 9, 0),
    )
}

// Finance — a four-tab money hub. Ledger is the running balance and log;
// Bills tracks recurring/one-time obligations with due dates, autopay, and a
// paid-history that advances the next due date and reschedules the reminder;
// Subscriptions normalizes weekly/monthly/yearly costs to a combined monthly
// spend; Yearly Spend annualizes logged bill payments plus active subscriptions
// by category.
@Composable
fun FinanceScreen() {
    var data by remember { mutableStateOf(loadData()) }
    fun persist(next: FinanceData) { data = next; saveData(next) }
    var tab by remember { mutableStateOf(0) }
    val tabs = listOf("Ledger", "Bills", "Subs", "Yearly", "Markets", "Import")

    Column(Modifier.fillMaxSize()) {
        ScrollableTabRow(selectedTabIndex = tab, edgePadding = 0.dp) {
            tabs.forEachIndexed { i, title ->
                Tab(selected = tab == i, onClick = { tab = i }, text = { Text(title) })
            }
        }
        when (tab) {
            0 -> LedgerTab(data) { persist(it) }
            1 -> BillsTab(data) { persist(it) }
            2 -> SubscriptionsTab(data) { persist(it) }
            3 -> YearlyTab(data)
            4 -> MarketsTab()
            else -> ImportTab(data) { persist(it) }
        }
    }
}

private const val RECEIPT_SYSTEM =
    "You read a photo of a store or restaurant receipt. Respond with ONLY a JSON object and nothing else: " +
        "{\"merchant\": string, \"amount\": number, \"date\": \"YYYY-MM-DD\"}. \"amount\" is the grand total " +
        "as a positive number. Use \"\" for merchant if unclear and \"\" for date if not visible. Never invent values."

// Parse the receipt JSON into a spending ledger entry (amount stored negative).
private fun parseReceipt(raw: String): Entry? {
    val start = raw.indexOf('{')
    val end = raw.lastIndexOf('}')
    if (start < 0 || end <= start) return null
    val obj = try { json.parseToJsonElement(raw.substring(start, end + 1)).jsonObject } catch (e: Exception) { return null }
    val amt = obj["amount"]?.jsonPrimitive?.content?.toDoubleOrNull() ?: return null
    val merchant = (obj["merchant"]?.jsonPrimitive?.content ?: "").trim().replace("\n", " ").ifBlank { "Receipt" }
    val date = (obj["date"]?.jsonPrimitive?.content ?: "").trim()
    val iso = if (parseDateOrNull(date) != null) date else today().toString()
    return Entry(0L, merchant, -abs(amt), "General", false, iso)
}

// A money ledger with categories, dates, and a recurring flag. All-time balance
// and a live this-month income / spending / net summary up top, then a
// per-category breakdown. Marking an entry recurring schedules a real reminder
// ~30 days out (Android). Negative amounts are spending.
@Composable
private fun LedgerTab(data: FinanceData, onChange: (FinanceData) -> Unit) {
    val entries = data.entries
    fun persist(next: List<Entry>) { onChange(data.copy(entries = next)) }
    var nextId by remember { mutableStateOf((entries.maxOfOrNull { it.id } ?: 0L) + 1) }
    var desc by remember { mutableStateOf("") }
    var amount by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("General") }
    var recurring by remember { mutableStateOf(false) }
    var scanning by remember { mutableStateOf(false) }
    var scanErr by remember { mutableStateOf<String?>(null) }
    var showSource by remember { mutableStateOf(false) }
    var previewBlob by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    fun onReceipt(b64: String?) {
        when {
            b64 == null -> {}
            b64.isEmpty() -> scanErr = "Couldn't read that image — try another photo."
            else -> {
                scanErr = null; scanning = true
                scope.launch {
                    val reply = AiClient.askWithImage(RECEIPT_SYSTEM, "Extract the receipt total, merchant, and date.", b64, 400)
                    scanning = false
                    if (reply.isError) { scanErr = reply.text; return@launch }
                    val e = parseReceipt(reply.text)
                    if (e == null) { scanErr = "Couldn't read a total off that receipt."; return@launch }
                    // Keep the source photo so the entry has a receipt of record.
                    val blobId = saveBlob(b64) ?: ""
                    persist(listOf(e.copy(id = nextId, photoBlob = blobId)) + entries); nextId += 1
                }
            }
        }
    }

    val balance = entries.sumOf { it.amount }
    val month = today().toString().take(7)
    val monthEntries = entries.filter { it.date.startsWith(month) }
    val monthIncome = monthEntries.filter { it.amount > 0 }.sumOf { it.amount }
    val monthSpend = monthEntries.filter { it.amount < 0 }.sumOf { it.amount }
    val byCategory = entries.groupBy { it.category }
        .map { (cat, es) -> cat to es.sumOf { it.amount } }
        .sortedByDescending { abs(it.second) }

    Column(Modifier.fillMaxSize().padding(20.dp)) {
        Text(
            "Balance ${fmt(balance)}",
            style = MaterialTheme.typography.titleLarge,
            color = if (balance < 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.height(8.dp))
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(MaterialTheme.colorScheme.surfaceVariant).padding(12.dp),
        ) {
            MonthStat("This month in", fmt(monthIncome), MaterialTheme.colorScheme.primary, Modifier.weight(1f))
            MonthStat("Out", fmt(monthSpend), MaterialTheme.colorScheme.error, Modifier.weight(1f))
            MonthStat("Net", fmt(monthIncome + monthSpend), if (monthIncome + monthSpend < 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary, Modifier.weight(1f))
        }
        Spacer(Modifier.height(14.dp))

        OutlinedTextField(desc, { desc = it }, modifier = Modifier.fillMaxWidth(), singleLine = true, placeholder = { Text("What was it?") })
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                amount, { amount = it }, modifier = Modifier.weight(1f), singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), placeholder = { Text("Amount (– to spend)") },
            )
            Spacer(Modifier.width(10.dp))
            Button(onClick = {
                val d = desc.trim().replace("\n", " ")
                val a = amount.trim().toDoubleOrNull()
                if (d.isNotEmpty() && a != null) {
                    val e = Entry(nextId, d, a, category, recurring, today().toString())
                    nextId += 1
                    persist(listOf(e) + entries)
                    if (recurring && Native.supportsNotifications) {
                        Native.scheduleReminder(
                            id = (d + "recur").hashCode(),
                            title = "Recurring: $d",
                            body = "Due again — ${fmt(a)}",
                            atEpochMillis = epochMillisAt(today().plusDays(30), 9, 0),
                        )
                    }
                    desc = ""; amount = ""; recurring = false
                }
            }) { Text("Add") }
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            CATEGORIES.take(3).forEach { c -> FilterChip(selected = category == c, onClick = { category = c }, label = { Text(c) }) }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            CATEGORIES.drop(3).forEach { c -> FilterChip(selected = category == c, onClick = { category = c }, label = { Text(c) }) }
            FilterChip(selected = recurring, onClick = { recurring = !recurring }, label = { Text("↻ Recurring") })
        }

        if (Native.supportsCamera) {
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedButton(
                    onClick = { if (!AiClient.hasKey()) scanErr = "Add an AI key in Settings to scan." else { scanErr = null; showSource = true } },
                    enabled = !scanning,
                ) {
                    if (scanning) {
                        CircularProgressIndicator(Modifier.height(16.dp).width(16.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(8.dp))
                        Text("Reading…")
                    } else {
                        Text("📷 Scan receipt")
                    }
                }
                scanErr?.let {
                    Spacer(Modifier.width(10.dp))
                    Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
                }
            }
        }

        if (showSource) {
            AlertDialog(
                onDismissRequest = { showSource = false },
                title = { Text("Scan a receipt") },
                text = { Text("Take a new photo of the receipt, or choose one from your library.") },
                confirmButton = { TextButton(onClick = { showSource = false; Native.takePhoto { onReceipt(it) } }) { Text("Take a photo") } },
                dismissButton = { TextButton(onClick = { showSource = false; Native.capturePhoto { onReceipt(it) } }) { Text("Choose from library") } },
            )
        }

        if (byCategory.size > 1) {
            Spacer(Modifier.height(14.dp))
            Text("BY CATEGORY", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(6.dp))
            byCategory.forEach { (cat, total) ->
                Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                    Text(cat, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                    Text(fmt(total), style = MaterialTheme.typography.bodyMedium, color = if (total < 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
                }
            }
        }
        Spacer(Modifier.height(14.dp))

        previewBlob?.let { blobId ->
            val full = remember(blobId) { loadBlobImage(blobId) }
            AlertDialog(
                onDismissRequest = { previewBlob = null },
                confirmButton = { TextButton(onClick = { previewBlob = null }) { Text("Close") } },
                title = { Text("Receipt") },
                text = {
                    if (full != null) {
                        Image(
                            bitmap = full,
                            contentDescription = "Receipt",
                            modifier = Modifier.fillMaxWidth().heightIn(max = 420.dp).clip(RoundedCornerShape(8.dp)),
                            contentScale = ContentScale.Fit,
                        )
                    } else {
                        Text("The receipt image is no longer available.")
                    }
                },
            )
        }

        LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            items(entries, key = { it.id }) { e ->
                Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (e.photoBlob.isNotBlank()) {
                        val thumb = remember(e.photoBlob) { loadBlobImage(e.photoBlob) }
                        if (thumb != null) {
                            Image(
                                bitmap = thumb,
                                contentDescription = "Receipt",
                                modifier = Modifier.size(40.dp).clip(RoundedCornerShape(6.dp)).clickable { previewBlob = e.photoBlob },
                                contentScale = ContentScale.Crop,
                            )
                            Spacer(Modifier.width(10.dp))
                        } else {
                            Text("🧾", modifier = Modifier.padding(end = 10.dp))
                        }
                    }
                    Column(Modifier.weight(1f)) {
                        Text(e.desc, style = MaterialTheme.typography.bodyLarge)
                        Text(
                            listOfNotNull(e.category, if (e.recurring) "↻" else null, e.date.ifBlank { null }?.let { usDate(it) }).joinToString(" · "),
                            style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Text(fmt(e.amount), style = MaterialTheme.typography.bodyLarge, color = if (e.amount < 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
                    TextButton(onClick = {
                        if (e.recurring && Native.supportsNotifications) Native.cancelReminder((e.desc + "recur").hashCode())
                        if (e.photoBlob.isNotBlank()) deleteBlob(e.photoBlob)
                        persist(entries.filterNot { it.id == e.id })
                    }) { Text("✕") }
                }
            }
        }
    }
}

// Bills — recurring or one-time obligations with a due date, cadence, optional
// autopay, and a reminder N days ahead. "Paid" logs a payment, advances the next
// due date by the cadence, and reschedules the reminder.
@Composable
private fun BillsTab(data: FinanceData, onChange: (FinanceData) -> Unit) {
    val bills = data.bills
    fun persist(next: List<Bill>) { onChange(data.copy(bills = next)) }
    fun patchBill(id: Long, f: (Bill) -> Bill) = persist(bills.map { if (it.id == id) f(it) else it })
    var expandedBill by remember { mutableStateOf<Long?>(null) }
    var payDate by remember { mutableStateOf(today().toString()) }
    var payAmount by remember { mutableStateOf("") }
    var nextId by remember { mutableStateOf((bills.maxOfOrNull { it.id } ?: 0L) + 1) }
    var name by remember { mutableStateOf("") }
    var amount by remember { mutableStateOf("") }
    var dueDate by remember { mutableStateOf(today().toString()) }
    var cadence by remember { mutableStateOf("monthly") }
    var autopay by remember { mutableStateOf(false) }
    var remindDays by remember { mutableStateOf(3) }
    var contact by remember { mutableStateOf("") }

    val sorted = bills.sortedBy { parseDateOrNull(it.dueDate) ?: today().plusDays(3650) }
    val monthlyTotal = bills.filter { it.cadence != "one-time" }.sumOf { monthlyEquiv(it.amount, it.cadence) }

    Column(Modifier.fillMaxSize().padding(20.dp)) {
        Text("Bills — ${fmt(monthlyTotal)}/mo recurring", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(12.dp))

        OutlinedTextField(name, { name = it }, modifier = Modifier.fillMaxWidth(), singleLine = true, placeholder = { Text("Bill name") })
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            amount, { amount = it }, modifier = Modifier.fillMaxWidth(), singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), placeholder = { Text("Amount") },
        )
        Spacer(Modifier.height(8.dp))
        DateField(dueDate) { v -> dueDate = v }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            CADENCES.forEach { c -> FilterChip(selected = cadence == c, onClick = { cadence = c }, label = { Text(c) }) }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            FilterChip(selected = autopay, onClick = { autopay = !autopay }, label = { Text("Autopay") })
            Text("Remind", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            REMIND_OPTIONS.forEach { r ->
                FilterChip(selected = remindDays == r, onClick = { remindDays = r }, label = { Text(if (r == 0) "day of" else "${r}d") })
            }
        }
        Spacer(Modifier.height(8.dp))
        com.alekpeed.lifeos.people.ContactField(contact, placeholder = "Linked contact (optional)") { contact = it }
        Spacer(Modifier.height(8.dp))
        Button(onClick = {
            val n = name.trim().replace("\n", " ")
            val a = amount.trim().toDoubleOrNull()
            if (n.isNotEmpty() && a != null) {
                val bill = Bill(nextId, n, a, dueDate.trim(), cadence, autopay, remindDays, contact = contact.trim())
                nextId += 1
                persist(listOf(bill) + bills)
                scheduleBill(bill)
                name = ""; amount = ""; dueDate = today().toString(); cadence = "monthly"; autopay = false; remindDays = 3; contact = ""
            }
        }, modifier = Modifier.fillMaxWidth()) { Text("Add bill") }
        Spacer(Modifier.height(14.dp))

        if (bills.isEmpty()) {
            Text("No bills yet. Add one above.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            return
        }
        LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            items(sorted, key = { it.id }) { b ->
                val expanded = expandedBill == b.id
                Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(
                            Modifier.weight(1f).clickable { expandedBill = if (expanded) null else b.id },
                        ) {
                            Text(b.name, style = MaterialTheme.typography.bodyLarge)
                            val due = parseDateOrNull(b.dueDate)
                            val meta = buildList {
                                add(b.cadence)
                                if (due != null) add("due ${relativeLabel(due)}") else if (b.dueDate.isNotBlank()) add("due ${usDate(b.dueDate)}")
                                if (b.autopay) add("autopay")
                                if (b.contact.isNotBlank()) add("👤 ${b.contact}")
                                if (b.attachments.isNotEmpty()) add("📎 ${b.attachments.size}")
                                if (b.paymentHistory.isNotEmpty()) {
                                    val last = b.paymentHistory.maxByOrNull { it.date }?.date
                                    add("paid ${b.paymentHistory.size}×" + if (last != null) ", last ${usDate(last)}" else "")
                                }
                            }.joinToString(" · ")
                            Text(meta, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Text(fmt(b.amount), style = MaterialTheme.typography.bodyLarge)
                        Spacer(Modifier.width(4.dp))
                        TextButton(onClick = {
                            val paid = b.copy(
                                paymentHistory = b.paymentHistory + Payment(today().toString(), b.amount),
                                dueDate = advanceDue(b.dueDate, b.cadence),
                            )
                            persist(bills.map { if (it.id == b.id) paid else it })
                            scheduleBill(paid)
                        }) { Text("Paid") }
                        TextButton(onClick = {
                            if (Native.supportsNotifications) Native.cancelReminder(billReminderId(b.name))
                            persist(bills.filterNot { it.id == b.id })
                        }) { Text("✕") }
                    }
                    if (expanded) {
                        Column(
                            Modifier.fillMaxWidth().padding(top = 6.dp, bottom = 4.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            com.alekpeed.lifeos.attach.AttachmentsSection(
                                b.attachments,
                                { list -> patchBill(b.id) { it.copy(attachments = list) } },
                                label = "Bill PDFs & statements",
                            )
                            Text("Payment history", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            if (b.paymentHistory.isEmpty()) {
                                Text("No payments logged yet.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            b.paymentHistory.sortedByDescending { it.date }.forEach { p ->
                                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                    Text("${usDate(p.date)} · ${fmt(p.amount)}", style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                                    TextButton(onClick = {
                                        patchBill(b.id) { it.copy(paymentHistory = it.paymentHistory.filterNot { q -> q.date == p.date && q.amount == p.amount }) }
                                    }) { Text("×") }
                                }
                            }
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Box(Modifier.weight(1f)) { DateField(payDate) { v -> payDate = v } }
                                OutlinedTextField(
                                    payAmount, { payAmount = it }, modifier = Modifier.weight(1f), singleLine = true,
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), placeholder = { Text("Amount") },
                                )
                                TextButton(onClick = {
                                    val a = payAmount.trim().toDoubleOrNull() ?: b.amount
                                    patchBill(b.id) { it.copy(paymentHistory = it.paymentHistory + Payment(payDate.trim().ifBlank { today().toString() }, a)) }
                                    payAmount = ""
                                }) { Text("Log") }
                            }
                        }
                    }
                }
            }
        }
    }
}

// Subscriptions — weekly/monthly/yearly recurring costs, each normalized to a
// combined monthly spend. Cancelled subs stay on the list (struck through in the
// meta line) but drop out of the monthly total and the Yearly Spend annualization.
@Composable
private fun SubscriptionsTab(data: FinanceData, onChange: (FinanceData) -> Unit) {
    val subs = data.subscriptions
    fun persist(next: List<Subscription>) { onChange(data.copy(subscriptions = next)) }
    fun patchSub(id: Long, f: (Subscription) -> Subscription) = persist(subs.map { if (it.id == id) f(it) else it })
    var expandedSub by remember { mutableStateOf<Long?>(null) }
    var nextId by remember { mutableStateOf((subs.maxOfOrNull { it.id } ?: 0L) + 1) }
    var name by remember { mutableStateOf("") }
    var amount by remember { mutableStateOf("") }
    var cycle by remember { mutableStateOf("monthly") }

    val active = subs.filter { it.active }
    val monthlyTotal = active.sumOf { monthlyEquiv(it.amount, it.cycle) }
    val yearlyTotal = active.sumOf { yearlyEquiv(it.amount, it.cycle) }

    Column(Modifier.fillMaxSize().padding(20.dp)) {
        Text("${fmt(monthlyTotal)}/mo", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
        Text("${fmt(yearlyTotal)}/yr · ${active.size} active", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(12.dp))

        OutlinedTextField(name, { name = it }, modifier = Modifier.fillMaxWidth(), singleLine = true, placeholder = { Text("Subscription name") })
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                amount, { amount = it }, modifier = Modifier.weight(1f), singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), placeholder = { Text("Amount") },
            )
            Spacer(Modifier.width(10.dp))
            Button(onClick = {
                val n = name.trim().replace("\n", " ")
                val a = amount.trim().toDoubleOrNull()
                if (n.isNotEmpty() && a != null) {
                    persist(listOf(Subscription(nextId, n, a, cycle, true)) + subs)
                    nextId += 1
                    name = ""; amount = ""; cycle = "monthly"
                }
            }) { Text("Add") }
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            CYCLES.forEach { c -> FilterChip(selected = cycle == c, onClick = { cycle = c }, label = { Text(c) }) }
        }
        Spacer(Modifier.height(14.dp))

        if (subs.isEmpty()) {
            Text("No subscriptions yet.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            return
        }
        LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            items(subs.sortedByDescending { monthlyEquiv(it.amount, it.cycle) }, key = { it.id }) { s ->
                val expanded = expandedSub == s.id
                Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f).clickable { expandedSub = if (expanded) null else s.id }) {
                            Text(s.name, style = MaterialTheme.typography.bodyLarge, color = if (s.active) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant)
                            val meta = buildList {
                                add(s.cycle)
                                add("${fmt(monthlyEquiv(s.amount, s.cycle))}/mo")
                                if (s.category.isNotBlank() && s.category != "Subscriptions") add(s.category)
                                parseDateOrNull(s.renewalDate)?.let { add("renews ${relativeLabel(it)}") }
                                if (!s.active) add("cancelled")
                            }.joinToString(" · ")
                            Text(meta, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Text(fmt(s.amount), style = MaterialTheme.typography.bodyLarge)
                        TextButton(onClick = {
                            persist(subs.map { if (it.id == s.id) it.copy(active = !it.active) else it })
                        }) { Text(if (s.active) "Cancel" else "Resume") }
                        TextButton(onClick = { persist(subs.filterNot { it.id == s.id }) }) { Text("✕") }
                    }
                    if (expanded) {
                        Column(Modifier.fillMaxWidth().padding(top = 6.dp, bottom = 4.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("Renews", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            DateField(s.renewalDate) { v -> patchSub(s.id) { it.copy(renewalDate = v) } }
                            OutlinedTextField(
                                s.category, { v -> patchSub(s.id) { it.copy(category = v.replace("\n", " ")) } },
                                modifier = Modifier.fillMaxWidth(), singleLine = true, label = { Text("Category") },
                            )
                            OutlinedTextField(
                                s.notes, { v -> patchSub(s.id) { it.copy(notes = v) } },
                                modifier = Modifier.fillMaxWidth(), placeholder = { Text("Notes") },
                            )
                        }
                    }
                }
            }
        }
    }
}

// Yearly Spend — a projected annual outlay by category: every bill payment logged
// this calendar year, plus each active subscription annualized (weekly ×52,
// monthly ×12, yearly ×1). Read-only; it reflects what the other two tabs hold.
@Composable
private fun YearlyTab(data: FinanceData) {
    val year = today().toString().take(4)
    val billsByCat = data.bills
        .flatMap { b -> b.paymentHistory.filter { it.date.startsWith(year) }.map { b.category to it.amount } }
        .groupBy({ it.first }, { it.second })
        .mapValues { it.value.sum() }
    val subsByCat = data.subscriptions.filter { it.active }
        .groupBy { it.category }
        .mapValues { e -> e.value.sumOf { yearlyEquiv(it.amount, it.cycle) } }

    val combined = (billsByCat.keys + subsByCat.keys).associateWith {
        (billsByCat[it] ?: 0.0) + (subsByCat[it] ?: 0.0)
    }.entries.sortedByDescending { it.value }
    val total = combined.sumOf { it.value }
    val billsPaid = billsByCat.values.sum()
    val subsAnnual = subsByCat.values.sum()

    Column(Modifier.fillMaxSize().padding(20.dp)) {
        Text("Yearly Spend — $year", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(6.dp))
        Text(fmt(total), style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.primary)
        Text(
            "${fmt(billsPaid)} bills paid · ${fmt(subsAnnual)} subscriptions annualized",
            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(16.dp))

        if (combined.isEmpty()) {
            Text("Nothing yet — log a bill payment or add an active subscription.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            return
        }
        Text("BY CATEGORY", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(8.dp))
        LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            items(combined) { (cat, amt) ->
                Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    Row(Modifier.fillMaxWidth()) {
                        Text(cat, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                        Text(fmt(amt), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
                    }
                    val frac = if (total > 0) (amt / total).toFloat().coerceIn(0f, 1f) else 0f
                    Spacer(Modifier.height(4.dp))
                    Row(Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)).background(MaterialTheme.colorScheme.surfaceVariant)) {
                        if (frac > 0f) Row(Modifier.fillMaxWidth(frac).height(6.dp).background(MaterialTheme.colorScheme.primary)) {}
                    }
                }
            }
        }
    }
}

// ---- CSV import ----------------------------------------------------------

private data class Txn(val date: String, val desc: String, val amount: Double)

// Quote-aware CSV tokenizer → rows of fields. Handles "" escapes and CRLF.
private fun parseCsvRows(text: String): List<List<String>> {
    val rows = ArrayList<List<String>>()
    var field = StringBuilder()
    var row = ArrayList<String>()
    var inQuotes = false
    var i = 0
    while (i < text.length) {
        val c = text[i]
        when {
            inQuotes -> when {
                c == '"' && i + 1 < text.length && text[i + 1] == '"' -> { field.append('"'); i++ }
                c == '"' -> inQuotes = false
                else -> field.append(c)
            }
            c == '"' -> inQuotes = true
            c == ',' -> { row.add(field.toString()); field = StringBuilder() }
            c == '\n' || c == '\r' -> {
                if (c == '\r' && i + 1 < text.length && text[i + 1] == '\n') i++
                row.add(field.toString()); field = StringBuilder()
                if (row.any { it.isNotBlank() }) rows.add(row)
                row = ArrayList()
            }
            else -> field.append(c)
        }
        i++
    }
    row.add(field.toString())
    if (row.any { it.isNotBlank() }) rows.add(row)
    return rows
}

// "$1,234.56" / "(45.00)" / "-45" → signed Double, or null if not a number.
private fun parseAmount(raw: String): Double? {
    var s = raw.trim()
    if (s.isEmpty()) return null
    var neg = false
    if (s.startsWith("(") && s.endsWith(")")) { neg = true; s = s.substring(1, s.length - 1) }
    s = s.replace("$", "").replace(",", "").replace("+", "").trim()
    if (s.startsWith("-")) { neg = true; s = s.substring(1) }
    val v = s.toDoubleOrNull() ?: return null
    return if (neg) -v else v
}

// Bank dates are US-ordered (MM/DD/YYYY); normalize to ISO, falling back to today.
private fun normalizeDate(raw: String): String {
    val s = raw.trim()
    Regex("""\d{4}-\d{2}-\d{2}""").find(s)?.let { return it.value }
    val m = Regex("""(\d{1,2})[/-](\d{1,2})[/-](\d{2,4})""").find(s) ?: return today().toString()
    val (a, b, c) = m.destructured
    var yr = c.toInt(); if (yr < 100) yr += 2000
    val mm = a.toInt(); val dd = b.toInt()
    if (mm !in 1..12 || dd !in 1..31) return today().toString()
    val iso = "${yr.toString().padStart(4, '0')}-${mm.toString().padStart(2, '0')}-${dd.toString().padStart(2, '0')}"
    return if (parseDateOrNull(iso) != null) iso else today().toString()
}

// Best-effort transaction extraction: use header names when present (amount, or
// debit/credit; date; description-like), else guess (first col = date, longest
// non-numeric = description, last numeric = amount).
private fun parseTransactions(text: String): List<Txn> {
    val rows = parseCsvRows(text)
    if (rows.isEmpty()) return emptyList()
    val header = rows[0].map { it.trim().lowercase() }
    fun findIdx(vararg keys: String) = header.indexOfFirst { h -> keys.any { h.contains(it) } }
    val dateIdx = findIdx("date", "posted")
    val descIdx = findIdx("description", "desc", "payee", "name", "memo", "details", "narrative")
    val amtIdx = findIdx("amount", "value")
    val debitIdx = findIdx("debit", "withdrawal")
    val creditIdx = findIdx("credit", "deposit")
    val hasHeader = dateIdx >= 0 || descIdx >= 0 || amtIdx >= 0 || debitIdx >= 0 || creditIdx >= 0
    val dataRows = if (hasHeader) rows.drop(1) else rows

    val out = ArrayList<Txn>()
    for (r in dataRows) {
        if (r.isEmpty()) continue
        val amount: Double? = when {
            amtIdx >= 0 -> parseAmount(r.getOrElse(amtIdx) { "" })
            debitIdx >= 0 || creditIdx >= 0 -> {
                val debit = if (debitIdx >= 0) parseAmount(r.getOrElse(debitIdx) { "" }) else null
                val credit = if (creditIdx >= 0) parseAmount(r.getOrElse(creditIdx) { "" }) else null
                when {
                    credit != null && credit != 0.0 -> abs(credit)
                    debit != null && debit != 0.0 -> -abs(debit)
                    else -> null
                }
            }
            else -> r.asReversed().firstNotNullOfOrNull { parseAmount(it) }
        }
        if (amount == null) continue
        val dateStr = normalizeDate(if (dateIdx >= 0) r.getOrElse(dateIdx) { "" } else r.getOrElse(0) { "" })
        val desc = (if (descIdx >= 0) r.getOrElse(descIdx) { "" }
        else r.filter { parseAmount(it) == null && !it.contains("/") }.maxByOrNull { it.length } ?: "Imported")
            .trim().replace("\n", " ").ifBlank { "Imported" }
        out.add(Txn(dateStr, desc, amount))
    }
    return out
}

// Find a bill this transaction likely pays: same amount (to the cent) and a name
// that overlaps the transaction description.
private fun matchBill(txn: Txn, bills: List<Bill>): Bill? {
    val amt = abs(txn.amount)
    val desc = txn.desc.lowercase()
    return bills.filter { it.amount > 0 && abs(it.amount - amt) <= 0.01 }
        .firstOrNull { b ->
            val name = b.name.lowercase().trim()
            name.isNotBlank() && (desc.contains(name) || name.split(" ").any { w -> w.length >= 4 && desc.contains(w) })
        }
}

// Import — pick a bank/card CSV, review the parsed transactions, then either add
// them to the ledger or (for rows that match a bill) mark that bill paid.
@Composable
private fun ImportTab(data: FinanceData, onChange: (FinanceData) -> Unit) {
    var txns by remember { mutableStateOf<List<Txn>?>(null) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    fun onFile(text: String?) {
        if (text == null) return
        busy = true
        scope.launch {
            val parsed = withContext(Dispatchers.Default) { parseTransactions(text) }
            busy = false
            if (parsed.isEmpty()) { error = "Couldn't find transactions in that file."; txns = null }
            else { error = null; txns = parsed }
        }
    }

    Column(Modifier.fillMaxSize().padding(20.dp)) {
        Text("Import transactions", style = MaterialTheme.typography.titleMedium)
        Text("Pick a bank or card CSV export. Rows go to the ledger (spending negative); rows that match a bill can be marked paid instead.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(12.dp))

        if (!Native.supportsFilePick) {
            Text("File import isn't available on this platform.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            return
        }

        Button(onClick = { error = null; Native.pickTextFile { onFile(it) } }, enabled = !busy) {
            if (busy) {
                CircularProgressIndicator(Modifier.height(16.dp).width(16.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(8.dp))
                Text("Reading…")
            } else {
                Text("Choose CSV file")
            }
        }
        error?.let {
            Spacer(Modifier.height(6.dp))
            Text(it, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.error)
        }

        txns?.let { list ->
            val matches = remember(list, data.bills) { list.map { matchBill(it, data.bills) } }
            var payIdx by remember(list, data.bills) { mutableStateOf(matches.indices.filter { matches[it] != null }.toSet()) }
            val payCount = payIdx.size
            val ledgerCount = list.size - payCount

            Spacer(Modifier.height(12.dp))
            Text("${list.size} transaction${if (list.size == 1) "" else "s"}" + if (matches.any { it != null }) " · ${matches.count { it != null }} match a bill" else "", style = MaterialTheme.typography.labelLarge)
            Spacer(Modifier.height(8.dp))
            LazyColumn(Modifier.fillMaxWidth().weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                itemsIndexed(list) { i, t ->
                    val b = matches[i]
                    Column(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(usDate(t.date).ifBlank { t.date }, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(84.dp))
                            Text(t.desc, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                            Text(fmt(t.amount), style = MaterialTheme.typography.bodyMedium, color = if (t.amount < 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
                            TextButton(onClick = { txns = list.filterIndexed { idx, _ -> idx != i } }) { Text("×") }
                        }
                        if (b != null) {
                            val paying = i in payIdx
                            FilterChip(
                                selected = paying,
                                onClick = { payIdx = if (paying) payIdx - i else payIdx + i },
                                label = { Text(if (paying) "✓ Pay bill: ${b.name}" else "Add to ledger instead") },
                            )
                        }
                    }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { txns = null }) { Text("Cancel") }
                Spacer(Modifier.weight(1f))
                Button(onClick = {
                    // Mark matched-and-selected rows' bills paid; add the rest to the ledger.
                    var bills = data.bills
                    payIdx.forEach { i ->
                        val b = matches.getOrNull(i) ?: return@forEach
                        bills = bills.map { bill ->
                            if (bill.id == b.id) {
                                val paid = bill.copy(
                                    paymentHistory = bill.paymentHistory + Payment(list[i].date, bill.amount),
                                    dueDate = advanceDue(bill.dueDate, bill.cadence),
                                )
                                scheduleBill(paid)
                                paid
                            } else bill
                        }
                    }
                    var id = (data.entries.maxOfOrNull { it.id } ?: 0L) + 1
                    val entries = list.filterIndexed { i, _ -> i !in payIdx }
                        .map { Entry(id++, it.desc, it.amount, "Imported", false, it.date) }
                    onChange(data.copy(entries = entries + data.entries, bills = bills))
                    txns = null
                }) { Text("Import ($ledgerCount ledger, $payCount paid)") }
            }
        }
    }
}

// Markets — a keyless CoinGecko crypto watchlist (price + 24h change) plus the Dow
// Jones close from Stooq. The watchlist is user-editable and persisted; ids are
// CoinGecko slugs (bitcoin, ethereum, solana), not ticker symbols. On-demand fetch
// with a manual refresh — no polling.
@Composable
private fun MarketsTab() {
    var watch by remember { mutableStateOf(MarketsClient.watchlist { Storage.read(it) }) }
    var prices by remember { mutableStateOf<List<CoinPrice>>(emptyList()) }
    var djia by remember { mutableStateOf<Double?>(null) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var newCoin by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()

    fun persistWatch() = MarketsClient.saveWatchlist(watch) { k, v -> Storage.write(k, v) }

    fun refresh() {
        loading = true; error = null
        scope.launch {
            MarketsClient.crypto(watch)
                .onSuccess { prices = it }
                .onFailure { error = it.message ?: "Couldn't load prices" }
            MarketsClient.djia().onSuccess { djia = it }
            loading = false
        }
    }

    LaunchedEffect(Unit) { refresh() }

    Column(Modifier.fillMaxSize().padding(20.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Markets", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            TextButton(onClick = { refresh() }, enabled = !loading) {
                if (loading) {
                    CircularProgressIndicator(Modifier.height(16.dp).width(16.dp), strokeWidth = 2.dp)
                } else {
                    Text("Refresh")
                }
            }
        }
        djia?.let {
            Text("Dow Jones  ${fmtIndex(it)}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        error?.let {
            Text(it, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.error)
        }
        Spacer(Modifier.height(12.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                newCoin, { newCoin = it }, modifier = Modifier.weight(1f), singleLine = true,
                placeholder = { Text("CoinGecko id (bitcoin, ethereum…)") },
            )
            Spacer(Modifier.width(10.dp))
            Button(onClick = {
                val id = newCoin.trim().lowercase()
                if (id.isNotEmpty() && id !in watch) {
                    watch = watch + id
                    persistWatch()
                    newCoin = ""
                    refresh()
                }
            }) { Text("Add") }
        }
        Text(
            "Coin ids are CoinGecko slugs (bitcoin, not BTC).",
            style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(12.dp))

        if (prices.isEmpty() && !loading) {
            Text(
                if (watch.isEmpty()) "No coins on your watchlist yet." else "No prices — tap Refresh, or check the coin ids.",
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            items(prices, key = { it.id }) { c ->
                Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(c.label, style = MaterialTheme.typography.bodyLarge)
                        Text(c.id, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text(fmtPrice(c.usd), style = MaterialTheme.typography.bodyLarge)
                        Text(
                            fmtPct(c.change24h),
                            style = MaterialTheme.typography.labelMedium,
                            color = if (c.change24h < 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                        )
                    }
                    TextButton(onClick = {
                        watch = watch - c.id
                        persistWatch()
                        prices = prices.filterNot { it.id == c.id }
                    }) { Text("✕") }
                }
            }
        }
    }
}

// Crypto prices span orders of magnitude, so show cents for ≥ $1 and up to six
// decimals for sub-dollar coins, all via integer math to avoid Double's
// scientific-notation toString.
private fun fmtPrice(v: Double): String {
    if (v >= 1.0) return fmt(v)
    val micros = (v * 1_000_000).toLong()
    val whole = micros / 1_000_000
    val frac = (micros % 1_000_000).toString().padStart(6, '0').trimEnd('0').ifEmpty { "0" }
    return "$$whole.$frac"
}

private fun fmtPct(v: Double): String {
    val sign = if (v >= 0) "+" else "-"
    val cents = (abs(v) * 100).toLong()
    return "$sign${cents / 100}.${(cents % 100).toString().padStart(2, '0')}%"
}

private fun fmtIndex(v: Double): String {
    val cents = (abs(v) * 100).toLong()
    return "${cents / 100}.${(cents % 100).toString().padStart(2, '0')}"
}

@Composable
private fun MonthStat(label: String, value: String, color: androidx.compose.ui.graphics.Color, modifier: Modifier) {
    Column(modifier) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.titleSmall, color = color)
    }
}
