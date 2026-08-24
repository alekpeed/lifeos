package com.alekpeed.lifeos.vault

import com.alekpeed.lifeos.Storage
import com.alekpeed.lifeos.data.today
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

// The zero-knowledge vault (§13.3): things that should be ciphertext everywhere except
// in front of you.
//
// **What "zero-knowledge" means here, exactly.** The passphrase is never stored, never
// synced, and never leaves the device. The key derived from it lives in memory and only
// while unlocked. Supabase receives what is on disk, and what is on disk is a blob it
// cannot read. Nobody with the database — including a future me with a service-role key
// — can open it.
//
// **The tradeoff this forces, stated up front rather than discovered later.** The
// Telegram digest and the FCM sender read module blobs server-side to work out what is
// due (§7 D-5). They cannot read what they cannot decrypt. So this is a *vault* — one
// module, opted into — not encryption over the whole app: encrypting everything would
// silently break the two things that reach you when the app is closed. You cannot have
// server-side digests and zero-knowledge storage for the same records, and pretending
// otherwise would mean shipping a digest that quietly went empty.
//
// **What it does not protect against**, because a security feature that overstates
// itself is worse than none. It does not protect a device somebody else is holding while
// the vault is open. It does not protect against a keylogger or a screen recorder. It
// does not protect the plaintext you copy to the clipboard. It protects the data at
// rest and in transit, against anyone who gets the file or the database row.
//
// **There is no recovery.** No reset, no hint, no support address. A forgotten
// passphrase is the data gone, permanently, by design — a recovery path is a second way
// in, which is the thing this exists to not have.

// The encrypted blob. Ordinary key, so it syncs like everything else — that is the
// point, and it is also why it stays in the backup: ciphertext in a backup is safe, and
// a backup without it would restore a vault with nothing in it.
const val VAULT_BLOB_KEY = "Vault"

// Salt, iteration count and the verifier. None of it is secret — a salt is public by
// construction — and all of it is needed to open the vault on another device, so it
// syncs and backs up alongside.
const val VAULT_META_KEY = "VaultMeta"

// Encrypted with the derived key at creation and checked at unlock. Without it, a wrong
// passphrase and a corrupt blob look identical, and the app would tell you your notes
// were damaged when you had simply mistyped.
private const val VERIFIER_PLAINTEXT = "lifeos-vault-v1"

@Serializable
data class VaultMeta(
    val format: String = VAULT_FORMAT,
    val salt: String = "",
    val iterations: Int = VAULT_ITERATIONS,
    val verifier: String = "",
) {
    val exists: Boolean get() = salt.isNotBlank() && verifier.isNotBlank()
}

@Serializable
data class VaultEntry(
    val id: Long,
    val title: String,
    val secret: String = "",
    val notes: String = "",
    val updated: String = "",
)

@Serializable
data class VaultData(val entries: List<VaultEntry> = emptyList())

private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

fun loadVaultMeta(): VaultMeta {
    val raw = Storage.read(VAULT_META_KEY)
    if (raw.isNullOrBlank()) return VaultMeta()
    return runCatching { json.decodeFromString<VaultMeta>(raw) }.getOrElse { VaultMeta() }
}

object Vault {

    // In memory, for as long as the vault is open, and nowhere else. Not a Compose
    // state, not a Storage key, not a companion of anything that gets serialized.
    private var key: ByteArray? = null

    val unlocked: Boolean get() = key != null

    fun exists(): Boolean = loadVaultMeta().exists

    // First run. Refuses to overwrite an existing vault, because doing so would destroy
    // its contents while looking like setup.
    fun create(passphrase: String): Boolean {
        if (exists()) return false
        if (passphrase.length < MIN_PASSPHRASE) return false
        val salt = VaultCrypto.randomBytes(VAULT_SALT_BYTES)
        val derived = VaultCrypto.deriveKey(passphrase, salt, VAULT_ITERATIONS)
        val meta = VaultMeta(
            salt = VaultCrypto.toBase64(salt),
            iterations = VAULT_ITERATIONS,
            verifier = VaultCrypto.encrypt(derived, VERIFIER_PLAINTEXT),
        )
        Storage.write(VAULT_META_KEY, json.encodeToString(meta))
        key = derived
        save(VaultData())
        return true
    }

