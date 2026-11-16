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
package com.vitorpamplona.amethyst.commons.model

import com.vitorpamplona.amethyst.commons.model.preferences.GeohashIdentitySecrets
import kotlin.concurrent.Volatile

/**
 * Where one account's geohash-chat identity secrets (the device seed and the location-chat
 * nickname) are kept. [GeohashChatIdentityState] does the locking and derivation; this only moves
 * bytes. Reads and writes touch disk, so callers are off the main thread.
 */
interface GeohashIdentityStore {
    /** The stored secrets. The first read may copy them in from an older location. */
    suspend fun read(): GeohashIdentitySecrets

    /** Replaces the stored secrets with [value]. */
    suspend fun write(value: GeohashIdentitySecrets)

    /** Also records a new nickname wherever older app versions look for it, if the platform still does. */
    fun mirrorLegacyNickname(nickname: String) = Unit

    /** Also records a new device seed wherever older app versions look for it, if the platform still does. */
    fun mirrorLegacyDeviceSeed(seedHex: String) = Unit
}

/** Keeps the secrets in memory only (previews, tests). */
class InMemoryGeohashIdentityStore : GeohashIdentityStore {
    @Volatile private var value = GeohashIdentitySecrets()

    override suspend fun read() = value

    override suspend fun write(value: GeohashIdentitySecrets) {
        this.value = value
    }
}
