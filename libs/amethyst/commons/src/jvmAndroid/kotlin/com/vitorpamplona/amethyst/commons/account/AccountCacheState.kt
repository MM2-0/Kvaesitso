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

import com.vitorpamplona.amethyst.commons.connectedApps.nip46.Nip46ClientStore
import com.vitorpamplona.amethyst.commons.connectedApps.signers.NostrSignerPermissionStore
import com.vitorpamplona.amethyst.commons.domain.nip46.RemoteSignerAuthPrompts
import com.vitorpamplona.amethyst.commons.marmot.EncryptedKeyPackageBundleStore
import com.vitorpamplona.amethyst.commons.marmot.EncryptedMarmotMessageStore
import com.vitorpamplona.amethyst.commons.marmot.EncryptedMlsGroupStateStore
import com.vitorpamplona.amethyst.commons.marmot.EncryptedPublishObligationStore
import com.vitorpamplona.amethyst.commons.model.AMETHYST_CLIENT_TAG_NAME
import com.vitorpamplona.amethyst.commons.model.Account
import com.vitorpamplona.amethyst.commons.model.AccountSettings
import com.vitorpamplona.amethyst.commons.model.GeohashIdentityStore
import com.vitorpamplona.amethyst.commons.model.cache.LocalCache
import com.vitorpamplona.amethyst.commons.model.location.LocationResult
import com.vitorpamplona.amethyst.commons.model.marmot.AndroidIngestDedupStore
import com.vitorpamplona.amethyst.commons.model.marmot.AndroidPushStateStore
import com.vitorpamplona.amethyst.commons.model.marmot.MarmotGroupNotifier
import com.vitorpamplona.amethyst.commons.model.nip46Signer.Nip46ConsentPrompter
import com.vitorpamplona.amethyst.commons.model.preferences.AppPreferenceStores
import com.vitorpamplona.amethyst.commons.relayClient.assemblers.CashuMintDirectoryFilterAssembler
import com.vitorpamplona.amethyst.commons.relayClient.nip47WalletConnect.NWCPaymentFilterAssembler
import com.vitorpamplona.amethyst.commons.relayauth.DataStoreRelayAuthPermissionStore
import com.vitorpamplona.amethyst.commons.service.http.EncryptionKeyCache
import com.vitorpamplona.amethyst.commons.service.pow.PoWPublishQueue
import com.vitorpamplona.cordn.client.CordnBlobCipher
import com.vitorpamplona.marmot.appComponents.agentTextStream.transport.MarmotQuicTransport
import com.vitorpamplona.marmot.client.InMemoryMlsGroupStateStore
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.hexToByteArray
import com.vitorpamplona.quartz.nip01Core.core.toHexKey
import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import com.vitorpamplona.quartz.nip01Core.relay.client.INostrClient
import com.vitorpamplona.quartz.nip01Core.signers.NostrSigner
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerInternal
import com.vitorpamplona.quartz.nip03Timestamp.OtsResolver
import com.vitorpamplona.quartz.nip46RemoteSigner.BunkerClientMetadata
import com.vitorpamplona.quartz.nip46RemoteSigner.NostrConnectURI
import com.vitorpamplona.quartz.nip46RemoteSigner.signer.NostrSignerRemote
import com.vitorpamplona.quartz.nip60Cashu.mintApi.OkHttpMintTransport
import com.vitorpamplona.quartz.nip89AppHandlers.clientTag.NostrSignerWithClientTag
import com.vitorpamplona.quartz.utils.Log
import com.vitorpamplona.quartz.utils.cache.LargeCache
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okio.Path.Companion.toOkioPath
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/**
 * Builds and holds the logged-in [Account]s. Each app passes its platform pieces in, and none of
 * them has a default: a feature that needs a new piece fails to compile in every app until that
 * app supplies it, or passes `null` (or a no-op) to say it goes without. A default here let the
 * desktop app ship without a Web of Trust download and without Cordn storage, unnoticed.
 */