    fun unlock(passphrase: String): Boolean {
        val meta = loadVaultMeta()
        if (!meta.exists) return false
        val salt = VaultCrypto.fromBase64(meta.salt) ?: return false
        val derived = VaultCrypto.deriveKey(passphrase, salt, meta.iterations)
        if (VaultCrypto.decrypt(derived, meta.verifier) != VERIFIER_PLAINTEXT) {
            VaultCrypto.wipe(derived)
            return false
        }
        key = derived
        return true
    }

    fun lock() {
        key?.let { VaultCrypto.wipe(it) }
        key = null
    }

    // Null when locked, and null is the honest answer: there is nothing readable here
    // without the key. An empty list would be a lie about how much you have.
    fun load(): VaultData? {
        val k = key ?: return null
        val raw = Storage.read(VAULT_BLOB_KEY)?.trim().orEmpty()
        if (raw.isEmpty()) return VaultData()
        val plain = VaultCrypto.decrypt(k, raw) ?: return null
        return runCatching { json.decodeFromString<VaultData>(plain) }.getOrNull()
    }

    // Only ever writes ciphertext. There is no path in this file that puts a decrypted
    // vault into Storage, which is what keeps the plaintext out of the mutation log, the
    // sync push and the backup alike.
    fun save(data: VaultData): Boolean {
        val k = key ?: return false
        Storage.write(VAULT_BLOB_KEY, VaultCrypto.encrypt(k, json.encodeToString(data)))
        return true
    }

    // Re-encrypts the contents under the new key. Deriving a new key without rewriting
    // the blob would lock you out of your own vault with the passphrase you just set,
    // which is the classic way to get this wrong.
    fun changePassphrase(old: String, new: String): Boolean {
        if (new.length < MIN_PASSPHRASE) return false
        if (!unlock(old)) return false
        val data = load() ?: return false
        val salt = VaultCrypto.randomBytes(VAULT_SALT_BYTES)
        val derived = VaultCrypto.deriveKey(new, salt, VAULT_ITERATIONS)
        val meta = VaultMeta(
            salt = VaultCrypto.toBase64(salt),
            iterations = VAULT_ITERATIONS,
            verifier = VaultCrypto.encrypt(derived, VERIFIER_PLAINTEXT),
        )
        // The blob first: if this is interrupted between the two writes, an old blob
        // with new meta is unopenable, while a new blob with old meta is merely stale
        // until the next save. Neither is good; only one loses data.
        key = derived
        val wrote = save(data)
        if (!wrote) return false
        Storage.write(VAULT_META_KEY, json.encodeToString(meta))
        return true
    }

    // The only way out of a forgotten passphrase, and it is not recovery — it is
    // deletion, and the screen makes you say so. Kept because the alternative is a
    // permanently dead module on the list.
    fun destroy() {
        lock()
        Storage.write(VAULT_BLOB_KEY, "")
        Storage.write(VAULT_META_KEY, "")
    }

    fun nextId(data: VaultData): Long = (data.entries.maxOfOrNull { it.id } ?: 0L) + 1

    fun put(data: VaultData, entry: VaultEntry): VaultData {
        val stamped = entry.copy(updated = today().toString())
        return if (data.entries.any { it.id == entry.id }) {
            data.copy(entries = data.entries.map { if (it.id == entry.id) stamped else it })
        } else {
            data.copy(entries = listOf(stamped) + data.entries)
        }
    }

    fun remove(data: VaultData, id: Long): VaultData =
        data.copy(entries = data.entries.filterNot { it.id == id })
}

// Short enough to type on a phone, long enough to be worth the 210,000 rounds behind
// it. A four-digit PIN would make the derivation theatre.
const val MIN_PASSPHRASE = 8

// True when a stored value is one of ours rather than plaintext somebody wrote there.
// Used by the tests that guard the one invariant this module has: nothing readable ever
// reaches the store.
fun looksEncrypted(raw: String?): Boolean =
    !raw.isNullOrBlank() && raw.startsWith("$VAULT_FORMAT:") && raw.split(":").size == 3
