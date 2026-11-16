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

import com.github.javakeyring.PasswordAccessException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/**
 * Regression tests for the desktop `SecureKeyStorage` keyring-instance cache.
 *
 * Prior to the fix, every `savePrivateKey` / `getPrivateKey` / `deletePrivateKey`
 * call opened a fresh `Keyring.create()` handle. On macOS, each fresh handle
 * incurs a Security Framework session open; on GNOME/KWallet a fresh handle can
 * re-trigger the OS unlock prompt. The Amethyst cold-boot path calls the
 * storage at least twice (metadata AES key, then the active account nsec), so
 * the user was seeing the keychain unlock prompt twice on startup.
 *
 * These tests pin the invariant that at most one `Keyring` is opened for the
 * process lifetime of a `SecureKeyStorage` instance, no matter how many
 * save/get/delete calls happen.
 */
class SecureKeyStorageKeyringCacheTest {
    private class InMemoryKeyring : KeyringHandle {
        private val store: ConcurrentHashMap<Pair<String, String>, String> = ConcurrentHashMap()

        override fun getPassword(
            service: String,
            account: String,
        ): String = store[service to account] ?: throw PasswordAccessException("No stored credentials match $service account: $account")

        override fun setPassword(
            service: String,
            account: String,
            password: String,
        ) {
            store[service to account] = password
        }

        override fun deletePassword(
            service: String,
            account: String,
        ) {
            if (store.remove(service to account) == null) {
                throw PasswordAccessException("No stored credentials match $service account: $account")
            }
        }
    }

    private fun newStorage(counter: AtomicInteger): SecureKeyStorage {
        val storage = SecureKeyStorage.create()
        storage.keyringFactory = {
            counter.incrementAndGet()
            InMemoryKeyring()
        }
        return storage
    }

    @Test
    fun `single instance across many save-get-delete calls (double-prompt regression)`() =
        runBlocking {
            val opens = AtomicInteger(0)
            val storage = newStorage(opens)

            // Simulate the cold-boot storm: metadata key + active-account nsec
            // + a handful of subsequent NWC / bunker ephemeral key touches.
            storage.savePrivateKey("account-metadata-key", "aaaa")
            assertEquals("aaaa", storage.getPrivateKey("account-metadata-key"))
            storage.savePrivateKey("npub1alice", "bbbb")
            assertEquals("bbbb", storage.getPrivateKey("npub1alice"))
            storage.savePrivateKey("bunker-ephemeral-npub1alice", "cccc")
            assertEquals("cccc", storage.getPrivateKey("bunker-ephemeral-npub1alice"))
            assertEquals(true, storage.deletePrivateKey("bunker-ephemeral-npub1alice"))
            assertNull(storage.getPrivateKey("bunker-ephemeral-npub1alice"))

            // The cache must open the keyring exactly once for the process
            // lifetime; each additional Keyring.create() call would surface as
            // an OS-level unlock prompt on macOS / GNOME / KWallet.
            assertEquals(
                "SecureKeyStorage must open Keyring exactly once per process",
                1,
                opens.get(),
            )
        }

    @Test
    fun `hasPrivateKey reuses the cached keyring`() =
        runBlocking {
            val opens = AtomicInteger(0)
            val storage = newStorage(opens)

            storage.savePrivateKey("npub1x", "1111")
            repeat(5) {
                assertEquals(true, storage.hasPrivateKey("npub1x"))
                assertEquals(false, storage.hasPrivateKey("npub1missing"))
            }
            assertEquals(1, opens.get())
        }

    @Test
    fun `concurrent first-touches still open the keyring exactly once`() =
        runBlocking {
            val opens = AtomicInteger(0)
            val storage = newStorage(opens)

            val threads =
                List(16) { idx ->
                    Thread {
                        runBlocking {
                            // Alternate between get and save so both call paths race to
                            // grab the cached keyring on the first invocation.
                            if (idx % 2 == 0) {
                                storage.getPrivateKey("npub1concurrent-$idx")
                            } else {
                                storage.savePrivateKey("npub1concurrent-$idx", "0x$idx")
                            }
                        }
                    }
                }
            threads.forEach { it.start() }
            threads.forEach { it.join() }

            // Race-free single-open guarantee under contention.
            assertEquals(
                "Concurrent first-touches must not open the Keyring more than once",
                1,
                opens.get(),
            )
        }
}
