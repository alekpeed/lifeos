package com.alekpeed.lifeos.vault

import com.alekpeed.lifeos.Storage
import com.alekpeed.lifeos.TestHome
import com.alekpeed.lifeos.core.isBackupKey
import com.alekpeed.lifeos.history.History
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

// §13.3 — the zero-knowledge vault.
//
// This is the one module where a silent failure is not an inconvenience. Two things have
// to hold and both are testable: nothing readable ever reaches the store, and the crypto
// is used correctly rather than merely called. So the tests go looking for plaintext in
// every place it could leak to, and check the properties that make GCM sound — a fresh
// IV every time, and a tampered blob that refuses rather than returns garbage.
class VaultTest {

    private val pass = "correct horse battery"

    @BeforeTest
    fun setUp() {
        TestHome.clear()
        History.forget()
        Vault.lock()
    }

    @AfterTest
    fun tearDown() {
        Vault.lock()
        TestHome.clear()
    }

    // ---- the primitives ----------------------------------------------------------------

    @Test
    fun `what goes in comes back out`() {
        val key = VaultCrypto.deriveKey(pass, VaultCrypto.randomBytes(VAULT_SALT_BYTES), 1000)
        val blob = VaultCrypto.encrypt(key, "the thing")
        assertEquals("the thing", VaultCrypto.decrypt(key, blob))
    }

    @Test
    fun `the same plaintext never encrypts to the same blob`() {
        // A fresh IV per encryption. Reusing one under a single key is the mistake that
        // breaks GCM outright, so encrypt generates its own and never takes one.
        val key = VaultCrypto.deriveKey(pass, ByteArray(16), 1000)
        val a = VaultCrypto.encrypt(key, "same")
        val b = VaultCrypto.encrypt(key, "same")
        assertNotEquals(a, b)
        assertEquals("same", VaultCrypto.decrypt(key, a))
        assertEquals("same", VaultCrypto.decrypt(key, b))
    }

    @Test
    fun `a wrong key does not open it`() {
        val salt = ByteArray(16)
        val right = VaultCrypto.deriveKey(pass, salt, 1000)
        val wrong = VaultCrypto.deriveKey("something else", salt, 1000)
        assertNull(VaultCrypto.decrypt(wrong, VaultCrypto.encrypt(right, "secret")))
    }

    @Test
    fun `a different salt is a different key`() {
        val a = VaultCrypto.deriveKey(pass, ByteArray(16) { 1 }, 1000)
        val b = VaultCrypto.deriveKey(pass, ByteArray(16) { 2 }, 1000)
        assertFalse(a.contentEquals(b))
        // And the same salt is the same key, which is what lets another device open the
        // vault from the passphrase alone.
        assertTrue(VaultCrypto.deriveKey(pass, ByteArray(16) { 1 }, 1000).contentEquals(a))
    }

    @Test
    fun `a tampered blob refuses rather than returning something plausible`() {
        // The authentication tag earning its place: without it, a flipped bit gives back
        // corrupted plaintext that the app would go on to parse.
        val key = VaultCrypto.deriveKey(pass, ByteArray(16), 1000)
        val blob = VaultCrypto.encrypt(key, """{"entries":[]}""")
        val parts = blob.split(":")
        val body = VaultCrypto.fromBase64(parts[2])!!
        body[body.size / 2] = (body[body.size / 2] + 1).toByte()
        assertNull(VaultCrypto.decrypt(key, "${parts[0]}:${parts[1]}:${VaultCrypto.toBase64(body)}"))
    }

    @Test
    fun `malformed input is a refusal, not a crash`() {
        val key = VaultCrypto.deriveKey(pass, ByteArray(16), 1000)
        for (bad in listOf("", "nonsense", "v1:", "v1:a:b", "v2:${'$'}{VaultCrypto.toBase64(ByteArray(12))}:x", "a:b:c:d")) {
            assertNull(VaultCrypto.decrypt(key, bad), "for: $bad")
        }
    }

