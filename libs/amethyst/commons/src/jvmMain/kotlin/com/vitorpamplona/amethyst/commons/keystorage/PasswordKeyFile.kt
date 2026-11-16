/*
 * Copyright (c) 2025 Vitor Pamplona
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of
 * this software and associated documentation files (the "Software"), to deal in
 * the Software without restriction, including without limitation the rights to use,
 * copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the
 * Software, and to permit persons to whom the Software is furnished to do so,
 * subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS
 * FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR
 * COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN
 * AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION
 * WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */
package com.vitorpamplona.amethyst.commons.keystorage

import com.vitorpamplona.amethyst.commons.util.restrictToOwner
import com.vitorpamplona.quartz.nip49PrivKeyEnc.Nip49
import com.vitorpamplona.quartz.utils.Hex
import com.vitorpamplona.quartz.utils.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.Base64
import java.util.Properties

/**
 * Private keys kept without an OS keyring: each one a NIP-49 `ncryptsec` (scrypt,
 * XChaCha20-Poly1305) under one device password, in [file]. The password is asked through
 * [KeyStoragePrompts] (chosen when the first key is kept, typed once per launch after that) and
 * held in memory for this run only.
 *
 * Only 32-byte private keys fit NIP-49; anything else is refused.
 *
 * [legacyFile] is the old console-password fallback (`npub:base64(AES-GCM)` lines). When the
 * password typed here also opens it, its private keys move into [file]; it is renamed aside once
 * nothing is left in it that [file] cannot hold (the legacy app's metadata key is not a private
 * key), and until then reads fall back to it.
 */
