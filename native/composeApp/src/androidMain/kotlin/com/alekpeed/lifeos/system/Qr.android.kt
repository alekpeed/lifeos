package com.alekpeed.lifeos.system

import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import com.google.zxing.qrcode.encoder.Encoder

actual fun encodeQr(text: String): QrMatrix? {
    return try {
        val hints = hashMapOf<EncodeHintType, Any>(EncodeHintType.CHARACTER_SET to "UTF-8")
        val qr = Encoder.encode(text, ErrorCorrectionLevel.M, hints)
        val m = qr.matrix ?: return null
        val w = m.width
        val mods = BooleanArray(w * m.height) { m.get(it % w, it / w).toInt() == 1 }
        QrMatrix(w, mods) // QR grids are square, so w == h
    } catch (e: Exception) {
        null
    }
}
