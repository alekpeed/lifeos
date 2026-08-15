package com.alekpeed.lifeos.attach

// Plain zip read/write. RecordPackage.kt (the record-export/import logic) needs
// this but is otherwise ordinary commonMain code with no platform dependency of
// its own — this is the one seam, actualized once in jvmShared and shared by
// both real targets rather than duplicated per platform.
expect object ZipPackage {
    fun zip(entries: Map<String, ByteArray>): ByteArray
    fun unzip(bytes: ByteArray): Map<String, ByteArray>
}
