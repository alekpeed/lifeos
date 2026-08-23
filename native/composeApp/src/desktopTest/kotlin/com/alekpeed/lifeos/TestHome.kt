package com.alekpeed.lifeos

// Desktop Storage fixes its directory the first time anything touches it, so user.home
// has to point somewhere disposable before that happens — and it can only be redirected
// once per JVM, which is why every test class shares this one scratch home rather than
// each making its own.
internal object TestHome {
    val dir: java.io.File =
        java.io.File(System.getProperty("java.io.tmpdir"), "lifeos-test-home")
            .apply { deleteRecursively(); mkdirs() }

    init {
        System.setProperty("user.home", dir.absolutePath)
    }

    // Wipe the store between tests. Touching this also guarantees the init above has run.
    fun clear() {
        java.io.File(dir, ".lifeos").listFiles()?.forEach { it.delete() }
    }
}
