package com.alekpeed.lifeos.ai

// A WAV wrapper for raw microphone PCM. Both platforms capture 16-bit signed
// little-endian mono at 16 kHz — the format Whisper is happiest with and the
// smallest thing worth sending — and both need the same 44-byte RIFF header in
// front of it before anything will accept the bytes as a file.
//
// Lives in commonMain because it's plain byte arithmetic that androidMain and
// desktopMain would otherwise both have to spell out.

const val MIC_SAMPLE_RATE = 16000
private const val BITS = 16
private const val CHANNELS = 1

fun wavFromPcm16(pcm: ByteArray, sampleRate: Int = MIC_SAMPLE_RATE): ByteArray {
    val byteRate = sampleRate * CHANNELS * BITS / 8
    val blockAlign = CHANNELS * BITS / 8
    val out = ByteArray(44 + pcm.size)
    var i = 0
    fun ascii(s: String) { s.forEach { out[i++] = it.code.toByte() } }
    fun le32(v: Int) {
        out[i++] = (v and 0xFF).toByte()
        out[i++] = ((v shr 8) and 0xFF).toByte()
        out[i++] = ((v shr 16) and 0xFF).toByte()
        out[i++] = ((v shr 24) and 0xFF).toByte()
    }
    fun le16(v: Int) {
        out[i++] = (v and 0xFF).toByte()
        out[i++] = ((v shr 8) and 0xFF).toByte()
    }

    ascii("RIFF"); le32(36 + pcm.size); ascii("WAVE")
    ascii("fmt "); le32(16); le16(1); le16(CHANNELS)
    le32(sampleRate); le32(byteRate); le16(blockAlign); le16(BITS)
    ascii("data"); le32(pcm.size)
    pcm.copyInto(out, 44)
    return out
}