    @Test
    fun `wiping a key leaves nothing behind in it`() {
        val key = VaultCrypto.deriveKey(pass, ByteArray(16), 1000)
        assertTrue(key.any { it != 0.toByte() })
        VaultCrypto.wipe(key)
        assertTrue(key.all { it == 0.toByte() })
    }

    // ---- the vault ---------------------------------------------------------------------

    @Test
    fun `a new vault opens, holds something, and reads it back`() {
        assertFalse(Vault.exists())
        assertTrue(Vault.create(pass))
        assertTrue(Vault.exists())
        assertTrue(Vault.unlocked)

        val data = Vault.put(Vault.load()!!, VaultEntry(1, "Router", "hunter2"))
        assertTrue(Vault.save(data))
        assertEquals("hunter2", Vault.load()!!.entries.single().secret)
    }

    @Test
    fun `a short passphrase is refused before anything is written`() {
        assertFalse(Vault.create("short"))
        assertFalse(Vault.exists())
    }

    @Test
    fun `creating over an existing vault is refused`() {
        // Otherwise "set up the vault" would silently be "destroy the vault".
        assertTrue(Vault.create(pass))
        Vault.save(Vault.put(Vault.load()!!, VaultEntry(1, "Kept", "value")))
        assertFalse(Vault.create("a different one"))
        Vault.lock()
        assertTrue(Vault.unlock(pass))
        assertEquals("value", Vault.load()!!.entries.single().secret)
    }

    @Test
    fun `locked means unreadable, not empty`() {
        // Null rather than an empty list: an empty list is a claim about how much you
        // have, and a locked vault makes no claim at all.
        Vault.create(pass)
        Vault.save(Vault.put(Vault.load()!!, VaultEntry(1, "Router", "hunter2")))
        Vault.lock()
        assertFalse(Vault.unlocked)
        assertNull(Vault.load())
        assertFalse(Vault.save(VaultData()))
    }

    @Test
    fun `the wrong passphrase does not unlock and does not damage anything`() {
        Vault.create(pass)
        Vault.save(Vault.put(Vault.load()!!, VaultEntry(1, "Router", "hunter2")))
        Vault.lock()
        assertFalse(Vault.unlock("not the passphrase"))
        assertFalse(Vault.unlocked)
        assertTrue(Vault.unlock(pass))
        assertEquals("hunter2", Vault.load()!!.entries.single().secret)
    }

    @Test
    fun `unlocking a vault that does not exist fails rather than creating one`() {
        assertFalse(Vault.unlock(pass))
        assertFalse(Vault.unlocked)
        assertFalse(Vault.exists())
    }

    // ---- the invariant: nothing readable reaches the store -------------------------------

    @Test
    fun `no plaintext is ever written to storage`() {
        Vault.create(pass)
        Vault.save(Vault.put(Vault.load()!!, VaultEntry(1, "Router admin", "hunter2", "on the shelf")))

        val stored = Storage.read(VAULT_BLOB_KEY)
        assertTrue(looksEncrypted(stored), "the blob is not in the vault format: $stored")
        for (secret in listOf("hunter2", "Router admin", "on the shelf")) {
            assertFalse(stored!!.contains(secret), "found \"$secret\" in the stored blob")
        }
    }

    @Test
    fun `the passphrase itself is nowhere on disk`() {
        Vault.create(pass)
        // Every key the store holds, not just the vault's own — a passphrase that leaked
        // into a settings value would be just as bad.
        for (key in Storage.keys()) {
            val raw = Storage.read(key).orEmpty()
            assertFalse(raw.contains(pass), "the passphrase turned up in \"$key\"")
        }
    }

