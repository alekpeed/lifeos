package com.alekpeed.lifeos.data

import com.alekpeed.lifeos.Storage
import com.alekpeed.lifeos.storageAtomic
import kotlinx.serialization.json.*

// Android alarm request codes are Ints. Never reduce random record IDs modulo 90,000:
// doing that would make unrelated reminders cancel each other. Keep a device-local
// allocation, outside the historical task/capsule/reminder ranges, across restarts.
internal fun recordAlarmId(kind: String, id: Long, legacyBase: Int): Int {
    if (id < MIN_RECORD_ID) return legacyBase + (id % 90_000).toInt()
    return storageAtomic {
        val key = "__record_alarm_ids"
        val slots = Storage.read(key)?.let { Json.parseToJsonElement(it).jsonObject } ?: JsonObject(emptyMap())
        val subject = "$kind:$id"
        slots[subject]?.jsonPrimitive?.int ?: run {
            val previous = slots.values.maxOfOrNull { it.jsonPrimitive.int } ?: 1_000_000
            check(previous < Int.MAX_VALUE) { "Alarm ID capacity exceeded" }
            val next = previous + 1
            Storage.write(key, JsonObject(slots + (subject to JsonPrimitive(next))).toString())
            next
        }
    }
}
