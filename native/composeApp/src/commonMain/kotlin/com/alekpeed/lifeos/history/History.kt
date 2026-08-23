package com.alekpeed.lifeos.history

import com.alekpeed.lifeos.Storage
import com.alekpeed.lifeos.core.isBackupKey
import com.alekpeed.lifeos.data.recordLabel
import kotlinx.datetime.Clock
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

// R-02 + R-03, as one mechanism.
//
// The work order calls these "a delete flag plus a mutation log" and then says, in the
// same breath, that doing them separately "produces two overlapping mechanisms". A flag
// AND a log is exactly those two overlapping mechanisms, so this builds only the log and
// derives soft delete from it: a deleted record is one whose newest event is a DELETE,
// and restoring it is replaying that event backwards. Same promise — nothing is gone
// until you say so — without adding a `deleted` field to 38 data classes and a
// `.filter { !it.deleted }` to every list in the app, where one missed filter silently
// shows deleted rows forever.
//
// It works without any module knowing it exists. Storage is a flat key -> text store and
// every module keeps its records as a JSON blob under one key, so the log hooks the two
// places every change already funnels through (Storage.write and Storage.remove), diffs
// the blob about to be overwritten against the one replacing it, and records what
// actually moved. Nothing in a module changes.
//
// Events are minimal but *invertible*, which is the property Time Machine needs (§4:
// "replaying the log to that point"). CREATE keeps only the id, because undoing it means
// removing the record. UPDATE keeps the prior and new value of the changed fields only.
// DELETE keeps the whole record, because putting it back is the entire point.
//
// The log is local and per-device: the key is reserved (`__`), so SyncMeta skips it and
// it never enters the backup export. A shared cross-device log is event-sourced sync,
// which is a different and much larger build.

@Serializable
enum class Change { CREATE, UPDATE, DELETE }

@Serializable
data class Mutation(
    val seq: Long,
    val at: Long,
    // The Storage key the record lives under — "Tasks", "Travel".
    val key: String,
    // The array field inside that key's blob ("trips"), or "" when the blob is itself an
    // array. Needed to put a restored record back where it came from.
    val coll: String = "",
    // The record's own id, as text so Long ids and String ids read the same.
    val rec: String = "",
    val change: Change,
    // How the record read at the time. A snapshot rather than resolved live, because a
    // deleted record has nothing left to resolve against.
    val label: String = "",
    // UPDATE: the changed fields as they were. DELETE: the whole record. Values are raw
    // JSON text, so nested objects and lists survive the round trip.
    val before: Map<String, String> = emptyMap(),
    // UPDATE: the changed fields as they became. Empty for CREATE and DELETE.
    val after: Map<String, String> = emptyMap(),
    // Applied from another device rather than made here.
    val remote: Boolean = false,
    // A field was too large to keep in full. The event still reads correctly; it just
    // cannot be undone, because undoing it would write the truncation back.
    val truncated: Boolean = false,
) {
    val reversible: Boolean get() = !truncated
}

object History {
    private const val LOG_KEY = "__history"

    // A single field bigger than this is not worth carrying in a log that gets rewritten
    // on every save. Nothing normal comes close — the blob store (R-01) means photos and
    // PDFs are already just an id by the time they reach here.
    private const val MAX_FIELD = 4_000

    // Ceilings. Trash beats both: a delete inside the retention window is never dropped
    // to make room, because "it is still in the trash" is a promise and an edit from
    // three weeks ago is not.
    private const val MAX_EVENTS = 1_500
    private const val TRASH_DAYS = 30
    private const val TRASH_MS = TRASH_DAYS * 24L * 60L * 60L * 1_000L

    // Consecutive edits to one record inside this window collapse into a single event.
    // A text field saves on every keystroke, so without this a typed sentence is forty
    // rows in the activity list and forty entries against the cap.
    private const val COALESCE_MS = 120_000L

    // Deleting a whole key only happens when the other device deleted it. Logging the
    // records it held is the useful part; logging ten thousand of them is not.
    private const val MAX_DELETE_FANOUT = 300

    private val json = Json { ignoreUnknownKeys = true }

    private var loaded = false
    private val log = mutableListOf<Mutation>()
    private var nextSeq = 1L

