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
package com.vitorpamplona.amethyst.commons.account

import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.vitorpamplona.amethyst.commons.cashu.CashuKeysetCounterStore
import com.vitorpamplona.amethyst.commons.cashu.UnavailableCashuKeysetCounterStore
import com.vitorpamplona.amethyst.commons.keystorage.KeyFileLockedException
import com.vitorpamplona.amethyst.commons.keystorage.KeyNotKeptException
import com.vitorpamplona.amethyst.commons.keystorage.KeyStoragePrompts
import com.vitorpamplona.amethyst.commons.keystorage.PrivateKeyVault
import com.vitorpamplona.amethyst.commons.model.AccountSettings
import com.vitorpamplona.amethyst.commons.model.GeohashIdentityStore
import com.vitorpamplona.amethyst.commons.model.preferences.AccountPreferenceStores
import com.vitorpamplona.amethyst.commons.model.preferences.AccountRosterStore
import com.vitorpamplona.amethyst.commons.model.preferences.AccountSecrets
import com.vitorpamplona.amethyst.commons.model.preferences.AccountSecretsEncryptedStores
import com.vitorpamplona.amethyst.commons.model.preferences.AccountSettingsSource
import com.vitorpamplona.amethyst.commons.model.preferences.AccountSettingsStores
import com.vitorpamplona.amethyst.commons.model.preferences.AppPreferenceStores
import com.vitorpamplona.amethyst.commons.model.preferences.GeohashIdentitySecrets
import com.vitorpamplona.amethyst.commons.model.preferences.SecretEncryption
import com.vitorpamplona.amethyst.commons.model.preferences.toAccountSecrets
import com.vitorpamplona.amethyst.commons.model.preferences.toAccountSettings
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.JsonMapper
import com.vitorpamplona.quartz.nip01Core.core.hexToByteArray
import com.vitorpamplona.quartz.nip01Core.core.toHexKey
import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import com.vitorpamplona.quartz.nip19Bech32.decodePrivateKeyAsHexOrNull
import com.vitorpamplona.quartz.nip19Bech32.toNpub
import com.vitorpamplona.quartz.utils.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okio.Path
import java.util.concurrent.ConcurrentHashMap

/**
 * The saved logins of a front end with no legacy storage to migrate from: the encrypted roster, each
 * account's DataStore records, its encrypted secrets and its private key in [keyVault]. These are the
 * stores Android writes too; Android adds its legacy fallbacks on top of them.
 */
