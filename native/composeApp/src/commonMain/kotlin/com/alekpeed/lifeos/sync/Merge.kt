package com.alekpeed.lifeos.sync

import kotlinx.serialization.json.*

// Same three-way semantics as functions/merge.js. Null Kotlin value means missing;
// JsonNull is an explicit JSON null. Record arrays are merged by id, never position.
object RecordMerge {
    private fun records(v: JsonElement?): Boolean = v is JsonArray && v.all { it is JsonObject && it["id"] is JsonPrimitive } && v.map { it.jsonObject["id"].toString() }.distinct().size == v.size
    fun merge(base: JsonElement?, local: JsonElement?, remote: JsonElement?): JsonElement? {
        if (local == base) return remote
        if (remote == base || local == remote) return local
        if (local is JsonObject && remote is JsonObject && (base == null || base is JsonObject)) {
            val b = base as? JsonObject ?: JsonObject(emptyMap())
            return JsonObject((b.keys + local.keys + remote.keys).mapNotNull { k -> merge(b[k], local[k], remote[k])?.let { k to it } }.toMap())
        }
        if (records(local) && records(remote) && (base == null || records(base))) {
            fun map(v: JsonElement?) = (v as? JsonArray).orEmpty().associateBy { it.jsonObject["id"].toString() }
            val b = map(base); val l = map(local); val r = map(remote)
            return JsonArray((r.keys + l.keys + b.keys).mapNotNull { merge(b[it], l[it], r[it]) })
        }
        return local
    }
    fun text(base: String?, local: String?, remote: String?): String? {
        fun parse(s: String?): JsonElement? = s?.let { runCatching { Json.parseToJsonElement(it) }.getOrElse { JsonPrimitive(s) } }
        val v = merge(parse(base), parse(local), parse(remote)) ?: return null
        return if (v is JsonPrimitive && v.isString) v.content else v.toString()
    }
}
