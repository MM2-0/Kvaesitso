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

import androidx.datastore.preferences.core.stringPreferencesKey

/**
 * The per-account values that used to live in the encrypted
 * `secret_keeper_<npub>` file, other than the private key itself.
 *
 * Everything here stays encrypted, including the parts that are not obviously
 * secret. `defaultPaymentSourceId` is only an identifier and
 * `nip46SeenRequestIds` only request ids, but both were encrypted before and
 * both say something about what the account does — moving them to a plain
 * store would be a quiet downgrade, so they keep the protection they had.
 *
 * The two `legacy` fields are read-only leftovers the app still migrates from;
 * they are carried across so an upgrade does not strand a wallet that only
 * exists in the old shape.
 */
data class AccountSecrets(
    val nip46SignerEnabled: Boolean = false,
    val nip46BunkerSecret: String = "",
    val nip46TransportKey: String = "",
    val nip46SeenRequestIds: Set<String> = emptySet(),
    val nwcWalletsJson: String? = null,
    val clinkDebitWalletsJson: String? = null,
    val defaultPaymentSourceId: String? = null,
    val legacyDefaultNwcWalletId: String? = null,
    val legacyZapPaymentRequestServer: String? = null,
    /** NIP-46 login: the `bunker://` address of the signer this account signs through. */
    val remoteSignerBunkerUri: String? = null,
    /** NIP-46 login: this client's transport private key (hex) for that signer. */
    val remoteSignerTransportKey: String? = null,
)

/**
 * Keys for [AccountSecrets] inside an [EncryptedDataStore].
 *
 * All of them are string keys: the store encrypts strings, so a boolean and a
 * set are encoded here rather than stored in typed keys that would sit in
 * cleartext beside the encrypted values.
 */
internal object AccountSecretKeys {
    val nip46SignerEnabled = stringPreferencesKey("nip46SignerEnabled")
    val nip46BunkerSecret = stringPreferencesKey("nip46BunkerSecret")
    val nip46TransportKey = stringPreferencesKey("nip46TransportKey")
    val nip46SeenRequestIds = stringPreferencesKey("nip46SeenRequestIds")
    val nwcWallets = stringPreferencesKey("nwcWallets")
    val clinkDebitWallets = stringPreferencesKey("clinkDebitWallets")
    val defaultPaymentSourceId = stringPreferencesKey("defaultPaymentSourceId")
    val legacyDefaultNwcWalletId = stringPreferencesKey("defaultNwcWalletId")
    val legacyZapPaymentRequestServer = stringPreferencesKey("zapPaymentServer")
    val remoteSignerBunkerUri = stringPreferencesKey("remoteSignerBunkerUri")
    val remoteSignerTransportKey = stringPreferencesKey("remoteSignerTransportKey")

    /** Records that the one-off copy out of the legacy file has run for this account. */
    val migrated = stringPreferencesKey("migrated.accountSecrets")

    /**
     * Sets are stored newline-joined rather than as JSON.
     *
     * The members are nostr event ids — hex, so they cannot contain a newline —
     * which makes the round trip exact without pulling a serializer into the
     * encryption path.
     */
    const val SET_SEPARATOR = "\n"
}

/**
 * The names [AccountSecrets] had in the `secret_keeper_<npub>` file, and how to
 * read a set of them back out.
 *
 * Unlike the plain-store groups, these are still written to both stores on
 * every save, so this is not only a migration source: it is what lets a check
 * read the legacy file and the current one and assert they agree before the
 * legacy file is deleted.
 */
object LegacyAccountSecretNames {
    const val NIP46_SIGNER_ENABLED = "nip46SignerEnabled"
    const val NIP46_BUNKER_SECRET = "nip46BunkerSecret"
    const val NIP46_TRANSPORT_KEY = "nip46TransportKey"
    const val NIP46_SEEN_IDS = "nip46SeenRequestIds"
    const val NWC_WALLETS = "nwcWallets"
    const val CLINK_DEBIT_WALLETS = "clinkDebitWallets"
    const val DEFAULT_PAYMENT_SOURCE_ID = "defaultPaymentSourceId"
    const val DEFAULT_NWC_WALLET_ID = "defaultNwcWalletId"
    const val ZAP_PAYMENT_REQUEST_SERVER = "zapPaymentServer"

