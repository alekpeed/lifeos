package com.alekpeed.lifeos.platform

import com.alekpeed.lifeos.Storage
import org.json.JSONObject
import kotlin.math.sqrt

// "Only my voice" support. Vosk's speaker model turns an utterance into an x-vector
// voiceprint; we store the owner's averaged voiceprint at enrollment and, at trigger
// time, compare the incoming utterance's voiceprint to it with cosine similarity.
//
// Honest limits (see the handoff doc): this is a *filter*, not a lock — short-phrase
// speaker verification has real error rates, degrades with colds/noise/distance, and
// a recording of the owner's voice can spoof it. It meaningfully cuts other people
// accidentally triggering capture; it is not a security control.
object VoiceId {

    private const val KEY_PRINT = "VoicePrint"
    private const val KEY_ONLY = "OnlyMyVoice"
    private const val KEY_THRESHOLD = "VoiceThreshold"
    private const val DEFAULT_THRESHOLD = 0.55f

    fun isOnlyMyVoiceEnabled(): Boolean = Storage.read(KEY_ONLY) == "1"

    fun setOnlyMyVoice(on: Boolean) = Storage.write(KEY_ONLY, if (on) "1" else "0")

    fun hasVoiceprint(): Boolean = readEnrolled() != null

    fun clearVoiceprint() = Storage.write(KEY_PRINT, "")

    fun threshold(): Float =
        Storage.read(KEY_THRESHOLD)?.toFloatOrNull()?.coerceIn(0f, 1f) ?: DEFAULT_THRESHOLD

    fun saveVoiceprint(vec: FloatArray) = Storage.write(KEY_PRINT, serialize(vec))

    fun readEnrolled(): FloatArray? {
        val raw = Storage.read(KEY_PRINT)?.trim().orEmpty()
        if (raw.isEmpty()) return null
        return parse(raw)
    }

    // Does this utterance's voiceprint match the enrolled owner? If nothing is
    // enrolled, there's nothing to check against, so we don't block (returns true).
    fun matchesOwner(spk: FloatArray?): Boolean {
        val enrolled = readEnrolled() ?: return true
        if (spk == null || spk.size != enrolled.size) return false
        return cosine(spk, enrolled) >= threshold()
    }

    // Pull the "spk" x-vector out of a Vosk result JSON, if present.
    fun extractSpk(hypothesisJson: String?): FloatArray? {
        hypothesisJson ?: return null
        return try {
            val arr = JSONObject(hypothesisJson).optJSONArray("spk") ?: return null
            FloatArray(arr.length()) { arr.getDouble(it).toFloat() }
        } catch (e: Exception) {
            null
        }
    }

    fun average(vectors: List<FloatArray>): FloatArray? {
        val clean = vectors.filter { it.isNotEmpty() }
        if (clean.isEmpty()) return null
        val n = clean.first().size
        if (clean.any { it.size != n }) return clean.first()
        val out = FloatArray(n)
        for (v in clean) for (i in 0 until n) out[i] += v[i]
        for (i in 0 until n) out[i] /= clean.size
        return out
    }

    private fun serialize(vec: FloatArray): String = vec.joinToString(",")

    private fun parse(s: String): FloatArray? {
        val parts = s.split(",")
        if (parts.isEmpty()) return null
        return try {
            FloatArray(parts.size) { parts[it].trim().toFloat() }
        } catch (e: Exception) {
            null
        }
    }

    private fun cosine(a: FloatArray, b: FloatArray): Float {
        var dot = 0f; var na = 0f; var nb = 0f
        for (i in a.indices) { dot += a[i] * b[i]; na += a[i] * a[i]; nb += b[i] * b[i] }
        if (na == 0f || nb == 0f) return 0f
        return dot / (sqrt(na) * sqrt(nb))
    }
}