    @Test
    fun `the mutation log never sees the contents`() {
        // History diffs every write. If a decrypted vault were ever handed to
        // Storage.write, the plaintext would live on in the log after the vault
        // re-locked — the leak that would be hardest to notice.
        Vault.create(pass)
        Vault.save(Vault.put(Vault.load()!!, VaultEntry(1, "Router", "hunter2")))
        val log = Storage.read("__history").orEmpty()
        assertFalse(log.contains("hunter2"), "the mutation log holds the secret")
    }

    @Test
    fun `the ciphertext belongs in a backup and the meta goes with it`() {
        // Both are safe to share — one is unreadable, the other is a salt, which is
        // public by construction. Leaving either out would produce a restore that looks
        // fine and cannot be opened.
        assertTrue(isBackupKey(VAULT_BLOB_KEY))
        assertTrue(isBackupKey(VAULT_META_KEY))
    }

    // ---- changing the passphrase ---------------------------------------------------------

    @Test
    fun `changing the passphrase re-encrypts what is inside`() {
        // The classic way to get this wrong is deriving a new key without rewriting the
        // blob, which locks you out with the passphrase you just set.
        Vault.create(pass)
        Vault.save(Vault.put(Vault.load()!!, VaultEntry(1, "Router", "hunter2")))
        assertTrue(Vault.changePassphrase(pass, "a longer new one"))

        Vault.lock()
        assertFalse(Vault.unlock(pass), "the old passphrase still opens it")
        assertTrue(Vault.unlock("a longer new one"))
        assertEquals("hunter2", Vault.load()!!.entries.single().secret)
    }

    @Test
    fun `a change with the wrong current passphrase changes nothing`() {
        Vault.create(pass)
        Vault.save(Vault.put(Vault.load()!!, VaultEntry(1, "Router", "hunter2")))
        assertFalse(Vault.changePassphrase("wrong", "a longer new one"))
        Vault.lock()
        assertTrue(Vault.unlock(pass))
        assertEquals("hunter2", Vault.load()!!.entries.single().secret)
    }

    @Test
    fun `a change to a too-short passphrase is refused`() {
        Vault.create(pass)
        assertFalse(Vault.changePassphrase(pass, "short"))
        Vault.lock()
        assertTrue(Vault.unlock(pass))
    }

    // ---- destroying it --------------------------------------------------------------------

    @Test
    fun `destroy leaves nothing to unlock`() {
        Vault.create(pass)
        Vault.save(Vault.put(Vault.load()!!, VaultEntry(1, "Router", "hunter2")))
        Vault.destroy()
        assertFalse(Vault.exists())
        assertFalse(Vault.unlocked)
        assertFalse(Vault.unlock(pass))
        assertFalse(Storage.read(VAULT_BLOB_KEY).orEmpty().contains("hunter2"))
    }

    @Test
    fun `a fresh vault after destroying is genuinely fresh`() {
        Vault.create(pass)
        Vault.save(Vault.put(Vault.load()!!, VaultEntry(1, "Router", "hunter2")))
        Vault.destroy()
        assertTrue(Vault.create(pass))
        assertTrue(Vault.load()!!.entries.isEmpty())
    }

    // ---- entries --------------------------------------------------------------------------

    @Test
    fun `putting an entry twice edits it rather than duplicating it`() {
        Vault.create(pass)
        var data = Vault.put(VaultData(), VaultEntry(1, "Router", "old"))
        data = Vault.put(data, VaultEntry(1, "Router", "new"))
        assertEquals(1, data.entries.size)
        assertEquals("new", data.entries.single().secret)
    }

    @Test
    fun `ids do not repeat after a deletion in the middle`() {
        var data = VaultData()
        data = Vault.put(data, VaultEntry(Vault.nextId(data), "A"))
        data = Vault.put(data, VaultEntry(Vault.nextId(data), "B"))
        data = Vault.put(data, VaultEntry(Vault.nextId(data), "C"))
        data = Vault.remove(data, 2)
        assertEquals(4L, Vault.nextId(data))
    }
}