internal class PasswordKeyFile(
    private val file: File,
    private val legacyFile: File? = null,
    private val legacyDecrypt: (ciphertext: String, password: String) -> String = { _, _ -> error("no legacy cipher") },
    private val logN: Int = Nip49.DEFAULT_LOG_N,
) {
    private val mutex = Mutex()

    @Volatile
    private var password: String? = null

    /** The user chose to keep no key on this device for this run. */
    @Volatile
    var declined: Boolean = false
        private set

    /** The user skipped the unlock dialog for this run: reads come back empty, no re-asking. */
    @Volatile
    private var skippedUnlock: Boolean = false

    val isUnlocked: Boolean get() = password != null

    /**
     * Whether a password was ever chosen: keys exist under one. A legacy file with nothing readable
     * in it (empty, or not the old format) does not count: no password could open it, so the user
     * could never choose a new one.
     */
    fun hasPassword(): Boolean = read().isNotEmpty() || legacyEntries().isNotEmpty()

    fun status(): KeyStorageStatus =
        if (declined && !hasPassword()) {
            KeyStorageStatus.NotKept
        } else {
            KeyStorageStatus.PasswordFile(hasPassword = hasPassword(), unlocked = isUnlocked)
        }

    suspend fun get(
        alias: String,
        prompts: KeyStoragePrompts?,
    ): String? =
        mutex.withLock {
            // A key only the legacy file holds is still a key: answering "absent" for it would load
            // the account read-only, or let the legacy import mint a new metadata key.
            if (alias !in read() && alias !in legacyEntries()) return@withLock null
            val pass = password ?: unlockLocked(prompts) ?: return@withLock null
            // Unlocking moved the legacy private keys over; what could not move is read in place.
            read()[alias]?.let { return@withLock Nip49().decrypt(it, pass) }
            legacyEntries()[alias]?.let { legacyDecrypt(it, pass) }
        }

    /**
     * Keeps [privKeyHex] under [alias]. Throws [KeyNotKeptException] when the user chose not to
     * keep keys, [KeyFileLockedException] when they skipped the password this run, and
     * [SecureStorageException] when nothing can ask them.
     */
    suspend fun put(
        alias: String,
        privKeyHex: String,
        prompts: KeyStoragePrompts?,
    ) {
        if (privKeyHex.length != 64 || !Hex.isHex(privKeyHex)) {
            throw SecureStorageException("Without an OS keyring only private keys can be kept")
        }
        mutex.withLock {
            val pass =
                password ?: if (hasPassword()) {
                    unlockLocked(prompts) ?: throw KeyFileLockedException("The key file stayed locked")
                } else {
                    createLocked(prompts)
                }
            val entries = read().toMutableMap()
            entries[alias] = Nip49().encrypt(privKeyHex, pass, logN)
            write(entries)
        }
    }

    suspend fun delete(alias: String): Boolean =
        mutex.withLock {
            val entries = read().toMutableMap()
            if (entries.remove(alias) == null) return@withLock false
            if (entries.isEmpty()) {
                file.delete()
                password = null
            } else {
                write(entries)
            }
            true
        }

    /**
     * Takes back this run's "Don't save" (Save keys): the next key to keep asks for a password
     * again. A login that already declined keeps its account's choice until it logs in again.
     */
    fun allowKeeping() {
        declined = false
    }

    /** Asks for the password now (Key Backup › Unlock), even after a skip. */
    suspend fun unlock(prompts: KeyStoragePrompts?) {
        mutex.withLock {
            skippedUnlock = false
            if (password == null && hasPassword()) unlockLocked(prompts)
        }
    }

    private suspend fun createLocked(prompts: KeyStoragePrompts?): String {
        if (declined) throw KeyNotKeptException("Keys are not kept on this device")
        val asker = prompts ?: throw SecureStorageException("No OS keyring, and nothing can ask for a password")
        return when (val answer = asker.createPassword()) {
            is NewPasswordAnswer.Password -> answer.password.also { password = it }
            NewPasswordAnswer.DontRemember -> {
                declined = true
                throw KeyNotKeptException("Keys are not kept on this device")
            }
        }
    }

    private suspend fun unlockLocked(prompts: KeyStoragePrompts?): String? {
        if (skippedUnlock) return null
        val asker = prompts ?: return null
        var wrong = false
        while (true) {
            val typed = asker.unlock(wrong)
            if (typed == null) {
                skippedUnlock = true
                return null
            }
            if (opens(typed)) {
                password = typed
                migrateLegacy(typed)
                return typed
            }
            wrong = true
        }
    }

    /** Whether [candidate] opens the file: the first entry decrypts, or the legacy file does. */
    private fun opens(candidate: String): Boolean {
        val sample = read().values.firstOrNull()
        if (sample != null) return runCatching { Nip49().decrypt(sample, candidate) }.isSuccess
        val legacySample = legacyEntries().values.firstOrNull() ?: return false
        return runCatching { legacyDecrypt(legacySample, candidate) }.isSuccess
    }

    private fun migrateLegacy(pass: String) {
        val legacy = legacyFile ?: return
        val old = legacyEntries()
        if (old.isEmpty()) return
        try {
            val entries = read().toMutableMap()
            old.forEach { (alias, ciphertext) ->
                val plain = runCatching { legacyDecrypt(ciphertext, pass) }.getOrNull() ?: return@forEach
                if (alias !in entries && plain.length == 64 && Hex.isHex(plain)) {
                    entries[alias] = Nip49().encrypt(plain, pass, logN)
                }
            }
            write(entries)
            // Kept while it holds anything the new file cannot (a metadata key, a URI): reads fall
            // back to it, and renaming it aside would hide those for good.
            if (old.keys.all { it in entries }) {
                legacy.renameTo(File(legacy.parentFile, legacy.name + ".migrated"))
            }
            Log.d(TAG) { "Moved the private keys out of the old password file" }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Log.w(TAG, "Could not move the keys of the old password file", e)
        }
    }

    private fun legacyEntries(): Map<String, String> {
        val legacy = legacyFile ?: return emptyMap()
        if (!legacy.exists()) return emptyMap()
        return legacy
            .readLines()
            .mapNotNull { line ->
                val parts = line.split(":", limit = 2)
                if (parts.size == 2 && isLegacyCiphertext(parts[1])) parts[0] to parts[1] else null
            }.toMap()
    }

    /** Base64 of salt (16), IV (12) and an AES-GCM ciphertext with its 16-byte tag. */
    private fun isLegacyCiphertext(value: String): Boolean {
        val bytes = runCatching { Base64.getDecoder().decode(value.trim()) }.getOrNull() ?: return false
        return bytes.size > LEGACY_HEADER_AND_TAG
    }

    private fun read(): Map<String, String> {
        if (!file.exists()) return emptyMap()
        val props = Properties()
        file.inputStream().use { props.load(it) }
        return props.stringPropertyNames().associateWith { props.getProperty(it) }
    }

    private fun write(entries: Map<String, String>) {
        file.parentFile?.let { parent ->
            parent.mkdirs()
            parent.restrictToOwner(TAG)
        }
        val temp = File(file.parentFile, file.name + ".tmp")
        temp.delete()
        temp.createNewFile()
        temp.restrictToOwner(TAG)
        val props = Properties()
        entries.forEach { (k, v) -> props.setProperty(k, v) }
        temp.outputStream().use { props.store(it, "Amethyst private keys, NIP-49 encrypted") }
        Files.move(temp.toPath(), file.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
    }

    companion object {
        private const val TAG = "PasswordKeyFile"
        private const val LEGACY_HEADER_AND_TAG = 16 + 12 + 16
    }
}