    /** The private key, which lives in its own store rather than in [AccountSecrets]. */
    const val NOSTR_PRIVKEY = "nostr_privkey"

    /**
     * The location-chat identity, which is [GeohashIdentitySecrets] rather than
     * part of [AccountSecrets] — see that class for why it is its own group.
     *
     * These two sit in `secret_keeper_<pubkey hex>`, not `secret_keeper_<npub>`:
     * the writer passed `signer.pubKey`, which is hex, where every other caller
     * passes an npub. They are therefore in a *different file* from everything
     * else named here, which is why they are deliberately **not** in [all]:
     * [all] is what `LegacyPreferenceCleanup` treats as claimed in the npub
     * file, and these never appear in it. That file's deletion cannot lose
     * them, and cannot clean them up either — retiring the hex file is its own
     * job, once these writes stop.
     */
    const val GEOHASH_DEVICE_SEED = "geohash_chat_device_seed"
    const val GEOHASH_NICKNAME = "geohash_chat_nickname"

    val all =
        setOf(
            NIP46_SIGNER_ENABLED,
            NIP46_BUNKER_SECRET,
            NIP46_TRANSPORT_KEY,
            NIP46_SEEN_IDS,
            NWC_WALLETS,
            CLINK_DEBIT_WALLETS,
            DEFAULT_PAYMENT_SOURCE_ID,
            DEFAULT_NWC_WALLET_ID,
            ZAP_PAYMENT_REQUEST_SERVER,
            NOSTR_PRIVKEY,
        )
}

/**
 * The secrets as the legacy file holds them.
 *
 * Absent keys become the same defaults the loader has always applied, so this
 * is directly comparable with what the current store returns.
 */
fun readLegacyAccountSecrets(source: LegacyPreferenceSource) =
    AccountSecrets(
        nip46SignerEnabled = source.getBoolean(LegacyAccountSecretNames.NIP46_SIGNER_ENABLED) ?: false,
        nip46BunkerSecret = source.getString(LegacyAccountSecretNames.NIP46_BUNKER_SECRET) ?: "",
        nip46TransportKey = source.getString(LegacyAccountSecretNames.NIP46_TRANSPORT_KEY) ?: "",
        nip46SeenRequestIds = source.getStringSet(LegacyAccountSecretNames.NIP46_SEEN_IDS) ?: emptySet(),
        nwcWalletsJson = source.getString(LegacyAccountSecretNames.NWC_WALLETS),
        clinkDebitWalletsJson = source.getString(LegacyAccountSecretNames.CLINK_DEBIT_WALLETS),
        defaultPaymentSourceId = source.getString(LegacyAccountSecretNames.DEFAULT_PAYMENT_SOURCE_ID),
        legacyDefaultNwcWalletId = source.getString(LegacyAccountSecretNames.DEFAULT_NWC_WALLET_ID),
        legacyZapPaymentRequestServer = source.getString(LegacyAccountSecretNames.ZAP_PAYMENT_REQUEST_SERVER),
    )

/** Keys for [GeohashIdentitySecrets] inside an [EncryptedDataStore]. */
internal object GeohashIdentityKeys {
    val deviceSeed = stringPreferencesKey(LegacyAccountSecretNames.GEOHASH_DEVICE_SEED)
    val nickname = stringPreferencesKey(LegacyAccountSecretNames.GEOHASH_NICKNAME)

    /** Records that the one-off copy out of the legacy file has run for this account. */
    val migrated = stringPreferencesKey("migrated.geohashIdentity")
}

/**
 * The location-chat identity as the legacy file holds it.
 *
 * Both absent is a real answer — an account that never opened a location chat —
 * and is why the caller compares against [GeohashIdentitySecrets] rather than
 * treating null as "not migrated".
 */
fun readLegacyGeohashIdentity(source: LegacyPreferenceSource) =
    GeohashIdentitySecrets(
        deviceSeed = source.getString(LegacyAccountSecretNames.GEOHASH_DEVICE_SEED),
        nickname = source.getString(LegacyAccountSecretNames.GEOHASH_NICKNAME),
    )
