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
import androidx.datastore.preferences.core.stringPreferencesKey
import com.vitorpamplona.amethyst.commons.util.platformFileSystem
import com.vitorpamplona.quartz.nip47WalletConnect.Nip47WalletConnect
import com.vitorpamplona.quartz.utils.cache.LargeCache
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.job
import okio.Path

/**
 * The per-account secret store: one encrypted DataStore per npub, at
 * `<root>/datastore/<npub>.secrets_pb`, for secrets that are not the identity
 * key — wallet connection strings, NIP-46 bunker material.
 *
 * **Private keys do not belong here.** They live in
 * [com.vitorpamplona.amethyst.commons.keystorage.SecureKeyStorage], which backs
 * them with the OS credential manager (Keychain, Credential Manager, Secret
 * Service) rather than a file this process can read, and which desktop already
 * uses. Two stores for one identity key would be one store too many, and the
 * weaker one would win by being convenient.
 *
 * Separate from [AccountPreferenceStores] so ordinary settings stay cheap to
 * read: every value here pays an encrypt/decrypt, which on a StrongBox-backed
 * device runs at roughly 68 KB/s.
 */
class AccountSecretsEncryptedStores(
    val rootFilesDir: () -> Path,
    val scope: CoroutineScope,
    private val encryption: SecretEncryption = SecretEncryption(),
) {
    companion object {
        val nwc = stringPreferencesKey("nwc")
    }

    /**
     * One store per account, each on its own child scope.
     *
     * The scope matters: DataStore keeps a process-wide registry keyed by file
     * path and only releases an entry when the owning scope is cancelled. On a
     * shared scope, deleting an account and re-adding it in the same session
     * would throw "multiple DataStores active for the same file".
     */
    private class Entry(
        val scope: CoroutineScope,
        val store: EncryptedDataStore,
    )

    private val storeCache = LargeCache<String, Entry>()

    /**
     * The `.preferences_pb` suffix is required, not decorative: DataStore's
     * Preferences factory rejects any other extension at open time. The
     * `.secrets` part is what keeps this file distinct from the account's
     * plain preference store.
     */
    fun file(npub: String): Path = rootFilesDir() / "datastore" / "$npub.secrets.preferences_pb"

    fun getDataStore(npub: String): EncryptedDataStore =
        storeCache
            .getOrCreate(npub) {
                val child = CoroutineScope(scope.coroutineContext + SupervisorJob(scope.coroutineContext[Job]))
                Entry(
                    child,
                    EncryptedDataStore(
                        PreferenceDataStoreFactory.createWithPath(scope = child, produceFile = { file(npub) }),
                        encryption,
                        scope = child,
                    ),
                )
            }.store

    fun nwc(npub: String): UpdatablePropertyFlow<Nip47WalletConnect.Nip47URI> =
        getDataStore(npub).getProperty(
            key = nwc,
            parser = Nip47WalletConnect.Nip47URI::parser,
            serializer = Nip47WalletConnect.Nip47URI::serializer,
        )

    /**
     * Drops the account's secrets.
     *
     * Cancels the store's scope and waits for it, so DataStore releases the
     * path and the same account can be added again in this session. The wait
     * is the point: `cancel()` only asks, and the path stays registered until
     * the job completes — [AccountPreferenceStores.removeAccount] has the
     * longer note.
     */
    suspend fun removeAccount(npub: String): Boolean {
        storeCache.get(npub)?.scope?.let {
            it.cancel()
            it.coroutineContext.job.join()
        }
        storeCache.remove(npub)
        val path = file(npub)
        if (!platformFileSystem.exists(path)) return false
        platformFileSystem.delete(path)
        return true
    }

    // ── the per-account secret group ──────────────────────────────────

    /**
     * Reads [AccountSecrets], or null when this account has not been migrated
     * out of the legacy encrypted file yet.
     *
     * Null and "all defaults" are deliberately different answers: the caller
     * uses null to decide whether to run the one-off copy, and an account that
     * genuinely holds no secrets must not trigger it forever.
     */
    suspend fun loadSecrets(npub: String): AccountSecrets? {
        // One snapshot for the whole group rather than ten flow collections.
        val stored = getDataStore(npub).snapshot()
        if (stored[AccountSecretKeys.migrated] == null) return null

        return AccountSecrets(
            nip46SignerEnabled = stored[AccountSecretKeys.nip46SignerEnabled].toBoolean(),
            nip46BunkerSecret = stored[AccountSecretKeys.nip46BunkerSecret] ?: "",
            nip46TransportKey = stored[AccountSecretKeys.nip46TransportKey] ?: "",
            nip46SeenRequestIds = decodeSet(stored[AccountSecretKeys.nip46SeenRequestIds]),
            nwcWalletsJson = stored[AccountSecretKeys.nwcWallets],
            clinkDebitWalletsJson = stored[AccountSecretKeys.clinkDebitWallets],
            defaultPaymentSourceId = stored[AccountSecretKeys.defaultPaymentSourceId],
            legacyDefaultNwcWalletId = stored[AccountSecretKeys.legacyDefaultNwcWalletId],
            legacyZapPaymentRequestServer = stored[AccountSecretKeys.legacyZapPaymentRequestServer],
            remoteSignerBunkerUri = stored[AccountSecretKeys.remoteSignerBunkerUri],
            remoteSignerTransportKey = stored[AccountSecretKeys.remoteSignerTransportKey],
        )
    }

    /**
     * Writes the group and its marker as one edit.
     *
     * One edit, not ten. Every account save runs this, and a key at a time cost
     * ten encrypted-file rewrites — none of which DataStore could skip, because
     * AES-GCM re-randomises the IV so the ciphertext differs even when the value
     * does not.
     *
     * It also makes the marker meaningful. Written in its own transaction after
     * the others it merely *tended* to be last; in the same one it cannot exist
     * without them, so a marker found on disk proves a complete group — which is
     * what `LegacyPreferenceCleanup` reads it as before deleting the legacy file.
     */
    suspend fun saveSecrets(
        npub: String,
        value: AccountSecrets,
    ) {
        getDataStore(npub).edit {
            put(AccountSecretKeys.nip46SignerEnabled, value.nip46SignerEnabled.toString())
            put(AccountSecretKeys.nip46BunkerSecret, value.nip46BunkerSecret)
            put(AccountSecretKeys.nip46TransportKey, value.nip46TransportKey)
            put(AccountSecretKeys.nip46SeenRequestIds, value.nip46SeenRequestIds.joinToString(AccountSecretKeys.SET_SEPARATOR))
            putOrRemove(AccountSecretKeys.nwcWallets, value.nwcWalletsJson)
            putOrRemove(AccountSecretKeys.clinkDebitWallets, value.clinkDebitWalletsJson)
            putOrRemove(AccountSecretKeys.defaultPaymentSourceId, value.defaultPaymentSourceId)
            putOrRemove(AccountSecretKeys.legacyDefaultNwcWalletId, value.legacyDefaultNwcWalletId)
            putOrRemove(AccountSecretKeys.legacyZapPaymentRequestServer, value.legacyZapPaymentRequestServer)
            putOrRemove(AccountSecretKeys.remoteSignerBunkerUri, value.remoteSignerBunkerUri)
            putOrRemove(AccountSecretKeys.remoteSignerTransportKey, value.remoteSignerTransportKey)

            put(AccountSecretKeys.migrated, "true")
        }
    }

    // ── the location-chat identity ────────────────────────────────────

    /**
     * Reads [GeohashIdentitySecrets], or null when this account has not been
     * copied out of the legacy encrypted file yet.
     *
     * Its own marker, not [AccountSecretKeys.migrated]: the two groups migrate
     * from *different files* (this one from `secret_keeper_<pubkey hex>`, the
     * secrets from `secret_keeper_<npub>`), so one marker cannot speak for both.
     */
    suspend fun loadGeohashIdentity(npub: String): GeohashIdentitySecrets? {
        val stored = getDataStore(npub).snapshot()
        if (stored[GeohashIdentityKeys.migrated] == null) return null

        return GeohashIdentitySecrets(
            deviceSeed = stored[GeohashIdentityKeys.deviceSeed],
            nickname = stored[GeohashIdentityKeys.nickname],
        )
    }

    /** Writes the group and its marker as one edit, for the reasons [saveSecrets] gives. */
    suspend fun saveGeohashIdentity(
        npub: String,
        value: GeohashIdentitySecrets,
    ) {
        getDataStore(npub).edit {
            putOrRemove(GeohashIdentityKeys.deviceSeed, value.deviceSeed)
            putOrRemove(GeohashIdentityKeys.nickname, value.nickname)

            put(GeohashIdentityKeys.migrated, "true")
        }
    }

    private fun decodeSet(raw: String?): Set<String> =
        raw
            ?.split(AccountSecretKeys.SET_SEPARATOR)
            ?.filter { it.isNotEmpty() }
            ?.toSet()
            ?: emptySet()
}
