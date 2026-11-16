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
package com.vitorpamplona.amethyst.commons.cordn

import com.vitorpamplona.amethyst.commons.keystorage.KeyStoreEncryption
import com.vitorpamplona.cordn.client.CordnBlobCipher

/**
 * The Android [CordnBlobCipher]: AES-GCM under a key held in the Android
 * KeyStore (StrongBox-backed where the device has it).
 *
 * ## Why there is no lock here any more
 *
 * There used to be one. [KeyStoreEncryption] kept a single `Cipher` in a field
 * and ran `init(...)` then `doFinal(...)` against it, which is not atomic, so
 * two groups saving at once on `Dispatchers.IO` could interleave and write
 * bytes encrypted under the wrong IV — unrecoverable for an `MlsGroupState`.
 *
 * [KeyStoreEncryption] now holds its `Cipher` in a `ThreadLocal`, so the pair
 * can no longer interleave and the lock guards nothing. Keeping it would
 * serialise every cordn group save and load on this device for no reason,
 * which is the opposite of what a thread pool is for.
 */
class KeyStoreCordnBlobCipher : CordnBlobCipher {
    // Not a constructor parameter: KeyStoreEncryption is internal to commons,
    // so taking one publicly would leak an internal type. Nothing needs to
    // inject it either — a test that wanted a different cipher implements
    // [CordnBlobCipher] directly, which is what the seam is for.
    // Built on first use, not at construction. This cipher is reached from
    // CordnRuntime, which Account builds eagerly, so a KeyStoreEncryption that
    // throws here takes the whole account down before any UI exists — the app
    // sits on "Loading account" forever. Every other user of KeyStoreEncryption
    // already guards it (AccountCacheState falls back to an in-memory Marmot
    // store); deferring keeps a cordn-only failure inside cordn.
    private val encryption by lazy { KeyStoreEncryption() }

    override fun encrypt(bytes: ByteArray): ByteArray = encryption.encrypt(bytes)

    override fun decrypt(bytes: ByteArray): ByteArray = encryption.decrypt(bytes)
}
