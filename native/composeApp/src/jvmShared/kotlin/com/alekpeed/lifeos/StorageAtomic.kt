package com.alekpeed.lifeos
private val storageMonitor = Any()
actual fun <T> storageAtomic(block: () -> T): T = synchronized(storageMonitor, block)
