package com.alekpeed.lifeos.platform

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import kotlin.math.abs

// Downscale to a small grid, bucket pixels into a coarse color cube, and return
// the most common buckets (their average color) as a handful of distinct swatches.
actual fun paletteFromBase64(base64: String): List<String> {
    return try {
        val bytes = Base64.decode(base64, Base64.DEFAULT)
        val full = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return emptyList()
        val small = Bitmap.createScaledBitmap(full, 56, 56, true)
        if (small !== full) full.recycle()

        val sumR = HashMap<Int, Long>()
        val sumG = HashMap<Int, Long>()
        val sumB = HashMap<Int, Long>()
        val cnt = HashMap<Int, Int>()
        for (y in 0 until small.height) {
            for (x in 0 until small.width) {
                val p = small.getPixel(x, y)
                if (((p ushr 24) and 0xFF) < 128) continue // skip transparent
                val r = (p ushr 16) and 0xFF
                val g = (p ushr 8) and 0xFF
                val b = p and 0xFF
                val key = (r / 32) * 64 + (g / 32) * 8 + (b / 32)
                sumR[key] = (sumR[key] ?: 0L) + r
                sumG[key] = (sumG[key] ?: 0L) + g
                sumB[key] = (sumB[key] ?: 0L) + b
                cnt[key] = (cnt[key] ?: 0) + 1
            }
        }
        small.recycle()

        val out = ArrayList<String>()
        for (e in cnt.entries.sortedByDescending { it.value }) {
            val k = e.key
            val c = e.value
            val r = (sumR[k]!! / c).toInt()
            val g = (sumG[k]!! / c).toInt()
            val b = (sumB[k]!! / c).toInt()
            val hex = "#%02X%02X%02X".format(r, g, b)
            if (out.any { colorsClose(it, hex) }) continue
            out.add(hex)
            if (out.size >= 8) break
        }
        out
    } catch (e: Exception) {
        emptyList()
    }
}

private fun colorsClose(a: String, b: String): Boolean {
    val ca = hexToRgb(a) ?: return false
    val cb = hexToRgb(b) ?: return false
    return abs(ca[0] - cb[0]) + abs(ca[1] - cb[1]) + abs(ca[2] - cb[2]) < 48
}

private fun hexToRgb(h: String): IntArray? {
    val s = h.removePrefix("#")
    if (s.length != 6) return null
    val v = s.toLongOrNull(16) ?: return null
    return intArrayOf(((v shr 16) and 0xFF).toInt(), ((v shr 8) and 0xFF).toInt(), (v and 0xFF).toInt())
}
