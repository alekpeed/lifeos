package com.alekpeed.lifeos.ai

import com.alekpeed.lifeos.Storage
import com.alekpeed.lifeos.net.httpSendBytes
import com.alekpeed.lifeos.platform.Native
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

// Speech-to-text through OpenAI's transcription endpoint, so the mic works the same
// everywhere. It matters most on desktop, which has no dictation of its own at all,
// but it's also better than the phone's built-in recognizer: real punctuation, no
// system dialog taking over the screen, and it doesn't cut you off mid-thought.
//
// Recording is the platform's job (Native.startRecording / stopRecording, which hand
// back a base64 WAV); this file is only the upload. The endpoint wants
// multipart/form-data, which nothing else in the app sends, so the body is assembled
// here as bytes and pushed through httpSendBytes — the same path a Storage upload
// takes.

// The transcription model. If this starts 404ing, check it against OpenAI's current
// model list; "whisper-1" is the long-standing fallback that has never gone away.
const val DEFAULT_WHISPER_MODEL = "whisper-1"

private const val ENGINE_KEY = "DictationEngine"   // "whisper" | "system"
private const val BOUNDARY = "----LifeOSAudioBoundary7f3a91"

object Whisper {

    private val json = Json { ignoreUnknownKeys = true }

    fun model(): String = Storage.read("WhisperModel")?.trim()?.ifBlank { null } ?: DEFAULT_WHISPER_MODEL

    // Whether this device can do it at all: a mic we can drive ourselves, plus a key.
    fun possible(): Boolean = Native.supportsRecording && OpenAiClient.key().isNotEmpty()

    // Whether it's what we should actually use. On a phone the system recognizer is a
    // reasonable thing to prefer (offline, free), so the choice is a setting — but the
    // default is Whisper wherever it's possible, since that's the better transcript.
    fun enabled(): Boolean = possible() && Storage.read(ENGINE_KEY) != "system"

    fun setEngine(useWhisper: Boolean) = Storage.write(ENGINE_KEY, if (useWhisper) "whisper" else "system")

    // Send a recording, get the text. `hint` is passed as the prompt, which is how the
    // model is told about words it wouldn't otherwise guess — proper nouns, the app's
    // own vocabulary — so a name doesn't come back spelled three different ways.
    @OptIn(ExperimentalEncodingApi::class)
    suspend fun transcribe(wavBase64: String, hint: String = ""): Result<String> {
        val key = OpenAiClient.key()
        if (key.isEmpty()) return Result.failure(IllegalStateException("Add an OpenAI key in Settings to use the mic."))
        if (wavBase64.isBlank()) return Result.failure(IllegalStateException("Nothing was recorded."))

        val audio = runCatching { Base64.decode(wavBase64) }
            .getOrElse { return Result.failure(IllegalStateException("That recording couldn't be read.")) }
        // 44 bytes is an empty WAV — header and no samples.
        if (audio.size <= 44) return Result.failure(IllegalStateException("Nothing was recorded."))
        if (audio.size > 24 * 1024 * 1024) return Result.failure(IllegalStateException("That recording is too long to send."))

        val body = multipart(audio, hint)
        val resp = httpSendBytes(
            "POST",
            "https://api.openai.com/v1/audio/transcriptions",
            mapOf(
                "Authorization" to "Bearer $key",
                "Content-Type" to "multipart/form-data; boundary=$BOUNDARY",
            ),
            Base64.encode(body),
        )
        if (resp.status !in 200..299) return Result.failure(IllegalStateException(errorFrom(resp.status, resp.body)))

        val text = runCatching {
            json.parseToJsonElement(resp.body).jsonObject["text"]?.jsonPrimitive?.content
        }.getOrNull()?.trim()
        if (text.isNullOrBlank()) return Result.failure(IllegalStateException("Nothing was said, or nothing came back."))
        return Result.success(text)
    }

    // The form body, byte for byte: the audio as a file part, then the plain fields.
    @OptIn(ExperimentalEncodingApi::class)
    private fun multipart(audio: ByteArray, hint: String): ByteArray {
        val head = buildString {
            append("--$BOUNDARY\r\n")
            append("Content-Disposition: form-data; name=\"file\"; filename=\"speech.wav\"\r\n")
            append("Content-Type: audio/wav\r\n\r\n")
        }.encodeToByteArray()

        val tail = buildString {
            append("\r\n--$BOUNDARY\r\n")
            append("Content-Disposition: form-data; name=\"model\"\r\n\r\n")
            append(model())
            append("\r\n--$BOUNDARY\r\n")
            append("Content-Disposition: form-data; name=\"response_format\"\r\n\r\n")
            append("json")
            if (hint.isNotBlank()) {
                append("\r\n--$BOUNDARY\r\n")
                append("Content-Disposition: form-data; name=\"prompt\"\r\n\r\n")
                append(hint.take(800))
            }
            append("\r\n--$BOUNDARY--\r\n")
        }.encodeToByteArray()

        val out = ByteArray(head.size + audio.size + tail.size)
        head.copyInto(out, 0)
        audio.copyInto(out, head.size)
        tail.copyInto(out, head.size + audio.size)
        return out
    }

    // Turn the API's error shape into one line worth showing on a phone.
    private fun errorFrom(status: Int, body: String): String {
        val msg = runCatching {
            json.parseToJsonElement(body).jsonObject["error"]?.jsonObject?.get("message")?.jsonPrimitive?.content
        }.getOrNull()
        return when {
            !msg.isNullOrBlank() -> msg
            status == 401 -> "That OpenAI key was rejected."
            status == -1 -> "No connection."
            else -> "Transcription failed ($status)."
        }
    }
}