class AccountCacheState(
    val geolocationFlow: () -> StateFlow<LocationResult>,
    val nwcFilterAssembler: () -> NWCPaymentFilterAssembler,
    val cashuMintDirectoryFilterAssembler: () -> CashuMintDirectoryFilterAssembler,
    val okHttpClientForMoney: (String) -> OkHttpClient,
    /**
     * The signer for an account whose key lives in a signer app ([AccountSettings.externalSignerPackageName]):
     * Android's NIP-55 apps. Returns null where the platform has none, which leaves such an account read-only.
     */
    val externalSignerFactory: (pubKey: HexKey, packageName: String) -> NostrSigner?,
    /** Builds the raw-QUIC transport for Marmot agent text stream previews. */
    val marmotStreamTransportFactory: (CoroutineScope) -> MarmotQuicTransport,
    /**
     * Encrypts the Cordn group files kept in each account's directory. Null where the platform has no
     * key store for it yet, which keeps Cordn's state in memory only.
     */
    val cordnBlobCipher: (() -> CordnBlobCipher)?,
    val otsResolverBuilder: () -> OtsResolver,
    val cache: LocalCache,
    val client: INostrClient,
    /** The running app's version name, handed to every [Account] it builds. */
    val appVersion: String,
    /** App-wide media decryption keys, shared by every [Account]. */
    val encryptionKeyCache: EncryptionKeyCache,
    /** Persists an account's settings (the app's encrypted storage). */
    val saveSettings: suspend (AccountSettings) -> Unit,
    /** Announces Marmot Welcomes and group messages; shared by every [Account]. */
    val marmotNotifier: () -> MarmotGroupNotifier,
    /** The NIP-46 consent dialogs, shared by every [Account]. */
    val nip46Consent: Nip46ConsentPrompter,
    /** Builds the store for one account's geohash-chat identity, keyed by its pubkey. */
    val geohashIdentityStore: (HexKey) -> GeohashIdentityStore,
    val rootFilesDir: () -> File,
    val powQueue: () -> PoWPublishQueue?,
    /** Resource-ledger wrapper applied to every account signer (see MeteringNostrSigner); `{ it }` for none. */
    val meterSigner: (NostrSigner) -> NostrSigner,
    /** App-global Connected-Apps signer permission store (shared with napplets), gating the NIP-46 bunker. */
    val signerPermissionStore: NostrSignerPermissionStore,
    /** App-global store of connected NIP-46 client display + relay info. */
    val nip46ClientStore: Nip46ClientStore,
    /**
     * Starts per-account persistence of the Buzz client-side bookkeeping that has no Nostr event to
     * rebuild from — the joined workspaces and the starred channels (restore now, mirror later
     * changes). A lambda because those stores need an Android `Context` and this class deliberately
     * takes none.
     */
    val startBuzzPersistence: (Account) -> Unit,
    /** Who this app says it is when it asks a NIP-46 remote signer to sign (NIP-46 client metadata). */
    val remoteSignerMetadata: BunkerClientMetadata?,
    /**
     * Builds a throwaway relay client for each Web of Trust sync, apart from [client]: the shared
     * client files everything into [cache], and a sync downloads hundreds of thousands of cards.
     * Null turns the Web of Trust download off: the screen then never leaves "not downloaded".
     */
    val trustNetworkClientBuilder: (() -> INostrClient)?,
    /** False on a metered network: background Web of Trust downloads then wait. */
    val canDownloadLargeFiles: () -> Boolean,
    /** Where the pages NIP-46 signers send with `auth_url` challenges wait for the user. */
    val remoteSignerAuthPrompts: RemoteSignerAuthPrompts? = null,
) : AccountCache {
    val accounts = MutableStateFlow<Map<HexKey, Account>>(emptyMap())

    /** Guards [loadAccount]'s check-then-create so concurrent callers can't build twin Accounts. */
    private val loadLock = Any()

    /**
     * One [AppPreferenceStores] per account directory, kept for the life of the
     * process.
     *
     * [buildAccount] runs again for the same account on re-login and on cache
     * races, and DataStore throws if a second instance is ever live on a file
     * that already has one. Caching the holder — rather than the store — keeps
     * that guarantee for every per-account store that gets added here later,
     * not just the relay-auth one.
     */
    private val accountStoreHolders = LargeCache<String, AppPreferenceStores>()

    private fun storesFor(accountDir: File): AppPreferenceStores =
        accountStoreHolders.getOrCreate(accountDir.absolutePath) {
            AppPreferenceStores(rootFilesDir = { accountDir.toOkioPath() })
        }

    override fun loadedAccount(pubkey: HexKey): Account? = accounts.value[pubkey]

    override fun removeAccount(pubkey: HexKey) {
        accounts.update { existingAccounts ->
            val oldValue = existingAccounts[pubkey]
            oldValue?.scope?.cancel()
            // CallManager keeps its own watchdog scope, independent of the account scope
            // cancelled above, so it has to be disposed explicitly.
            oldValue?.callManager?.dispose()
            // Unregisters the tracker's persistent listener from the shared
            // client; without this every removed account leaks a listener.
            oldValue?.chatDeliveryTracker?.destroy()
            // A remote signer listens on its relays until it is closed.
            remoteSigners.remove(pubkey)?.closeSubscription()
            existingAccounts.minus(pubkey)
        }
    }

    /**
     * Loads every saved account that can sign (has a private key or an external signer)
     * into the cache. Safe to call repeatedly — [loadAccount] is idempotent, so already
     * loaded accounts are returned as-is. Used by the always-on notification service so
     * GiftWraps addressed to non-active accounts still get unwrapped and notified.
     */
    suspend fun loadAllWritableAccounts(localPreferences: AccountSessionStore) {
        localPreferences.allSavedAccounts().forEach { savedAccount ->
            if (!savedAccount.canSign()) return@forEach
            try {
                val accountSettings = localPreferences.loadAccountConfigFromEncryptedStorage(savedAccount.npub) ?: return@forEach
                loadAccount(accountSettings)
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                Log.w("AccountCacheState", "Failed to preload account ${savedAccount.npub}", e)
            }
        }
    }

    /**
     * Cancels and removes every cached account whose pubkey is not in [keepPubkeys].
     * Used to release accounts that were preloaded for background notification handling
     * when the always-on service is turned off, while preserving the active account.
     */
    fun retainOnly(keepPubkeys: Set<HexKey>) {
        accounts.update { existingAccounts ->
            val toRemove = existingAccounts.filterKeys { it !in keepPubkeys }
            if (toRemove.isEmpty()) {
                existingAccounts
            } else {
                toRemove.values.forEach { it.scope.cancel() }
                // Like removeAccount: a cached signer would keep listening, and a reload would reuse
                // it with callbacks bound to these settings instead of the reloaded ones.
                toRemove.keys.forEach { remoteSigners.remove(it)?.closeSubscription() }
                existingAccounts.minus(toRemove.keys)
            }
        }
    }

    /** The on-disk root that [loadAccount] creates a per-account directory under. */
    private fun accountsRootDir() = File(rootFilesDir(), "accounts")

    /**
     * Deletes the on-disk per-account directory (the MLS/Marmot stores created in
     * [loadAccount]). Call only on permanent account deletion — [removeAccount] just
     * drops the in-memory copy and leaves these files behind.
     *
     * Also drops the account's DM index from [cache]: nothing else removes it, so the logged-out
     * account's conversations (decrypted NIP-17 messages included) stayed in memory until the
     * process died.
     */
    override fun deleteAccountFiles(pubkey: HexKey) {
        cache.chatroomList.remove(pubkey)
        val dir = File(accountsRootDir(), pubkey)
        if (dir.exists() && !dir.deleteRecursively()) {
            Log.w("AccountCacheState") { "Failed to delete account directory ${dir.absolutePath}" }
        }
    }

    /**
     * Removes per-account directories left behind by accounts that are no longer saved
     * (e.g. deleted before [deleteAccountFiles] existed). Keeps only [keepPubkeys]. Safe to
     * run alongside [loadAccount]: it only loads saved accounts, whose pubkeys are kept.
     */
    fun pruneOrphanAccountDirs(keepPubkeys: Set<HexKey>) {
        val children = accountsRootDir().listFiles() ?: return
        children.forEach { child ->
            if (child.isDirectory && child.name !in keepPubkeys) {
                if (child.deleteRecursively()) {
                    Log.d("AccountCacheState") { "Pruned orphan account dir ${child.name.take(8)}…" }
                } else {
                    Log.w("AccountCacheState") { "Failed to prune orphan account dir ${child.absolutePath}" }
                }
            }
        }
    }

    /** The NIP-46 signers of the loaded remote-signer accounts, closed when their account is removed. */
    private val remoteSigners = ConcurrentHashMap<HexKey, NostrSignerRemote>()

    private fun remoteSignerFor(settings: AccountSettings): NostrSignerRemote {
        val pubKey = settings.keyPair.pubKey.toHexKey()
        remoteSigners[pubKey]?.let { return it }
        // getOrPut on a ConcurrentMap may run its block in two racing loads (the UI login and the
        // always-on preload) and keep one, which left the other's relay subscription open,
        // unreachable and never closed. Here the loser closes its own. Opened before it is
        // published, so a removal that finds it in the map always finds it open and closes it.
        val transport = NostrSignerInternal(KeyPair(privKey = settings.remoteSignerTransportKey!!.hexToByteArray()))
        val built =
            NostrSignerRemote
                .fromBunkerUri(
                    settings.remoteSignerBunkerUri!!,
                    transport,
                    client,
                    clientMetadata = remoteSignerMetadata,
                    onAuthUrl = remoteSignerAuthPrompts?.let { prompts -> { url -> prompts.report(url, pubKey) } },
                    onAuthUrlSettled = remoteSignerAuthPrompts?.let { prompts -> { url -> prompts.settled(url, pubKey) } },
                    // The signer moved (NIP-46 switch_relays): the saved address follows, so the
                    // next launch, and the Tor routing of signer relays, reach it there.
                    onRelaysSwitched = { relays ->
                        NostrConnectURI.parseBunker(settings.remoteSignerBunkerUri!!)?.let { saved ->
                            settings.remoteSignerBunkerUri = NostrConnectURI.buildBunker(saved.remoteSignerPubKey, relays, secret = null)
                            settings.saveAccountSettings()
                        }
                    },
                ).also {
                    // The user's key, not the transport key: self-encryption (private lists, drafts)
                    // keys off signer.pubKey.
                    it.bindUserPubkey(pubKey)
                }
        built.openSubscription()
        remoteSigners.putIfAbsent(pubKey, built)?.let { winner ->
            built.closeSubscription()
            return winner
        }
        return built
    }

    override fun loadAccount(accountSettings: AccountSettings): Account {
        val pubKey = accountSettings.keyPair.pubKey.toHexKey()
        // Checked before a signer is built: a remote signer opens a relay subscription.
        accounts.value[pubKey]?.let { cached ->
            if (!supersedes(accountSettings, cached.settings)) return cached
            // A login that brings what the loaded account lacks, or changes whether its key is
            // kept: the old account would go on signing with nothing, and its saves would put back
            // the settings this login replaced.
            removeAccount(pubKey)
        }
        return loadAccount(
            signer =
                if (accountSettings.keyPair.privKey != null) {
                    NostrSignerInternal(accountSettings.keyPair)
                } else if (accountSettings.usesRemoteSigner()) {
                    remoteSignerFor(accountSettings)
                } else {
                    when (val packageName = accountSettings.externalSignerPackageName) {
                        null -> {
                            NostrSignerInternal(accountSettings.keyPair)
                        }

                        else -> {
                            externalSignerFactory(accountSettings.keyPair.pubKey.toHexKey(), packageName)
                                ?: NostrSignerInternal(accountSettings.keyPair)
                        }
                    }
                },
            accountSettings = accountSettings,
        )
    }

    fun loadAccount(
        signer: NostrSigner,
        accountSettings: AccountSettings,
    ): Account {
        val cached = accounts.value[signer.pubKey]
        if (cached != null) return cached

        // Serialize construction: the UI login path and the always-on service's preload race
        // to load the same account on cold start. Without the lock both see a null cache and
        // both build an Account — the loser is never cancelled, leaving a zombie whose
        // Nip46SignerState answers bunker requests with a NostrSignerExternal no Activity
        // ever registers a launcher on (every sign fails "No activity to launch from"),
        // while duplicating consent prompts and racing error replies to NIP-46 clients.
        return synchronized(loadLock) {
            accounts.value[signer.pubKey]?.let { return it }
            createAccount(signer, accountSettings)
        }
    }

    private fun createAccount(
        signer: NostrSigner,
        accountSettings: AccountSettings,
    ): Account {
        val signerWithClientTag =
            NostrSignerWithClientTag(
                inner = meterSigner(signer),
                clientName = CLIENT_TAG_NAME,
                disabled = { !accountSettings.syncedSettings.security.addClientTag.value },
            )

        val accountDir = File(rootFilesDir(), "accounts/${signer.pubKey}").apply { mkdirs() }

        val mlsStore =
            try {
                Log.d("AccountCacheState") {
                    "Initializing EncryptedMlsGroupStateStore for ${signer.pubKey.take(8)}… at ${accountDir.absolutePath}"
                }
                EncryptedMlsGroupStateStore(accountDir)
            } catch (e: Exception) {
                Log.e(
                    "AccountCacheState",
                    "Failed to initialize EncryptedMlsGroupStateStore, falling back to in-memory store (Marmot groups will NOT persist across restarts)",
                    e,
                )
                InMemoryMlsGroupStateStore()
            }
        Log.d("AccountCacheState") {
            "Account ${signer.pubKey.take(8)}… using Marmot store: ${mlsStore::class.simpleName}"
        }

        val marmotMessageStore =
            try {
                EncryptedMarmotMessageStore(accountDir)
            } catch (e: Exception) {
                Log.e(
                    "AccountCacheState",
                    "Failed to initialize EncryptedMarmotMessageStore (Marmot messages will NOT persist across restarts)",
                    e,
                )
                null
            }

        val marmotKeyPackageStore =
            try {
                EncryptedKeyPackageBundleStore(accountDir)
            } catch (e: Exception) {
                Log.e(
                    "AccountCacheState",
                    "Failed to initialize EncryptedKeyPackageBundleStore (Marmot KeyPackages will NOT persist across restarts)",
                    e,
                )
                null
            }

        val marmotPublishObligationStore =
            try {
                EncryptedPublishObligationStore(accountDir)
            } catch (e: Exception) {
                Log.e(
                    "AccountCacheState",
                    "Failed to initialize EncryptedPublishObligationStore " +
                        "(a Marmot commit interrupted mid-publish will NOT be retried after a restart)",
                    e,
                )
                null
            }

        val marmotIngestDedupStore =
            try {
                AndroidIngestDedupStore(accountDir)
            } catch (e: Exception) {
                Log.e(
                    "AccountCacheState",
                    "Failed to initialize AndroidIngestDedupStore " +
                        "(every backdated gift wrap will be re-decided on each sync)",
                    e,
                )
                null
            }

        val marmotPushStateStore =
            try {
                AndroidPushStateStore(accountDir)
            } catch (e: Exception) {
                Log.e(
                    "AccountCacheState",
                    "Failed to initialize AndroidPushStateStore " +
                        "(a revoked push token could be resurrected by a relayed token list after a restart)",
                    e,
                )
                null
            }

        // Per-account NIP-42 ALLOW/DENY overrides live in this account's own dir, so a DENY for one
        // account never leaks into another (the store used to be a single app-wide file).
        val relayAuthPermissionStore =
            DataStoreRelayAuthPermissionStore(
                storesFor(accountDir).getDataStore(DataStoreRelayAuthPermissionStore.FILE_NAME),
            )

        return Account(
            settings = accountSettings,
            signer = signerWithClientTag,
            geolocationFlow = geolocationFlow,
            nwcFilterAssembler = nwcFilterAssembler,
            cashuMintDirectoryFilterAssembler = cashuMintDirectoryFilterAssembler,
            cashuMintTransport = OkHttpMintTransport(okHttpClientForMoney),
            otsResolverBuilder = otsResolverBuilder,
            cache = cache,
            client = client,
            appVersion = appVersion,
            encryptionKeyCache = encryptionKeyCache,
            saveSettings = saveSettings,
            marmotNotifier = marmotNotifier,
            nip46Consent = nip46Consent,
            geohashIdentityStore = geohashIdentityStore(signer.pubKey),
            marmotStreamTransportFactory = marmotStreamTransportFactory,
            cordnBlobCipher = cordnBlobCipher,
            scope =
                CoroutineScope(
                    Dispatchers.IO +
                        SupervisorJob() +
                        CoroutineExceptionHandler { _, throwable ->
                            Log.e("AccountCacheState", "Account ${signer.pubKey} caught exception", throwable)
                        },
                ),
            // The same per-account directory the Marmot stores use. cordn
            // scopes itself further by coordinator underneath it, because a
            // gid is unique only within one (spec/00.md §4).
            cordnFilesDir = accountDir.toOkioPath().takeIf { cordnBlobCipher != null },
            // The Web of Trust index lives with the account, so deleting the account deletes it.
            trustNetworkDir = accountDir.toOkioPath() / "wot",
            trustNetworkClientBuilder = trustNetworkClientBuilder,
            canDownloadLargeFiles = canDownloadLargeFiles,
            mlsGroupStateStore = mlsStore,
            marmotMessageStore = marmotMessageStore,
            marmotKeyPackageStore = marmotKeyPackageStore,
            marmotPublishObligationStore = marmotPublishObligationStore,
            marmotIngestDedupStore = marmotIngestDedupStore,
            marmotPushStateStore = marmotPushStateStore,
            powQueue = powQueue,
            relayAuthPermissionStore = relayAuthPermissionStore,
            signerPermissionStore = signerPermissionStore,
            nip46ClientStore = nip46ClientStore,
        ).also { newAccount ->
            // Per account, not per device: the joined set makes a relay first-party for NIP-42, so a
            // shared one hands every other logged-in account an automatic login on a workspace it
            // never joined, and a shared star set reorders everyone's channel list at once.
            startBuzzPersistence(newAccount)
            accounts.update { existingAccounts ->
                existingAccounts.plus(Pair(signer.pubKey, newAccount))
            }
            if (signer is NostrSignerRemote) newAccount.scope.launch { askToSwitchRelays(signer) }
        }
    }

    /**
     * NIP-46 asks clients to send `switch_relays` as soon as they are connected, so a signer that
     * moved can say so. Every launch and every login loads the account through here. A signer
     * that predates the method rejects it, which changes nothing.
     */
    private suspend fun askToSwitchRelays(signer: NostrSignerRemote) {
        try {
            signer.switchRelays()?.let { relays ->
                Log.i("AccountCacheState") { "Remote signer moved to ${relays.joinToString { it.url }}" }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.d("AccountCacheState", "Remote signer did not answer switch_relays", e)
        }
    }

    fun clear() {
        accounts.update { existingAccounts ->
            existingAccounts.forEach {
                it.value.scope.cancel()
                it.value.chatDeliveryTracker.destroy()
            }
            emptyMap()
        }
    }

    companion object {
        const val CLIENT_TAG_NAME = AMETHYST_CLIENT_TAG_NAME
    }
}

/**
 * Whether [incoming], the settings a login just made current, replace the loaded account's
 * [loaded] ones: it can sign where the loaded one is read-only (the key was skipped or could not
 * be read at launch, and the user logged in again with it), or it keeps the key where the loaded
 * one did not, or the other way around.
 */
internal fun supersedes(
    incoming: AccountSettings,
    loaded: AccountSettings,
): Boolean {
    if (incoming === loaded || !incoming.isWriteable()) return false
    return !loaded.isWriteable() || incoming.rememberKey != loaded.rememberKey
}
