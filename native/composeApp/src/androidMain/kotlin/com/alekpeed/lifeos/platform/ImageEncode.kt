package com.alekpeed.lifeos.platform

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.util.Base64
import java.io.ByteArrayOutputStream
import kotlin.math.max

// Turns a picked/captured image Uri into a base64 JPEG small enough for a vision
// API. Downscales the long edge to ~1280px (the documented cost cap) and
// JPEG-compresses. On API 28+ uses ImageDecoder, which decodes HEIC/HEIF (what
// modern phones shoot by default) as well as JPEG/PNG; older devices fall back to
// a sampled BitmapFactory decode. Returns null on any failure.
internal object ImageEncode {

    private const val MAX_EDGE = 1280
    private const val JPEG_QUALITY = 85

    fun uriToDownscaledJpegBase64(context: Context, uri: Uri): String? {
        val bitmap = decodeBitmap(context, uri) ?: return null
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

    private fun decodeBitmap(context: Context, uri: Uri): Bitmap? {
        if (Build.VERSION.SDK_INT >= 28) {
            try {
                val source = ImageDecoder.createSource(context.contentResolver, uri)
                return ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
                    val longEdge = max(info.size.width, info.size.height)
                    if (longEdge > MAX_EDGE) {
                        val ratio = MAX_EDGE.toFloat() / longEdge
                        val w = (info.size.width * ratio).toInt().coerceAtLeast(1)
                        val h = (info.size.height * ratio).toInt().coerceAtLeast(1)
                        decoder.setTargetSize(w, h)
                    }
                    // A software bitmap so it can be JPEG-compressed (hardware bitmaps can't).
                    decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                }
            } catch (e: Exception) {
                // Fall through to the BitmapFactory path.
            }
        }
        return decodeWithBitmapFactory(context, uri)
    }

    private fun decodeWithBitmapFactory(context: Context, uri: Uri): Bitmap? {
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

        val longEdge = max(decoded.width, decoded.height)
        if (longEdge <= MAX_EDGE) return decoded

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