    // Set while a sync is writing. Those writes are real changes to your data and belong
    // in the log; they just did not happen here. Sync runs on its own coroutine, so the
    // flag has to be visible to the thread doing the writing.
    @Volatile
    private var remote = false

    private fun now(): Long = Clock.System.now().toEpochMilliseconds()

    private fun reserved(key: String) = key.startsWith("__")

    private fun ensureLoaded() {
        if (loaded) return
        loaded = true
        val raw = Storage.read(LOG_KEY)
        if (!raw.isNullOrBlank()) {
            runCatching { log.addAll(json.decodeFromString<List<Mutation>>(raw)) }
        }
        nextSeq = (log.maxOfOrNull { it.seq } ?: 0L) + 1L
    }

    // Written straight through on every change rather than debounced. A debounce would
    // save a few milliseconds of serialisation per keystroke and cost the guarantee the
    // whole thing exists for — a log that is a second behind cannot recover the delete
    // that happened in that second. The cap keeps the write small, and the app already
    // rewrites the module blob itself on the same keystroke.
    private fun persist() {
        runCatching { Storage.write(LOG_KEY, json.encodeToString(log.toList())) }
    }

    // ---- capture ---------------------------------------------------------------------

    // Sync applies remote records through here, so what comes out of it is marked.
    fun <T> asRemote(block: () -> T): T {
        remote = true
        return try { block() } finally { remote = false }
    }

    // Whether a write to this key is worth reading the old value for. Storage asks
    // before it re-reads the file, because that pre-read costs a whole file and most
    // keys — settings, secrets, the map tile cache — have nothing to log.
    fun tracks(key: String): Boolean = !reserved(key) && isBackupKey(key)

    fun onWrite(key: String, before: String?, after: String) {
        if (!tracks(key)) return
        // A key arriving for the first time from another device is that device's whole
        // history landing at once, not a thousand things you just did.
        if (remote && before.isNullOrBlank()) return
        capture(key, before, after)
    }

    fun onRemove(key: String, before: String?) {
        if (!tracks(key)) return
        if (before.isNullOrBlank()) return
        capture(key, before, null)
    }

    private fun capture(key: String, beforeRaw: String?, afterRaw: String?) {
        val old = parseRecords(beforeRaw)
        val new = parseRecords(afterRaw)
        if (old.isEmpty() && new.isEmpty()) return

        ensureLoaded()
        val at = now()
        var added = 0
        var deletes = 0

        for (coll in (old.keys + new.keys)) {
            val oldRecs = old[coll].orEmpty()
            val newRecs = new[coll].orEmpty()

            for ((id, rec) in newRecs) {
                val prior = oldRecs[id]
                if (prior == null) {
                    append(event(at, key, coll, id, Change.CREATE, rec, emptyMap(), emptyMap()))
                    added++
                } else if (prior.toString() != rec.toString()) {
                    val (b, a) = fieldDiff(prior, rec)
                    if (b.isEmpty() && a.isEmpty()) continue
                    val m = event(at, key, coll, id, Change.UPDATE, rec, b, a)
                    if (!coalesce(m)) append(m)
                    added++
                }
            }

            for ((id, rec) in oldRecs) {
                if (newRecs.containsKey(id)) continue
                if (deletes >= MAX_DELETE_FANOUT) break
                append(event(at, key, coll, id, Change.DELETE, rec, wholeRecord(rec), emptyMap()))
                deletes++
                added++
            }
        }

        if (added == 0) return
        prune()
        persist()
    }

    private fun event(
        at: Long,
        key: String,
        coll: String,
        rec: String,
        change: Change,
        record: JsonObject,
        before: Map<String, String>,
        after: Map<String, String>,
    ): Mutation {
        val cut = before.values.any { it.length > MAX_FIELD } ||
            after.values.any { it.length > MAX_FIELD }
        return Mutation(
            seq = nextSeq++,
            at = at,
            key = key,
            coll = coll,
            rec = rec,
            change = change,
            label = recordLabel(record)?.take(120).orEmpty(),
            before = before.mapValues { it.value.take(MAX_FIELD) },
            after = after.mapValues { it.value.take(MAX_FIELD) },
            remote = remote,
            truncated = cut,
        )
    }

    private fun append(m: Mutation) {
        log.add(m)
    }

    // How far back to look for an event to fold into. Only the tail would be enough for
    // one record typed into on its own, but a save that touches two records interleaves
    // them and every keystroke would then start a new entry for both.
    private const val COALESCE_LOOKBACK = 12

