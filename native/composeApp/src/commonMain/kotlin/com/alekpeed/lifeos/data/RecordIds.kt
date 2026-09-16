package com.alekpeed.lifeos.data

// IDs remain positive Long values, preserving stored references and routes. The upper
// bound is JavaScript's largest exact integer: Firebase's Node gateway parses JSON.
internal const val MAX_RECORD_ID = 9_007_199_254_740_991L
internal const val MIN_RECORD_ID = 4_294_967_296L

// Each call draws fresh OS-backed entropy. No clock, copied device seed, or restored
// counter can make two installations repeat the same sequence. The live-process set
// also retries duplicate draws. Callers on JVM serialize access to this generator.
internal class RecordIdGenerator(private val randomBits: () -> Long) {
    private val issued = mutableSetOf<Long>()
    fun next(): Long {
        while (true) {
            val id = randomBits().ushr(11)
            if (id >= MIN_RECORD_ID && issued.add(id)) return id
        }
    }
}

expect fun newRecordId(): Long
