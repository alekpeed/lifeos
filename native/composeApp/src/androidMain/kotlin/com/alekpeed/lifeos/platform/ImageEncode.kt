package com.alekpeed.lifeos.platform

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import java.io.ByteArrayOutputStream

// Turns a picked/captured image Uri into a base64 JPEG small enough for a vision
// API. Downscales the long edge to ~1280px (the documented cost cap) using a
// power-of-two sample during decode, then an exact scale, then JPEG-compresses.
// Returns null on any failure — the caller treats that as "no photo."
internal object ImageEncode {

    private const val MAX_EDGE = 1280
    private const val JPEG_QUALITY = 85

    fun uriToDownscaledJpegBase64(context: Context, uri: Uri): String? {
        val bitmap = decodeScaled(context, uri) ?: return null
        return try {
            val out = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out)
            Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
        } catch (e: Exception) {
            null
        } finally {
            bitmap.recycle()
        }
    }

    private fun decodeScaled(context: Context, uri: Uri): Bitmap? {
        // First pass: read bounds only, so we can pick an inSampleSize.
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, bounds)
        } ?: return null
        val srcW = bounds.outWidth
        val srcH = bounds.outHeight
        if (srcW <= 0 || srcH <= 0) return null

        var sample = 1
        while (srcW / (sample * 2) >= MAX_EDGE || srcH / (sample * 2) >= MAX_EDGE) {
            sample *= 2
        }

        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        val decoded = context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, opts)
        } ?: return null

        val longEdge = maxOf(decoded.width, decoded.height)
        if (longEdge <= MAX_EDGE) return decoded

        // Exact scale down to the cap.
        val ratio = MAX_EDGE.toFloat() / longEdge
        val w = (decoded.width * ratio).toInt().coerceAtLeast(1)
        val h = (decoded.height * ratio).toInt().coerceAtLeast(1)
        return try {
            val scaled = Bitmap.createScaledBitmap(decoded, w, h, true)
            if (scaled !== decoded) decoded.recycle()
            scaled
        } catch (e: Exception) {
            decoded
        }
    }
}