    // Fold an edit into a recent one on the same record, keeping the ORIGINAL before
    // values — otherwise undo would only ever step back one keystroke.
    private fun coalesce(m: Mutation): Boolean {
        var at = -1
        var seen = 0
        for (i in log.indices.reversed()) {
            if (seen++ >= COALESCE_LOOKBACK) break
            val c = log[i]
            if (m.at - c.at > COALESCE_MS) break
            if (c.change != Change.UPDATE) continue
            if (c.key != m.key || c.coll != m.coll || c.rec != m.rec) continue
            if (c.remote != m.remote) continue
            at = i
            break
        }
        if (at < 0) return false
        val last = log[at]

        val before = m.before.toMutableMap()
        // Fields the earlier event already saw keep their older "before".
        before.putAll(last.before)
        val after = last.after.toMutableMap()
        after.putAll(m.after)

        // A field edited back to what it was is no longer a change.
        val moved = (before.keys + after.keys).filter { before[it] != after[it] }
        if (moved.isEmpty()) {
            log.removeAt(at)
            return true
        }
        log[at] = last.copy(
            at = m.at,
            label = m.label,
            before = before.filterKeys { it in moved },
            after = after.filterKeys { it in moved },
            truncated = last.truncated || m.truncated,
        )
        return true
    }

    private fun prune() {
        if (log.size <= MAX_EVENTS) return
        val cutoff = now() - TRASH_MS
        val kept = log.count { it.change == Change.DELETE && it.at >= cutoff }
        var toDrop = log.size - maxOf(MAX_EVENTS, kept)
        if (toDrop <= 0) return
        val iter = log.iterator()
        while (iter.hasNext() && toDrop > 0) {
            val m = iter.next()
            if (m.change == Change.DELETE && m.at >= cutoff) continue
            iter.remove()
            toDrop--
        }
    }

    // ---- reading ----------------------------------------------------------------------

    fun all(): List<Mutation> {
        ensureLoaded()
        return log.toList()
    }

    fun recent(limit: Int = 200, key: String? = null): List<Mutation> {
        ensureLoaded()
        return log.asReversed().asSequence()
            .filter { key == null || it.key == key }
            .take(limit)
            .toList()
    }

    // Everything that ever happened to one record, newest first. What a module screen
    // shows when you ask a row where it came from.
    fun historyOf(key: String, rec: String): List<Mutation> {
        ensureLoaded()
        return log.filter { it.key == key && it.rec == rec }.asReversed()
    }

    // The keys that have any history at all, so the activity filter offers real choices.
    fun keysTouched(): List<String> {
        ensureLoaded()
        return log.map { it.key }.distinct().sorted()
    }

    // Deleted records still recoverable: the delete is the record's newest event — it was
    // not put back afterwards — and it is inside the retention window.
    fun trash(): List<Mutation> {
        ensureLoaded()
        val newest = LinkedHashMap<String, Mutation>()
        for (m in log) newest[m.key + "|" + m.coll + "|" + m.rec] = m
        val cutoff = now() - TRASH_MS
        return newest.values
            .filter { it.change == Change.DELETE && it.at >= cutoff }
            .sortedByDescending { it.at }
    }

    fun trashCount(): Int = trash().size

    // Purge one deleted record for good, or all of them.
    fun purge(m: Mutation) {
        ensureLoaded()
        if (log.removeAll { it.seq == m.seq }) persist()
    }

    fun emptyTrash() {
        ensureLoaded()
        val gone = trash().map { it.seq }.toSet()
        if (gone.isEmpty()) return
        log.removeAll { it.seq in gone }
        persist()
    }

    fun forget() {
        ensureLoaded()
        log.clear()
        persist()
    }

    val retentionDays: Int get() = TRASH_DAYS

    // ---- writing back -------------------------------------------------------------------

    // Undo one event: put back what it changed. False when it cannot be done honestly —
    // a truncated event, or an update whose record is no longer there to update.
    fun undo(m: Mutation): Boolean {
        if (!m.reversible) return false
        ensureLoaded()
        return when (m.change) {
            Change.CREATE -> editBlob(m.key, m.coll) { it.remove(m.coll, m.rec) }
            Change.DELETE -> editBlob(m.key, m.coll) { it.put(m.coll, m.rec, rebuild(m.before)) }
            Change.UPDATE -> editBlob(m.key, m.coll) { blob ->
                val cur = blob.get(m.coll, m.rec) ?: return@editBlob false
                blob.put(m.coll, m.rec, revert(cur, m.before))
            }
        }
    }

