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
package com.vitorpamplona.amethyst.commons.model.preferences

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.job
import kotlinx.coroutines.test.runTest
import okio.Path.Companion.toOkioPath
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * Round-trip coverage for the encrypted store.
 *
 * The store shipped with `decrypt` reading
 * `encryption.decrypt(...).contentToString()`, which renders a ByteArray as
 * `"[104, 101, 108]"` instead of decoding it, so every value read back was the
 * debug rendering of its own bytes. Nothing caught it because the class had no
 * callers. [saveAndGetRoundTrips] is the test that fails against that version.
 */
class EncryptedDataStoreTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val key = stringPreferencesKey("nwc")

    private var seq = 0

    /**
     * A store over its own pair of fresh paths.
     *
     * The files are named, never created: DataStore writes them itself, and an
     * empty file left behind by `newFile()` is not a valid preferences_pb. The
     * path is resolved once and captured, because `produceFile` may be invoked
     * more than once and must answer the same file every time.
     */
    private fun store(scope: CoroutineScope): EncryptedDataStore {
        val n = seq++
        val dataFile = File(folder.root, "secrets_$n.preferences_pb")
        val keyFile = File(folder.root, "secret_$n.key")
        return EncryptedDataStore(
            PreferenceDataStoreFactory.createWithPath(scope = scope, produceFile = { dataFile.toOkioPath() }),
            SecretEncryption(keyFile),
            scope = scope,
        )
    }

    @Test
    fun saveAndGetRoundTrips() =
        runTest {
            val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
            val subject = store(scope)
            val nsec = "e5e2b1d3f6a94c8d7b0e1f2a3c4d5e6f7a8b9c0d1e2f3a4b5c6d7e8f9a0b1c2d"

            subject.save(key, nsec)

            assertEquals(nsec, subject.get(key))
        }

    @Test
    fun missingKeyReadsBackAsNull() =
        runTest {
            val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
            assertNull(store(scope).get(key))
        }

    @Test
    fun valuesAreNotStoredInPlaintext() =
        runTest {
            val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
            val file = File(folder.root, "plaintext_check.preferences_pb")
            val subject =
                EncryptedDataStore(
                    PreferenceDataStoreFactory.createWithPath(scope = scope, produceFile = { file.toOkioPath() }),
                    SecretEncryption(File(folder.root, "plaintext_check.key")),
                    scope = scope,
                )
            val secret = "correct-horse-battery-staple"

            subject.save(key, secret)

            val onDisk = file.readBytes().decodeToString()
            assertEquals("the secret must not be readable in the store file", false, onDisk.contains(secret))
        }

    @Test
    fun overwriteReplacesTheValue() =
        runTest {
            val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
            val subject = store(scope)

            subject.save(key, "first")
            subject.save(key, "second")

            assertEquals("second", subject.get(key))
        }

    @Test
    fun removeClearsTheValue() =
        runTest {
            val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
            val subject = store(scope)

            subject.save(key, "value")
            subject.remove(key)

            assertNull(subject.get(key))
        }

    /**
     * [EncryptedDataStore.get] flattens a read failure into null;
     * [EncryptedDataStore.getOrThrow] does not.
     *
     * The difference guards a live key: a probe that decides whether to create
     * one must not read "absent" from a store it merely failed to open, or it
     * overwrites what is already there.
     */
    @Test
    fun getSwallowsAReadFailureButGetOrThrowDoesNot() =
        runTest {
            val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
            val n = seq++
            val dataFile = File(folder.root, "corrupt_$n.preferences_pb")
            val keyFile = File(folder.root, "corrupt_$n.key")
            val subject =
                EncryptedDataStore(
                    PreferenceDataStoreFactory.createWithPath(scope = scope, produceFile = { dataFile.toOkioPath() }),
                    SecretEncryption(keyFile),
                    scope = scope,
                )
            subject.save(key, "a real value")
            scope.cancel()
            scope.coroutineContext.job.join()

            // Truncate the store so opening it fails rather than reading empty.
            dataFile.writeBytes(byteArrayOf(0x01, 0x02, 0x03))

            val readScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
            val reopened =
                EncryptedDataStore(
                    PreferenceDataStoreFactory.createWithPath(scope = readScope, produceFile = { dataFile.toOkioPath() }),
                    SecretEncryption(keyFile),
                    scope = readScope,
                )

            assertNull("get() reports the unreadable store as absent", reopened.get(key))
            assertTrue(
                "getOrThrow() must not call it absent",
                runCatching { reopened.getOrThrow(key) }.isFailure,
            )
            readScope.cancel()
        }

    /**
     * A stock DataMigration writes values as-is, but this store decrypts on
     * read — so plaintext put there by one cannot survive the trip, and the
     * read raises rather than returning something wrong.
     *
     * This is why secrets migrate lazily, through [save], instead of through a
     * DataMigration the way the plain preference stores do. Getting it wrong
     * would leave an account's wallet strings unreadable rather than obviously
     * missing.
     */
    @Test
    fun aRawMigrationIntoAnEncryptedStoreIsNotReadable() =
        runTest {
            val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
            val n = seq++
            val dataFile = File(folder.root, "rawmig_$n.preferences_pb")
            val keyFile = File(folder.root, "rawmig_$n.key")
            val subject =
                EncryptedDataStore(
                    PreferenceDataStoreFactory.createWithPath(
                        scope = scope,
                        migrations = listOf(CopyOnceMigration("probe") { out -> out[key] = "plaintext-secret" }),
                        produceFile = { dataFile.toOkioPath() },
                    ),
                    SecretEncryption(keyFile),
                    scope = scope,
                )

            assertTrue(
                "a raw-migrated value must not read back as if it were valid",
                runCatching { subject.get(key) }.isFailure,
            )
            scope.cancel()
        }

    @Test
    fun editWritesEveryKeyInOneGo() =
        runTest {
            val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
            val subject = store(scope)
            val other = stringPreferencesKey("bunker")

            subject.edit {
                put(key, "wallet")
                put(other, "secret")
            }

            val snapshot = subject.snapshot()
            assertEquals("wallet", snapshot[key])
            assertEquals("secret", snapshot[other])
        }

    /**
     * The whole point of writing a group in one edit: a marker written beside
     * its values cannot be found on disk without them.
     */
    @Test
    fun editIsOneTransaction() =
        runTest {
            val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
            val subject = store(scope)
            val marker = stringPreferencesKey("migrated")

            runCatching {
                subject.edit {
                    put(key, "wallet")
                    put(marker, "true")
                    throw IllegalStateException("crash midway")
                }
            }

            val snapshot = subject.snapshot()
            assertNull(snapshot[marker])
            assertNull(snapshot[key])
        }

    @Test
    fun putOrRemoveClearsANullValue() =
        runTest {
            val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
            val subject = store(scope)

            subject.edit { put(key, "wallet") }
            subject.edit { putOrRemove(key, null) }

            assertNull(subject.snapshot()[key])
        }

    /**
     * `contains` must not decrypt.
     *
     * A value the current key cannot decrypt — a rotated or wiped keystore — is
     * still a value that is there, and deleting it has to happen anyway.
     * `deletePrivateKey` gated its removal on a decrypting read and so skipped
     * exactly the case that needed it, leaving a deleted account's private key
     * on disk.
     */
    @Test
    fun containsSeesAValueThatCannotBeDecrypted() =
        runTest {
            val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
            val n = seq++
            val dataFile = File(folder.root, "secrets_$n.preferences_pb")
            val raw =
                PreferenceDataStoreFactory.createWithPath(scope = scope, produceFile = { dataFile.toOkioPath() })
            // Ciphertext this store's key was never used to produce.
            raw.edit { prefs -> prefs[key] = "bm90LWFjdHVhbGx5LWNpcGhlcnRleHQ=" }

            val subject =
                EncryptedDataStore(raw, SecretEncryption(File(folder.root, "secret_$n.key")), scope = scope)

            assertTrue(subject.contains(key))
            assertNull(runCatching { subject.get(key) }.getOrNull())

            subject.remove(key)
            assertTrue(!subject.contains(key))
        }
}
