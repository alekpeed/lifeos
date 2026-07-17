package com.alekpeed.lifeos.knowledge

import com.alekpeed.lifeos.Storage
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

// Knowledge Graph — link anything to anything and walk the connections. Ported
// from the web view's interaction grammar (one record in focus, its connections
// as spokes, tap one to refocus and keep walking), reusing Search as the picker
// for "what's linkable." The web renders the neighborhood as a radial SVG; native
// shows it as a focus + connections list (this app doesn't hand-build graphics).
// Edges are keyed on a record's (source, label); AI edge-suggestions defer to the
// AI layer. Persists edges as one JSON blob under "Knowledge Graph".

@Serializable
data class Edge(val aSource: String, val aLabel: String, val bSource: String, val bLabel: String) {
    fun touches(source: String, label: String) =
        (aSource == source && aLabel == label) || (bSource == source && bLabel == label)

    // The other endpoint from the given node, as (source, label).
    fun other(source: String, label: String): Pair<String, String> =
        if (aSource == source && aLabel == label) bSource to bLabel else aSource to aLabel
}

@Serializable
data class KGraphData(val edges: List<Edge> = emptyList())

private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

fun loadKGraph(): KGraphData {
    val raw = Storage.read("Knowledge Graph")
    if (raw.isNullOrBlank() || !raw.trimStart().startsWith("{")) return KGraphData()
    return runCatching { json.decodeFromString<KGraphData>(raw) }.getOrElse { KGraphData() }
}

fun saveKGraph(data: KGraphData) {
    Storage.write("Knowledge Graph", json.encodeToString(data))
}