    fun restore(m: Mutation): Boolean = m.change == Change.DELETE && undo(m)

    // The blob as it stood at a moment in time: current state, with every event since
    // then reversed. This is what a rebuilt Time Machine reads — not "how many records
    // existed" but what they said.
    fun blobAt(key: String, at: Long): JsonElement? {
        ensureLoaded()
        val current = Storage.read(key) ?: return null
        val root = runCatching { json.parseToJsonElement(current) }.getOrNull() ?: return null
        val blob = Blob.of(root) ?: return null
        for (m in log.asReversed()) {
            if (m.key != key || m.at <= at) continue
            when (m.change) {
                Change.CREATE -> blob.remove(m.coll, m.rec)
                Change.DELETE -> blob.put(m.coll, m.rec, rebuild(m.before))
                Change.UPDATE -> blob.get(m.coll, m.rec)?.let { cur ->
                    blob.put(m.coll, m.rec, revert(cur, m.before))
                }
            }
        }
        return blob.rebuild()
    }

    // One record as it stood at a moment, or null if it did not exist then. The
    // per-record half of blobAt, for a screen asking about one row rather than a module.
    fun recordAt(key: String, coll: String, rec: String, at: Long): JsonObject? {
        val root = blobAt(key, at) ?: return null
        return Blob.of(root)?.get(coll, rec)
    }

    // Put a record back to how it read on a day: write the replayed version over the
    // current one, or remove it if it did not exist then. Returns false when there is
    // nothing to do — the record is already in that state, or never existed and still
    // doesn't.
    //
    // This is an ordinary write, so it goes through the log like any edit. Undoing a
    // restore is therefore just another undo, and the trip back is recorded rather than
    // silently rewriting the past.
    fun restoreTo(key: String, coll: String, rec: String, at: Long): Boolean {
        ensureLoaded()
        val then = recordAt(key, coll, rec, at)
        val nowRaw = Storage.read(key)
        val current = if (nowRaw.isNullOrBlank()) null
        else runCatching { json.parseToJsonElement(nowRaw) }.getOrNull()?.let { Blob.of(it)?.get(coll, rec) }

        if (then == null && current == null) return false
        if (then != null && current != null && then.toString() == current.toString()) return false

        return editBlob(key, coll) { blob ->
            if (then == null) blob.remove(coll, rec) else blob.put(coll, rec, then)
        }
    }

    // The moment of the log's first event — the point before which replay is guessing
    // rather than reading. Null when nothing has been recorded yet.
    fun earliestEventAt(): Long? {
        ensureLoaded()
        return log.minOfOrNull { it.at }
    }

    fun size(): Int {
        ensureLoaded()
        return log.size
    }

    // Drop the in-memory copy and read the log back off disk. Internal because nothing in
    // the app needs it — the log is local and this object is the only writer — but a test
    // that edits the stored log directly does.
    internal fun reload() {
        log.clear()
        loaded = false
        ensureLoaded()
    }

    private fun editBlob(key: String, coll: String, block: (Blob) -> Boolean): Boolean {
        val raw = Storage.read(key)
        val root = if (raw.isNullOrBlank()) null
        else runCatching { json.parseToJsonElement(raw) }.getOrNull()
        // With the key gone entirely, the event's own collection name says which shape to
        // rebuild: "" only ever comes from a blob that was a bare array.
        val blob = root?.let { Blob.of(it) } ?: Blob.empty(rootIsArray = coll.isEmpty())
        if (!block(blob)) return false
        Storage.write(key, blob.rebuild().toString())
        return true
    }

    private fun revert(current: JsonObject, before: Map<String, String>): JsonObject {
        val fields = LinkedHashMap<String, JsonElement>(current)
        before.forEach { (f, v) ->
            // "null" means the field was absent, or present and null — the two decode
            // to the same thing, so dropping it is right either way.
            if (v == "null") fields.remove(f) else fields[f] = parseValue(v)
        }
        return JsonObject(fields)
    }

    // ---- json plumbing ---------------------------------------------------------------------

