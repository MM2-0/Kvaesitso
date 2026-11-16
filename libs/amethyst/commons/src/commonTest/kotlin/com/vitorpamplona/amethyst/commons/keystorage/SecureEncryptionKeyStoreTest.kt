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

import com.vitorpamplona.quartz.experimental.decoupling.store.generate
import com.vitorpamplona.quartz.nip01Core.core.toHexKey
import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SecureEncryptionKeyStoreTest {
    /** Stands in for the platform keychain, recording what the store writes. */
    private class MemorySlots : SecureEncryptionKeyStore.SecretSlots {
        val values = HashMap<String, String>()

        override suspend fun save(
            alias: String,
            secretHex: String,
        ) {
            values[alias] = secretHex
        }

        override suspend fun load(alias: String) = values[alias]

        override suspend fun delete(alias: String) = values.remove(alias) != null
    }

    private val owner = KeyPair().pubKey.toHexKey()

    @Test
    fun keysRoundTripUnderANamespacedAlias() =
        runTest {
            val slots = MemorySlots()
            val store = SecureEncryptionKeyStore(slots)
            val key = KeyPair()

            val pub = store.put(owner, key.privKey!!)

            assertEquals(key.pubKey.toHexKey(), pub)
            assertEquals(setOf("nip4e:$owner:$pub"), slots.values.keys, "never an npub alias, so no clash with account keys")
            assertContentEquals(key.privKey, store.get(owner, pub))
            assertTrue(store.delete(owner, pub))
            assertNull(store.get(owner, pub))
            assertFalse(store.delete(owner, pub))
        }

    @Test
    fun aValueThatIsNotTheAliasKeyReadsAsAbsent() =
        runTest {
            val slots = MemorySlots()
            val store = SecureEncryptionKeyStore(slots)
            val pub = assertNotNull(store.generate(owner))

            slots.values[SecureEncryptionKeyStore.alias(owner, pub)] = KeyPair().privKey!!.toHexKey()
            assertNull(store.get(owner, pub), "a secret for some other key must not be used as this one")

            slots.values[SecureEncryptionKeyStore.alias(owner, pub)] = "not hex"
            assertNull(store.get(owner, pub))

            slots.values[SecureEncryptionKeyStore.alias(owner, pub)] = "ab"
            assertNull(store.get(owner, pub))
        }
}
