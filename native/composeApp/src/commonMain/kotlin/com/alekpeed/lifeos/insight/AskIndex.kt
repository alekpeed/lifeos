package com.alekpeed.lifeos.insight

import com.alekpeed.lifeos.Storage
import com.alekpeed.lifeos.ai.OpenAiClient
import com.alekpeed.lifeos.data.allRecords
import com.alekpeed.lifeos.platform.readTextBlob
import com.alekpeed.lifeos.platform.saveTextBlob
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.math.sqrt

// Ask's semantic memory. Every record across the app is embedded once (OpenAI
// text-embedding-3-small) into a device-local index; a query is embedded and
// ranked by cosine similarity, so "money I owe" finds a bill even without the
// word. The index lives in a text blob (never synced/backed-up, like images);
// meta (a corpus hash so we know when it's stale) lives under one Storage key.
object AskIndex {
    private val json = Json { ignoreUnknownKeys = true }
    private const val META = "AskIndexMeta" // "hash|blobId|count"
    const val MAX_RECORDS = 500             // bounds embedding cost/time

    @Serializable
    private data class Entry(val s: String, val m: String, val t: String, val v: List<Float>)

    @Serializable
    private data class Index(val hash: Int, val entries: List<Entry>)

    data class Ranked(val source: String, val text: String, val moduleId: String, val score: Float)

    private var cache: Index? = null

    // Semantic search needs OpenAI embeddings (the baked-key provider).
    fun available(): Boolean = OpenAiClient.key().isNotEmpty()

    private fun meta(): Triple<Int, String, Int>? {
        val p = Storage.read(META)?.split("|") ?: return null
        if (p.size < 3) return null
        return Triple(p[0].toIntOrNull() ?: return null, p[1], p[2].toIntOrNull() ?: 0)
    }

    fun indexedCount(): Int = meta()?.third ?: 0

    fun corpusHash(): Int = allRecords().joinToString("\n") { "${it.source}\t${it.text}" }.hashCode()

    // True when there's no index, or your data has changed since it was built.
    fun isStale(): Boolean = meta()?.first != corpusHash()

    private fun load(): Index? {
        cache?.let { return it }
        val m = meta() ?: return null
        val raw = readTextBlob(m.second) ?: return null
        return runCatching { json.decodeFromString<Index>(raw) }.getOrNull()?.also { cache = it }
    }

    // Build / rebuild. onProgress(done, total). Returns entry count, or -1 if the
    // embedding call failed (e.g. offline / bad key).
    suspend fun build(onProgress: (Int, Int) -> Unit): Int {
        val records = allRecords().take(MAX_RECORDS)
        if (records.isEmpty()) return 0
        val entries = ArrayList<Entry>(records.size)
        val batch = 96
        var i = 0
        while (i < records.size) {
            val slice = records.subList(i, minOf(i + batch, records.size))
            val vecs = OpenAiClient.embed(slice.map { "${it.source}: ${it.text}" }) ?: return -1
            slice.forEachIndexed { j, r ->
                entries.add(Entry(r.source, r.moduleId, r.text, vecs.getOrNull(j)?.toList() ?: emptyList()))
            }
            i += batch
            onProgress(minOf(i, records.size), records.size)
        }
        val idx = Index(corpusHash(), entries)
        val blobId = saveTextBlob(json.encodeToString(idx)) ?: return -1
        Storage.write(META, "${idx.hash}|$blobId|${entries.size}")
        cache = idx
        return entries.size
    }

    // Rank the index against a query. Returns null if there's no index or the
    // query couldn't be embedded.
    suspend fun search(query: String, top: Int = 12): List<Ranked>? {
        val idx = load() ?: return null
        val q = OpenAiClient.embed(listOf(query))?.firstOrNull() ?: return null
        val qn = normalize(q)
        return idx.entries.asSequence()
            .filter { it.v.isNotEmpty() }
            .map { Ranked(it.s, it.t, it.m, dot(qn, it.v)) }
            .sortedByDescending { it.score }
            .take(top)
            .toList()
    }

    private fun normalize(v: FloatArray): FloatArray {
        var n = 0f
        for (x in v) n += x * x
        n = sqrt(n)
        return if (n == 0f) v else FloatArray(v.size) { v[it] / n }
    }

    private fun dot(a: FloatArray, b: List<Float>): Float {
        var s = 0f
        val n = minOf(a.size, b.size)
        for (i in 0 until n) s += a[i] * b[i]
        return s
    }
}