    // A module's blob is either an array of records or an object with array fields, and a
    // record is an object with an `id`. That covers every JSON-stored module; anything
    // else — a settings string, an array of bare strings — has no records to track and is
    // skipped rather than guessed at.
    internal class Blob(
        private val rootIsArray: Boolean,
        private val scalars: MutableMap<String, JsonElement>,
        private val arrays: MutableMap<String, MutableList<JsonElement>>,
    ) {
        fun names(): Set<String> = arrays.keys.toSet()

        fun records(coll: String): Map<String, JsonObject> {
            val out = LinkedHashMap<String, JsonObject>()
            arrays[coll]?.forEach { e ->
                val o = e as? JsonObject ?: return@forEach
                val id = idOf(o) ?: return@forEach
                out[id] = o
            }
            return out
        }

        fun get(coll: String, rec: String): JsonObject? = records(coll)[rec]

        fun put(coll: String, rec: String, value: JsonObject): Boolean {
            val list = arrays.getOrPut(coll) { mutableListOf() }
            val at = list.indexOfFirst { (it as? JsonObject)?.let { o -> idOf(o) } == rec }
            if (at >= 0) list[at] = value else list.add(value)
            return true
        }

        fun remove(coll: String, rec: String): Boolean {
            val list = arrays[coll] ?: return false
            val at = list.indexOfFirst { (it as? JsonObject)?.let { o -> idOf(o) } == rec }
            if (at < 0) return false
            list.removeAt(at)
            return true
        }

        fun rebuild(): JsonElement =
            if (rootIsArray) {
                JsonArray(arrays[""].orEmpty().toList())
            } else {
                JsonObject(
                    LinkedHashMap<String, JsonElement>().apply {
                        putAll(scalars)
                        arrays.forEach { (k, v) -> put(k, JsonArray(v.toList())) }
                    },
                )
            }

        private fun idOf(o: JsonObject): String? =
            (o["id"] as? JsonPrimitive)?.content?.takeIf { it.isNotBlank() && it != "null" }

        companion object {
            fun of(root: JsonElement): Blob? = when (root) {
                is JsonArray -> Blob(true, LinkedHashMap(), linkedMapOf("" to root.toMutableList()))
                is JsonObject -> {
                    val scalars = LinkedHashMap<String, JsonElement>()
                    val arrays = LinkedHashMap<String, MutableList<JsonElement>>()
                    root.forEach { (k, v) ->
                        if (v is JsonArray) arrays[k] = v.toMutableList() else scalars[k] = v
                    }
                    Blob(false, scalars, arrays)
                }
                else -> null
            }

            fun empty(rootIsArray: Boolean) =
                Blob(rootIsArray, LinkedHashMap(), LinkedHashMap())
        }
    }

    private fun parseRecords(raw: String?): Map<String, Map<String, JsonObject>> {
        if (raw.isNullOrBlank()) return emptyMap()
        val t = raw.trimStart()
        if (!t.startsWith("{") && !t.startsWith("[")) return emptyMap()
        val root = runCatching { json.parseToJsonElement(raw) }.getOrNull() ?: return emptyMap()
        val blob = Blob.of(root) ?: return emptyMap()
        val out = LinkedHashMap<String, Map<String, JsonObject>>()
        for (name in blob.names()) {
            val recs = blob.records(name)
            if (recs.isNotEmpty()) out[name] = recs
        }
        return out
    }

    private fun fieldDiff(
        old: JsonObject,
        new: JsonObject,
    ): Pair<Map<String, String>, Map<String, String>> {
        val before = LinkedHashMap<String, String>()
        val after = LinkedHashMap<String, String>()
        for (f in (old.keys + new.keys)) {
            if (f == "id") continue
            val o = old[f]?.toString() ?: "null"
            val n = new[f]?.toString() ?: "null"
            if (o == n) continue
            before[f] = o
            after[f] = n
        }
        return before to after
    }

    private fun wholeRecord(o: JsonObject): Map<String, String> =
        o.entries.associate { (k, v) -> k to v.toString() }

    private fun rebuild(fields: Map<String, String>): JsonObject =
        JsonObject(fields.mapValues { parseValue(it.value) })

    private fun parseValue(raw: String): JsonElement =
        runCatching { json.parseToJsonElement(raw) }.getOrElse { JsonPrimitive(raw) }
}
