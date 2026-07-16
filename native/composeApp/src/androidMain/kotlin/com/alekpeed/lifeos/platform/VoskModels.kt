package com.alekpeed.lifeos.platform

import android.content.Context
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.zip.ZipInputStream

// Manages the on-device Vosk speech model. The model (~40 MB) is too large to ship
// in the repo/APK, so it's fetched once on first use and unpacked into the app's
// private storage. Everything here runs off the main thread; callers hop back to
// the UI/service thread in their callbacks.
object VoskModels {

    // Small English recognition model — enough for keyword spotting + short capture.
    private const val MODEL_NAME = "vosk-model-small-en-us-0.15"
    private const val MODEL_URL = "https://alphacephei.com/vosk/models/$MODEL_NAME.zip"

    // Speaker-embedding model — produces the voiceprint used for "only my voice".
    private const val SPK_NAME = "vosk-model-spk-0.4"
    private const val SPK_URL = "https://alphacephei.com/vosk/models/$SPK_NAME.zip"

    fun modelDir(ctx: Context): File = File(ctx.filesDir, MODEL_NAME)
    fun speakerDir(ctx: Context): File = File(ctx.filesDir, SPK_NAME)

    // A real Vosk model always contains an acoustic-model ("am") subfolder; use it
    // as the "fully unpacked" marker so a half-finished download isn't mistaken for
    // a ready model.
    fun isModelReady(ctx: Context): Boolean = File(modelDir(ctx), "am").isDirectory
    fun isSpeakerReady(ctx: Context): Boolean = File(speakerDir(ctx), "mfcc.conf").exists() ||
        speakerDir(ctx).isDirectory && (speakerDir(ctx).list()?.isNotEmpty() == true)

    // Ensure the recognition model is present, downloading+unpacking if needed.
    // Blocking — call from a background thread. Returns true if the model is ready.
    fun ensureModel(ctx: Context, onProgress: (Int) -> Unit = {}): Boolean {
        if (isModelReady(ctx)) return true
        return downloadAndUnzip(ctx, MODEL_URL, onProgress) && isModelReady(ctx)
    }

    // Ensure the speaker-embedding model is present. Blocking — background thread.
    fun ensureSpeakerModel(ctx: Context, onProgress: (Int) -> Unit = {}): Boolean {
        if (isSpeakerReady(ctx)) return true
        return downloadAndUnzip(ctx, SPK_URL, onProgress) && isSpeakerReady(ctx)
    }

    // Streams the zip to a temp file, then unpacks it into filesDir. The archives
    // are top-level-foldered (e.g. "vosk-model-small-en-us-0.15/..."), so unpacking
    // at filesDir lands each model in its own directory.
    private fun downloadAndUnzip(ctx: Context, url: String, onProgress: (Int) -> Unit): Boolean {
        val tmp = File(ctx.cacheDir, "voskdl-${url.hashCode()}.zip")
        try {
            val conn = (URL(url).openConnection() as HttpURLConnection).apply {
                connectTimeout = 30_000
                readTimeout = 60_000
                instanceFollowRedirects = true
            }
            conn.connect()
            if (conn.responseCode !in 200..299) {
                conn.disconnect()
                return false
            }
            val total = conn.contentLength.toLong()
            conn.inputStream.use { input ->
                tmp.outputStream().use { out ->
                    val buf = ByteArray(64 * 1024)
                    var read: Int
                    var got = 0L
                    var lastPct = -1
                    while (input.read(buf).also { read = it } >= 0) {
                        out.write(buf, 0, read)
                        got += read
                        if (total > 0) {
                            val pct = ((got * 100) / total).toInt().coerceIn(0, 99)
                            if (pct != lastPct) { lastPct = pct; onProgress(pct) }
                        }
                    }
                }
            }
            conn.disconnect()
            unzipInto(tmp, ctx.filesDir)
            onProgress(100)
            return true
        } catch (e: Exception) {
            return false
        } finally {
            tmp.delete()
        }
    }

    private fun unzipInto(zip: File, destDir: File) {
        val destPath = destDir.canonicalPath
        ZipInputStream(zip.inputStream().buffered()).use { zin ->
            var entry = zin.nextEntry
            while (entry != null) {
                val outFile = File(destDir, entry.name)
                // Guard against zip-slip: never write outside destDir.
                if (!outFile.canonicalPath.startsWith(destPath + File.separator)) {
                    entry = zin.nextEntry
                    continue
                }
                if (entry.isDirectory) {
                    outFile.mkdirs()
                } else {
                    outFile.parentFile?.mkdirs()
                    outFile.outputStream().use { zin.copyTo(it) }
                }
                zin.closeEntry()
                entry = zin.nextEntry
            }
        }
    }
}
