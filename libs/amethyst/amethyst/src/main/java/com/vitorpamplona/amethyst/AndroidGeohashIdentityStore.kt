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
package com.vitorpamplona.amethyst

import androidx.core.content.edit
import com.vitorpamplona.amethyst.commons.model.GeohashIdentityStore
import com.vitorpamplona.amethyst.commons.model.preferences.GeohashIdentitySecrets
import com.vitorpamplona.amethyst.commons.model.preferences.readLegacyGeohashIdentity
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.hexToByteArray
import com.vitorpamplona.quartz.nip19Bech32.toNpub

/**
 * The account's geohash identity in [accountSecretsStore], copied on first read out of the
 * encrypted SharedPreferences file older versions wrote, and mirrored back there until
 * [LocalPreferences.LEGACY_WRITES_RETIRED] (so a rollback keeps the seed and the nickname).
 */
class AndroidGeohashIdentityStore(
    private val pubKeyHex: HexKey,
) : GeohashIdentityStore {
    /**
     * The npub the current store is keyed by.
     *
     * The legacy file is keyed by the pubkey *hex* — the old code passed
     * `signer.pubKey` where every other caller passes an npub, so the identity
     * lived in `secret_keeper_<hex>`, a different file from the account's own
     * `secret_keeper_<npub>`. The copy below reads that file and writes the
     * npub-keyed store, which is what folds this orphan back in with the rest.
     */
    private val npub by lazy { pubKeyHex.hexToByteArray().toNpub() }

    /**
     * What `secret_keeper_<pubkey hex>` holds.
     *
     * Only called when the store has nothing yet: opening this file creates it,
     * so reading it unconditionally would resurrect it after the cleanup has
     * deleted it. Touches disk; callers are off the main thread.
     */
    private fun legacy(): GeohashIdentitySecrets = readLegacyGeohashIdentity(LegacySharedPreferences(Amethyst.instance.encryptedStorage(pubKeyHex)))

    override suspend fun read(): GeohashIdentitySecrets = accountSecretsStore.readGeohashIdentity(npub) { legacy() }

    override suspend fun write(value: GeohashIdentitySecrets) = accountSecretsStore.mirrorGeohashIdentity(npub, value)

    // Mirrored, not moved: the legacy file stays readable until the legacy writes are retired
    // app-wide. Gated on the same switch as every other mirror.
    override fun mirrorLegacyNickname(nickname: String) {
        if (!LocalPreferences.LEGACY_WRITES_RETIRED) {
            Amethyst.instance.encryptedStorage(pubKeyHex).edit { putString(PREF_NICKNAME, nickname) }
        }
    }

    override fun mirrorLegacyDeviceSeed(seedHex: String) {
        if (!LocalPreferences.LEGACY_WRITES_RETIRED) {
            Amethyst.instance.encryptedStorage(pubKeyHex).edit { putString(PREF_KEY, seedHex) }
        }
    }

    companion object {
        private const val PREF_KEY = "geohash_chat_device_seed"
        private const val PREF_NICKNAME = "geohash_chat_nickname"
    }
}
