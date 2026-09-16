package com.alekpeed.lifeos.data

private val entropy = java.security.SecureRandom()
private val recordIds = RecordIdGenerator { entropy.nextLong() }
actual fun newRecordId(): Long = synchronized(recordIds) { recordIds.next() }
