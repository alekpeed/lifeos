package com.alekpeed.lifeos.vault

// The primitives, and nothing invented.
//
// AES-256-GCM for the data, PBKDF2-HMAC-SHA256 for turning a passphrase into a key.
// Both come from the platform's own crypto provider; there is no hand-rolled cipher
// here and there must never be one. What this file owns is the *parameters* and the
// wire format, which is the part that is easy to get wrong quietly:
//
//   · A fresh 12-byte IV for every single encryption. Reusing an IV under one key is
//     the one mistake that breaks GCM outright, so the IV is generated inside encrypt
//     and never passed in.
//   · The GCM tag is authentication, not decoration: a tampered or truncated blob fails
//     to decrypt rather than returning plausible garbage. `decrypt` returns null for
//     every failure — wrong key, altered bytes, malformed input — because from the
//     caller's side those are the same thing: this did not open.
//   · 256-bit keys, 128-bit tags.
//
// Both targets are the JVM, so the two actuals are the same javax.crypto code. They are
// written twice rather than shared because this project has no jvmMain source set, and
// inventing one to hold the crypto is a larger change than the duplication costs.
expect object VaultCrypto {
    // Cryptographically secure. Used for salts and IVs, never for anything else.
    fun randomBytes(n: Int): ByteArray

    // Passphrase → 256-bit key. Deterministic for a given (passphrase, salt, iterations),
    // which is what makes the vault openable on another device from the passphrase alone.
    fun deriveKey(passphrase: String, salt: ByteArray, iterations: Int): ByteArray

    // "v1:<base64 iv>:<base64 ciphertext+tag>". The version prefix is there so a future
    // change of algorithm can be told apart from a corrupt blob instead of guessed at.
    fun encrypt(key: ByteArray, plaintext: String): String

    // Null on any failure whatsoever. See above.
    fun decrypt(key: ByteArray, blob: String): String?

    fun toBase64(bytes: ByteArray): String
    fun fromBase64(text: String): ByteArray?

    // Overwrite a key in memory when locking. Best-effort by nature — the JVM may have
    // moved the array during a GC and left a copy behind — but the alternative is
    // leaving it there on purpose.
    fun wipe(bytes: ByteArray)
}

// Marks the format so a later version is a decision rather than a mystery.
const val VAULT_FORMAT = "v1"

// OWASP's floor for PBKDF2-HMAC-SHA256. Stored per vault rather than hardcoded at the
// point of use, so raising it later re-derives new vaults without stranding old ones.
const val VAULT_ITERATIONS = 210_000

const val VAULT_KEY_BYTES = 32
const val VAULT_SALT_BYTES = 16
const val VAULT_IV_BYTES = 12
const val VAULT_TAG_BITS = 128
