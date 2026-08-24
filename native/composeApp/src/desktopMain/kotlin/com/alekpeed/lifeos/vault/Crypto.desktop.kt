package com.alekpeed.lifeos.vault

import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

// See Crypto.kt in commonMain for the reasoning. This is the platform's own crypto
// provider doing the work — nothing here implements a cipher.
//
// This file is byte-identical to its counterpart on the other target. Both are the JVM;
// the project has no shared jvmMain source set, and adding one to hold forty lines of
// JCA calls is a bigger change than writing them twice.
actual object VaultCrypto {

    private val rng = SecureRandom()

    actual fun randomBytes(n: Int): ByteArray = ByteArray(n).also { rng.nextBytes(it) }

    actual fun deriveKey(passphrase: String, salt: ByteArray, iterations: Int): ByteArray {
        val spec = PBEKeySpec(passphrase.toCharArray(), salt, iterations, VAULT_KEY_BYTES * 8)
        return try {
            SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        } finally {
            // The spec holds its own copy of the passphrase characters.
            spec.clearPassword()
        }
    }

    actual fun encrypt(key: ByteArray, plaintext: String): String {
        // A fresh IV per encryption, generated here so a caller cannot supply one twice.
        val iv = randomBytes(VAULT_IV_BYTES)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(VAULT_TAG_BITS, iv))
        val out = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))
        return "$VAULT_FORMAT:${toBase64(iv)}:${toBase64(out)}"
    }

    actual fun decrypt(key: ByteArray, blob: String): String? {
        val parts = blob.split(":")
        if (parts.size != 3 || parts[0] != VAULT_FORMAT) return null
        val iv = fromBase64(parts[1]) ?: return null
        if (iv.size != VAULT_IV_BYTES) return null
        val body = fromBase64(parts[2]) ?: return null
        return try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(VAULT_TAG_BITS, iv))
            String(cipher.doFinal(body), Charsets.UTF_8)
        } catch (e: Exception) {
            // Wrong key, altered bytes, truncated input — all the same answer: it did
            // not open. Distinguishing them for the caller would leak which it was.
            null
        }
    }

    actual fun toBase64(bytes: ByteArray): String = Base64.getEncoder().encodeToString(bytes)

    actual fun fromBase64(text: String): ByteArray? =
        try { Base64.getDecoder().decode(text) } catch (e: Exception) { null }

    actual fun wipe(bytes: ByteArray) = bytes.fill(0)
}
