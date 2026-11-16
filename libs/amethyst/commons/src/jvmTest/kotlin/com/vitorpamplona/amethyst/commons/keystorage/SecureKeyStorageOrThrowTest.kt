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
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 * Unit tests for the strict `getPrivateKeyOrThrow` lookup and its pure macOS
 * `/usr/bin/security` exit-code parser.
 *
 * These tests must never touch the OS keychain and must run on any host, so:
 *  - the macOS integration paths are gated behind [MacSecurityResult] stubs
 *    injected via `SecureKeyStorage.macSecurityLookup`;
 *  - the parser test operates on captured stdout/stderr/exitCode triples;
 *  - non-mac backends are exercised via the `KeyringHandle` test seam already
 *    used by [SecureKeyStorageKeyringCacheTest].
 */
class SecureKeyStorageOrThrowTest {
    private class ExplodingKeyring(
        private val onGet: () -> Nothing,
    ) : KeyringHandle {
        override fun getPassword(
            service: String,
            account: String,
        ): String = onGet()

        override fun setPassword(
            service: String,
            account: String,
            password: String,
        ) {
            // unused in these tests
        }

        override fun deletePassword(
            service: String,
            account: String,
        ) {
            // unused in these tests
        }
    }

    private class StaticKeyring(
        private val map: Map<Pair<String, String>, String>,
    ) : KeyringHandle {
        override fun getPassword(
            service: String,
            account: String,
        ): String = map[service to account] ?: throw PasswordAccessException("No stored credentials match $service account: $account")

        override fun setPassword(
            service: String,
            account: String,
            password: String,
        ) {}

        override fun deletePassword(
            service: String,
            account: String,
        ) {}
    }

    // --- macOS security(1) parser ---

    @Test
    fun `parser exit 0 returns Found with trimmed password`() {
        val result = parseMacSecurityFindResult(0, "hunter2\n", "")
        assertTrue(result is MacSecurityResult.Found)
        assertEquals("hunter2", (result as MacSecurityResult.Found).password)
    }

    @Test
    fun `parser exit 0 preserves internal newlines and only strips trailing`() {
        val result = parseMacSecurityFindResult(0, "line1\nline2\n", "")
        assertEquals("line1\nline2", (result as MacSecurityResult.Found).password)
    }

    @Test
    fun `parser exit 44 returns NotFound`() {
        val result =
            parseMacSecurityFindResult(
                44,
                "",
                "security: SecKeychainSearchCopyNext: The specified item could not be found in the keychain.\n",
            )
        assertTrue(result is MacSecurityResult.NotFound)
    }

    @Test
    fun `parser exit 128 flagged as user-cancelled`() {
        val result = parseMacSecurityFindResult(128, "", "security: dismissed\n")
        assertTrue(result is MacSecurityResult.Ambiguous)
        val ambig = result as MacSecurityResult.Ambiguous
        assertEquals(128, ambig.exitCode)
        assertTrue(ambig.reason.contains("cancel"))
    }

    @Test
    fun `parser stderr -25293 mapped to errSecAuthFailed`() {
        val result = parseMacSecurityFindResult(51, "", "security: SecKeychainItemCopyContent (-25293)\n")
        val ambig = result as MacSecurityResult.Ambiguous
        assertEquals("errSecAuthFailed", ambig.reason)
    }

    @Test
    fun `parser unknown exit falls back to first stderr line`() {
        val result = parseMacSecurityFindResult(9999, "", "security: mystery: line 1\nline 2\n")
        val ambig = result as MacSecurityResult.Ambiguous
        assertEquals("security: mystery: line 1", ambig.reason)
    }

    // --- getPrivateKeyOrThrow: strict semantics via injected macOS lookup ---
    // (Enabled unconditionally: the macSecurityLookup indirection is exercised
    // via a stub, so no `security` binary is invoked. The `isMacOs()` check
    // means this test only takes the mac path on macOS runners; on Linux it
    // takes the javakeyring path, which we validate separately below.)

    private fun newStorage(): SecureKeyStorage = SecureKeyStorage.create()

    @Test
    fun `mac lookup Found returns password without ambiguity`() =
        runBlocking {
            if (!System.getProperty("os.name").orEmpty().startsWith("Mac")) return@runBlocking
            val storage = newStorage()
            storage.macSecurityLookup = { _, _ -> MacSecurityResult.Found("secretval") }
            assertEquals("secretval", storage.getPrivateKeyOrThrow("account-metadata-key"))
        }

    @Test
    fun `mac lookup NotFound returns null`() =
        runBlocking {
            if (!System.getProperty("os.name").orEmpty().startsWith("Mac")) return@runBlocking
            val storage = newStorage()
            storage.macSecurityLookup = { _, _ -> MacSecurityResult.NotFound }
            assertNull(storage.getPrivateKeyOrThrow("account-metadata-key"))
        }

    @Test
    fun `mac lookup Ambiguous throws SecureStorageException with reason`() =
        runBlocking {
            if (!System.getProperty("os.name").orEmpty().startsWith("Mac")) return@runBlocking
            val storage = newStorage()
            storage.macSecurityLookup = { _, _ ->
                MacSecurityResult.Ambiguous(128, "user cancelled Keychain dialog")
            }
            try {
                storage.getPrivateKeyOrThrow("account-metadata-key")
                fail("Expected SecureStorageException")
            } catch (e: SecureStorageException) {
                assertTrue(e.message?.contains("user cancelled") == true)
                assertTrue(e.message?.contains("128") == true)
            }
        }

    // --- non-mac backend: PasswordAccessException must throw, never null ---

    @Test
    fun `non-mac keyring PasswordAccessException throws not returns null`() =
        runBlocking {
            if (System.getProperty("os.name").orEmpty().startsWith("Mac")) return@runBlocking
            val storage = newStorage()
            storage.keyringFactory = { ExplodingKeyring { throw PasswordAccessException("locked") } }
            try {
                storage.getPrivateKeyOrThrow("account-metadata-key")
                fail("Expected SecureStorageException")
            } catch (e: SecureStorageException) {
                assertTrue(
                    e.message?.contains("ambiguous", ignoreCase = true) == true ||
                        e.message?.contains("refused", ignoreCase = true) == true,
                )
            }
        }

    @Test
    fun `non-mac keyring miss returns null`() =
        runBlocking {
            if (System.getProperty("os.name").orEmpty().startsWith("Mac")) return@runBlocking
            val storage = newStorage()
            // Windows and the Secret Service report a missing item as an exception; it is still absence.
            storage.keyringFactory = { StaticKeyring(emptyMap()) }
            assertNull(storage.getPrivateKeyOrThrow("npub1missing"))
        }

    @Test
    fun `non-mac keyring hit returns password`() =
        runBlocking {
            if (System.getProperty("os.name").orEmpty().startsWith("Mac")) return@runBlocking
            val storage = newStorage()
            storage.keyringFactory = {
                StaticKeyring(
                    mapOf(
                        ("amethyst-desktop" to "account-metadata-key") to "abc123",
                    ),
                )
            }
            assertEquals("abc123", storage.getPrivateKeyOrThrow("account-metadata-key"))
        }
}