class StoreAccountSessionStore(
    rootFilesDir: () -> Path,
    appStores: AppPreferenceStores,
    private val keyVault: PrivateKeyVault,
    scope: CoroutineScope,
    private val cashuCounters: (npub: String) -> CashuKeysetCounterStore = { UnavailableCashuKeysetCounterStore },
    encryption: SecretEncryption = SecretEncryption(),
    /** Asks for the key of an account whose key this device does not keep; null never asks. */
    private val keyPrompts: KeyStoragePrompts? = null,
) : AccountSessionStore {
    private val accountStores = AccountPreferenceStores(rootFilesDir)
    private val settingsStores = AccountSettingsStores(accountStores)
    private val secretsStores = AccountSecretsEncryptedStores(rootFilesDir, scope, encryption)
    private val roster = AccountRosterStore(appStores.getDataStore(ROSTER_FILE_NAME), encryption, scope)

    private val savedAccounts = MutableStateFlow<List<AccountInfo>?>(null)
    private val savedAccountsMutex = Mutex()

    private val loadMutex = Mutex()
    private val cachedAccounts = mutableMapOf<String, AccountSettings>()

    // The key each npub holds in [keyVault], as last read or written. Every settings save carries
    // the key, and settings are saved on each last-read marker, so without this every post that
    // loads rewrites the OS keychain item, and macOS asks for the keychain password each time.
    private val keysInVault = ConcurrentHashMap<String, String>()

    // Same for the secrets: AES-GCM re-randomises the IV, so DataStore can never skip an
    // unchanged group by itself and every save would rewrite and fsync the file.
    private val savedSecrets = ConcurrentHashMap<String, AccountSecrets>()

    // Accounts whose key the user chose not to keep on this device during this run: the key file's
    // "Don't remember", which happens inside the vault, after the roster entry was built.
    private val keysNotKept = ConcurrentHashMap.newKeySet<String>()

    // Accounts whose stored key was deleted because they no longer remember it, once per run.
    private val keysForgotten = ConcurrentHashMap.newKeySet<String>()

    // Keys that could not be kept because the key file stayed locked this run. Saved again only on
    // the next run: the user already skipped the password, and each settings save would retry.
    private val keysLockedOut = ConcurrentHashMap<String, String>()

    // Keys the vault failed to keep for any other reason this run (the OS keyring refused, an I/O
    // error). Saved again only on the next run: each settings save (a feed switch, a last-read
    // marker) would otherwise fail the same way, reopen the dialog the user just dismissed, and on
    // a refusing keyring ask for its password again.
    private val keysFailed = ConcurrentHashMap<String, String>()

    // Saved accounts this run opened transiently too (a temporary login, a desktop test run's
    // startup key): the transient entry replaces the saved one in memory, and the saved one is what
    // the roster keeps on disk for the next run.
    private val shadowedAccounts = ConcurrentHashMap<String, AccountInfo>()

    @Volatile
    private var currentAccount: String? = null

    override suspend fun currentAccount(): String? = currentAccount ?: roster.currentAccount().also { currentAccount = it }

    override fun accountsFlow(): StateFlow<List<AccountInfo>?> = savedAccounts

    override suspend fun allSavedAccounts(): List<AccountInfo> {
        savedAccounts.value?.let { return it }
        return savedAccountsMutex.withLock {
            savedAccounts.value ?: readRoster().also { savedAccounts.value = it }
        }
    }

    private suspend fun readRoster(): List<AccountInfo> =
        try {
            roster.allAccountInfoJson()?.let { JsonMapper.fromJson<List<AccountInfo>>(it) } ?: emptyList()
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Log.w(TAG, "Could not read the saved accounts", e)
            emptyList()
        }

    private suspend fun updateSavedAccounts(accounts: List<AccountInfo>) {
        if (savedAccounts.value == accounts) return
        savedAccounts.value = accounts
        roster.setAllAccountInfoJson(JsonMapper.toJson(accounts.mapNotNull { if (it.isTransient) shadowedAccounts[it.npub] else it }))
    }

    private suspend fun updateCurrentAccount(info: AccountInfo?) {
        if (info == null) {
            currentAccount = null
            roster.setCurrentAccount(null)
        } else if (currentAccount != info.npub) {
            currentAccount = info.npub
            if (!info.isTransient) roster.setCurrentAccount(info.npub)
        }
    }

    override suspend fun switchToAccount(accountInfo: AccountInfo) = updateCurrentAccount(accountInfo)

    override suspend fun setDefaultAccount(accountSettings: AccountSettings): AccountSettings {
        val npub = accountSettings.keyPair.pubKey.toNpub()

        // Adding a read-only npub for a pubkey this device already signs for keeps the signing
        // account: saving the read-only one over it would drop its key and every cached list.
        if (!accountSettings.isWriteable()) {
            val existing = loadAccountConfigFromEncryptedStorage(npub)
            if (existing != null && existing.isWriteable()) {
                setCurrentAccount(existing)
                return existing
            }
        }

        // Saved before the roster changes, so a collector of the roster never finds an account
        // whose settings are not on disk yet.
        saveAccountSettings(accountSettings)
        loadMutex.withLock { cachedAccounts[npub] = accountSettings }
        setCurrentAccount(accountSettings)
        return accountSettings
    }

    private suspend fun setCurrentAccount(settings: AccountSettings) {
        val info =
            AccountInfo(
                npub = settings.keyPair.pubKey.toNpub(),
                hasPrivKey = settings.isWriteable(),
                loggedInWithExternalSigner = settings.externalSignerPackageName != null,
                isTransient = settings.transientAccount,
                loggedInWithRemoteSigner = settings.usesRemoteSigner(),
                keyRemembered = settings.rememberKey && settings.keyPair.pubKey.toNpub() !in keysNotKept,
            )
        updateCurrentAccount(info)
        val saved = allSavedAccounts()
        if (info.isTransient) {
            saved.firstOrNull { it.npub == info.npub && !it.isTransient }?.let { shadowedAccounts[info.npub] = it }
        } else {
            shadowedAccounts.remove(info.npub)
        }
        updateSavedAccounts(saved.filter { it.npub != info.npub } + info)
    }

    /** Persists [settings]: the account's records, its secrets and its key. Transient logins keep nothing. */
    suspend fun saveAccountSettings(settings: AccountSettings) {
        if (settings.transientAccount) return
        val npub = settings.keyPair.pubKey.toNpub()
        settingsStores.save(settings)
        val secrets = settings.toAccountSecrets()
        if (savedSecrets[npub] != secrets) {
            secretsStores.saveSecrets(npub, secrets)
            savedSecrets[npub] = secrets
        }
        if (settings.rememberKey) {
            settings.keyPair.privKey?.let { saveKey(npub, it.toHexKey()) }
        } else {
            forgetKey(npub)
        }
        accountStores.getDataStore(npub).edit {
            it[lastReadPerRouteKey] = JsonMapper.toJson(settings.lastReadPerRoute.value.mapValues { entry -> entry.value.value })
            it[pendingAttestationsKey] = JsonMapper.toJson(settings.pendingAttestations.value)
        }
    }

    private val _keyVaultFailure = MutableStateFlow<Throwable?>(null)

    /**
     * Set when a private key could not be kept in [keyVault] (no OS keyring and no fallback password).
     * The login still goes on for this session; the key is gone after a restart. Cleared by the UI
     * once the user was told.
     */
    val keyVaultFailure: StateFlow<Throwable?> = _keyVaultFailure

    fun clearKeyVaultFailure() {
        _keyVaultFailure.value = null
    }

    private suspend fun saveKey(
        npub: String,
        privKeyHex: String,
    ) {
        if (keysInVault[npub] == privKeyHex || keysLockedOut[npub] == privKeyHex || keysFailed[npub] == privKeyHex) return
        try {
            keyVault.save(npub, privKeyHex)
            keysInVault[npub] = privKeyHex
            keysForgotten.remove(npub)
        } catch (e: KeyNotKeptException) {
            // The user's choice, not a failure: no dialog, and no asking again this run.
            keysInVault[npub] = privKeyHex
            keysNotKept.add(npub)
            updateSavedAccounts(allSavedAccounts().map { if (it.npub == npub) it.copy(keyRemembered = false) else it })
        } catch (e: KeyFileLockedException) {
            // A skipped password is not "don't remember": marking the account so would ask for its key
            // at the next launch and delete the copy the key file still holds.
            Log.w(TAG, "Could not keep the private key of $npub: the key file is locked", e)
            keysLockedOut[npub] = privKeyHex
            _keyVaultFailure.value = e
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Log.w(TAG, "Could not keep the private key of $npub", e)
            keysFailed[npub] = privKeyHex
            _keyVaultFailure.value = e
        }
    }

    /**
     * Save keys: keeps the keys this run's "Don't save" left out, now that the user took it back.
     * Each one goes to [keyVault] again (the key file asks for its new password) and its account
     * counts as remembered once the save succeeds; a second "Don't save" marks it as not kept again.
     */
    suspend fun keepDeclinedKeys() {
        keysNotKept.toList().forEach { npub ->
            keysNotKept.remove(npub)
            val key = keysInVault.remove(npub) ?: return@forEach
            saveKey(npub, key)
            // Only once the key is in the vault: a save that failed (an I/O error, a key file left
            // locked) marked remembered would open the account read-only at the next launch
            // instead of asking for its key.
            if (keysInVault[npub] == key && npub !in keysNotKept) {
                updateSavedAccounts(allSavedAccounts().map { if (it.npub == npub) it.copy(keyRemembered = true) else it })
            }
        }
    }

    /** Deletes the key a login that no longer remembers it may have left in [keyVault], once per run. */
    private suspend fun forgetKey(npub: String) {
        keysInVault.remove(npub)
        if (!keysForgotten.add(npub)) return
        try {
            keyVault.delete(npub)
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Log.w(TAG, "Could not delete the private key of $npub", e)
        }
    }

    /**
     * Asks for the key of [npub], whose key this device does not keep, until it matches [pubKeyHex]
     * or the user skips (null: the account loads read-only).
     */
    private suspend fun askForKey(
        npub: String,
        pubKeyHex: String,
    ): String? {
        val prompts = keyPrompts ?: return null
        var wrong = false
        while (true) {
            val typed = prompts.enterKey(npub, wrong)?.trim() ?: return null
            val hex = decodePrivateKeyAsHexOrNull(typed)
            if (hex != null && runCatching { KeyPair(privKey = hex.hexToByteArray()).pubKey.toHexKey() }.getOrNull() == pubKeyHex) {
                return hex
            }
            wrong = true
        }
    }

    override suspend fun loadAccountConfigFromEncryptedStorage(): AccountSettings? = currentAccount()?.let { loadAccountConfigFromEncryptedStorage(it) }

    override suspend fun loadAccountConfigFromEncryptedStorage(npub: String): AccountSettings? =
        loadMutex.withLock {
            cachedAccounts[npub] ?: load(npub)?.also { cachedAccounts[npub] = it }
        }

    private suspend fun load(npub: String): AccountSettings? {
        val records = settingsStores.load(npub)
        val pubKey = records.identity.pubKeyHex ?: return null
        // Only a login holding its own key has one in the vault. Asking the keychain for the others
        // costs a keychain access on macOS and, on Windows and Linux, throws: their backends report a
        // missing item as an error, which the strict lookup must not read as absent.
        val info = allSavedAccounts().firstOrNull { it.npub == npub }
        val remembered = info?.keyRemembered ?: true
        val privKey =
            when {
                info != null && !info.holdsOwnKey() -> null
                !remembered -> askForKey(npub, pubKey)
                else -> keyVault.get(npub)?.also { keysInVault[npub] = it }
            }
        val keyPair = KeyPair(privKey = privKey?.hexToByteArray(), pubKey = pubKey.hexToByteArray())
        val state = accountStores.getDataStore(npub).data.first()
        return AccountSettingsSource(
            keyPair = keyPair,
            externalSignerPackageName = records.identity.externalSignerPackageName,
            stores = records,
            secrets = (secretsStores.loadSecrets(npub)?.also { savedSecrets[npub] = it }) ?: AccountSecrets(),
            pendingAttestationsJson = state[pendingAttestationsKey],
            lastReadPerRouteJson = state[lastReadPerRouteKey],
            cashuCounters = cashuCounters(npub),
        ).toAccountSettings()
            .also { it.rememberKey = remembered }
    }

    /** Drops the in-memory copies, so the next load reads the stores again. */
    internal suspend fun forgetLoadedSettings() = loadMutex.withLock { cachedAccounts.clear() }

    /**
     * Reads [npub]'s settings again for an account that loaded without the key it keeps (its
     * password was skipped, or the keychain refused), once the key storage opens: Settings › Key
     * storage › Unlock or Try again. Null when it still has no key, or keeps none on this device.
     */
    suspend fun reloadWithKey(npub: String): AccountSettings? {
        val info = allSavedAccounts().firstOrNull { it.npub == npub } ?: return null
        if (!info.holdsOwnKey() || !info.keyRemembered) return null
        // What the keyless session changed (last-read markers, settings) goes to disk first, or the
        // reload would read it back from before.
        loadMutex.withLock { cachedAccounts[npub] }?.let { saveAccountSettings(it) }
        return loadMutex.withLock {
            val fresh = load(npub)?.takeIf { it.isWriteable() } ?: return@withLock null
            cachedAccounts[npub] = fresh
            fresh
        }
    }

    override suspend fun setHasBackedUpKeys(
        value: Boolean,
        npub: String,
    ) {
        settingsStores.identity(npub).setHasBackedUpKeys(value)
    }

    /** Whether [npub]'s key was backed up; true unless a freshly generated key is still waiting on it. */
    suspend fun hasBackedUpKeys(npub: String): Boolean = settingsStores.identity(npub).hasBackedUpKeys()

    override suspend fun deleteAccount(accountInfo: AccountInfo) {
        val npub = accountInfo.npub
        loadMutex.withLock { cachedAccounts.remove(npub) }
        keysInVault.remove(npub)
        keysNotKept.remove(npub)
        keysForgotten.remove(npub)
        keysLockedOut.remove(npub)
        keysFailed.remove(npub)
        savedSecrets.remove(npub)
        shadowedAccounts.remove(npub)
        keyVault.delete(npub)
        secretsStores.removeAccount(npub)
        accountStores.removeAccount(npub)
        updateSavedAccounts(allSavedAccounts().filter { it.npub != npub })

        val remaining = allSavedAccounts()
        if (remaining.isEmpty()) {
            updateCurrentAccount(null)
        } else if (currentAccount() == npub) {
            updateCurrentAccount(remaining.first())
        }
    }

    /** Where [pubKeyHex]'s location-chat identity lives: its encrypted secrets file, next to the account's own. */
    fun geohashIdentityStore(pubKeyHex: HexKey): GeohashIdentityStore =
        object : GeohashIdentityStore {
            private val npub by lazy { pubKeyHex.hexToByteArray().toNpub() }

            override suspend fun read(): GeohashIdentitySecrets = secretsStores.loadGeohashIdentity(npub) ?: GeohashIdentitySecrets()

            override suspend fun write(value: GeohashIdentitySecrets) = secretsStores.saveGeohashIdentity(npub, value)
        }

    companion object {
        private const val TAG = "StoreAccountSessionStore"
        private const val ROSTER_FILE_NAME = "roster"

        // The two values Android still keeps in its legacy file; here they sit in the account's DataStore.
        private val lastReadPerRouteKey = stringPreferencesKey("last_read_per_route")
        private val pendingAttestationsKey = stringPreferencesKey("pending_attestations")
    }
}

private fun AccountInfo.holdsOwnKey() = hasPrivKey && !loggedInWithExternalSigner && !loggedInWithRemoteSigner
