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

import androidx.compose.runtime.Stable
import com.vitorpamplona.amethyst.commons.defaults.DefaultNIP65RelaySet
import com.vitorpamplona.amethyst.commons.domain.nip46.BunkerLoginUseCase
import com.vitorpamplona.amethyst.commons.domain.nip46.Nip05SignerLoginUseCase
import com.vitorpamplona.amethyst.commons.domain.nip46.NostrConnectLoginUseCase
import com.vitorpamplona.amethyst.commons.domain.nip46.RemoteSignerAuthPrompts
import com.vitorpamplona.amethyst.commons.domain.nip46.RemoteSignerLogoutUseCase
import com.vitorpamplona.amethyst.commons.domain.nip46.stripBunkerSecret
import com.vitorpamplona.amethyst.commons.model.Account
import com.vitorpamplona.amethyst.commons.model.AccountSettings
import com.vitorpamplona.amethyst.commons.model.navigation.Route
import com.vitorpamplona.amethyst.commons.tor.SignerRelayRouting
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.hexToByteArray
import com.vitorpamplona.quartz.nip01Core.core.toHexKey
import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import com.vitorpamplona.quartz.nip01Core.relay.client.INostrClient
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerInternal
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerSync
import com.vitorpamplona.quartz.nip05DnsIdentifiers.Nip05Client
import com.vitorpamplona.quartz.nip05DnsIdentifiers.Nip05Id
import com.vitorpamplona.quartz.nip05DnsIdentifiers.Nip05RemoteSigner
import com.vitorpamplona.quartz.nip05DnsIdentifiers.resolveUserHexOrNull
import com.vitorpamplona.quartz.nip06KeyDerivation.Nip06
import com.vitorpamplona.quartz.nip19Bech32.Bech32Transcription
import com.vitorpamplona.quartz.nip19Bech32.Nip19Parser
import com.vitorpamplona.quartz.nip19Bech32.decodePrivateKeyAsHexOrNull
import com.vitorpamplona.quartz.nip19Bech32.decodePublicKeyAsHexOrNull
import com.vitorpamplona.quartz.nip19Bech32.entities.NAddress
import com.vitorpamplona.quartz.nip19Bech32.entities.NEmbed
import com.vitorpamplona.quartz.nip19Bech32.entities.NEvent
import com.vitorpamplona.quartz.nip19Bech32.entities.NNote
import com.vitorpamplona.quartz.nip19Bech32.entities.NProfile
import com.vitorpamplona.quartz.nip19Bech32.entities.NPub
import com.vitorpamplona.quartz.nip19Bech32.entities.NRelay
import com.vitorpamplona.quartz.nip19Bech32.entities.NSec
import com.vitorpamplona.quartz.nip19Bech32.toNpub
import com.vitorpamplona.quartz.nip46RemoteSigner.BunkerClientMetadata
import com.vitorpamplona.quartz.nip46RemoteSigner.NostrConnectURI
import com.vitorpamplona.quartz.nip49PrivKeyEnc.Nip49
import com.vitorpamplona.quartz.utils.Hex
import com.vitorpamplona.quartz.utils.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

val EMAIL_PATTERN = Regex(".+@.+\\.[a-z]+")

private const val BUNKER_URI_PREFIX = "bunker://"

// Comfortably longer than the account Tor connector's debounce (200 ms) plus loading the account.
private const val SIGNER_RELAY_HANDOVER_MS = 10_000L

sealed class AccountState {
    object Loading : AccountState()

    object LoggedOff : AccountState()

    @Stable
    class LoggedIn(
        val account: Account,
        var route: Route? = null,
    ) : AccountState()
}

@Stable
class AccountSessionManager(
    val accountsCache: AccountCache,
    val nip05ClientBuilder: () -> Nip05Client,
    val clientBuilder: () -> INostrClient,
    val localPreferences: AccountSessionStore,
    val scope: CoroutineScope,
    val hooks: AccountSessionHooks = object : AccountSessionHooks {},
    /** Who this app says it is to a NIP-46 remote signer it logs in with. */
    val remoteSignerMetadata: BunkerClientMetadata? = null,
    /** Routes a NIP-46 login's relays like the user's own before any account lists them. */
    val signerRelayRouting: SignerRelayRouting = SignerRelayRouting.None,
    /** Where a NIP-46 signer's `auth_url` challenges wait for the user, during login and after. */
    val remoteSignerAuthPrompts: RemoteSignerAuthPrompts = RemoteSignerAuthPrompts(),
) {
    private val _accountContent = MutableStateFlow<AccountState>(AccountState.Loading)
    val accountContent = _accountContent.asStateFlow()

    fun loggedInAccount() = (_accountContent.value as? AccountState.LoggedIn)?.account

    fun loginWithDefaultAccountIfLoggedOff() {
        // pulls account from storage.
        if (_accountContent.value !is AccountState.LoggedIn) {
            scope.launch(Dispatchers.IO) {
                loginWithDefaultAccount()
            }
        }
    }

    private suspend fun loginWithDefaultAccount(routeBuilder: ((account: Account) -> Route?)? = null) {
        val accountSettings =
            try {
                localPreferences.loadAccountConfigFromEncryptedStorage()
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                // A keystore that cannot be read (locked, or access denied) would otherwise leave
                // the app on its loading screen; the saved account stays for the next attempt.
                Log.w("AccountSessionManager", "Could not load the default account", e)
                null
            }

        if (accountSettings != null) {
            startUI(accountSettings, routeBuilder)
        } else {
            requestLoginUI()
        }
    }

    private fun requestLoginUI() = _accountContent.update { AccountState.LoggedOff }

    suspend fun loginAndStartUI(
        key: String,
        transientAccount: Boolean,
        loginWithExternalSigner: Boolean = false,
        packageName: String = "",
        rememberKey: Boolean = true,
    ) = withContext(Dispatchers.IO) {
        if (key.startsWith(BUNKER_URI_PREFIX)) {
            return@withContext startUI(localPreferences.setDefaultAccount(remoteSignerLogin(key, transientAccount)))
        }

        val parsed = Nip19Parser.uriToRoute(key)?.entity
        val pubKeyParsed =
            when (parsed) {
                is NSec -> null
                is NPub -> parsed.hex.hexToByteArray()
                is NProfile -> parsed.hex.hexToByteArray()
                is NNote -> null
                is NEvent -> null
                is NEmbed -> null
                is NRelay -> null
                is NAddress -> null
                else -> runCatching { if (loginWithExternalSigner) Hex.decode(key) else null }.getOrNull()
            }

        if (loginWithExternalSigner && pubKeyParsed == null) {
            throw Exception("Invalid key while trying to login with external signer")
        }

        val accountSettings =
            when {
                loginWithExternalSigner -> {
                    val keyPair = KeyPair(pubKey = pubKeyParsed)
                    AccountSettings(
                        keyPair = keyPair,
                        transientAccount = transientAccount,
                        cashuCounters = cashuCountersFor(keyPair.pubKey),
                        externalSignerPackageName = packageName.ifBlank { "com.greenart7c3.nostrsigner" },
                    )
                }

                key.startsWith("nsec") -> {
                    val privHex =
                        decodePrivateKeyAsHexOrNull(key)
                            ?: throw InvalidKeyException("Invalid nsec key")
                    val keyPair =
                        try {
                            KeyPair(privKey = privHex.hexToByteArray())
                        } catch (e: Exception) {
                            throw InvalidKeyException("Not a key: ${e::class.simpleName}")
                        }
                    AccountSettings(
                        keyPair = keyPair,
                        transientAccount = transientAccount,
                        rememberKey = rememberKey,
                        cashuCounters = cashuCountersFor(keyPair.pubKey),
                    )
                }

                key.contains(" ") && Nip06().isValidMnemonic(key) -> {
                    val keyPair = KeyPair(privKey = Nip06().privateKeyFromMnemonic(key))
                    AccountSettings(
                        keyPair = keyPair,
                        transientAccount = transientAccount,
                        rememberKey = rememberKey,
                        cashuCounters = cashuCountersFor(keyPair.pubKey),
                    )
                }

                pubKeyParsed != null -> {
                    val keyPair = KeyPair(pubKey = pubKeyParsed)
                    AccountSettings(
                        keyPair = keyPair,
                        transientAccount = transientAccount,
                        cashuCounters = cashuCountersFor(keyPair.pubKey),
                    )
                }

                else -> {
                    val keyPair =
                        try {
                            KeyPair(Hex.decode(key))
                        } catch (e: Exception) {
                            throw InvalidKeyException("Not a key: ${e::class.simpleName}")
                        }
                    AccountSettings(
                        keyPair = keyPair,
                        transientAccount = transientAccount,
                        rememberKey = rememberKey,
                        cashuCounters = cashuCountersFor(keyPair.pubKey),
                    )
                }
            }

        // setDefaultAccount may keep an existing signing account instead of this one when a
        // read-only npub is added for a pubkey we already sign for — show whichever became current.
        val current = localPreferences.setDefaultAccount(accountSettings)

        startUI(current)
    }

    /**
     * Connects to the NIP-46 signer at [bunkerUri] with a fresh transport key and returns the settings
     * of the account it signs for. The account cache opens its own signer from those settings, so
     * the one used to connect is closed here.
     */
    private suspend fun remoteSignerLogin(
        bunkerUri: String,
        transientAccount: Boolean,
    ): AccountSettings {
        val transport = KeyPair()
        val relays = NostrConnectURI.parseBunker(bunkerUri)?.relays ?: emptySet()
        return withSignerRelays(relays) {
            val result = BunkerLoginUseCase.execute(bunkerUri, NostrSignerInternal(transport), clientBuilder(), remoteSignerMetadata, remoteSignerAuthPrompts)
            result.signer.closeSubscription()
            remoteSignerSettings(result.pubKeyHex, stripBunkerSecret(bunkerUri), transport, transientAccount)
        }
    }

    /** The NIP-46 signer [address]'s domain advertises, or null for none (or no answer). */
    private suspend fun findNip05Signer(address: String): Nip05RemoteSigner? {
        val nip05 = Nip05Id.parse(address) ?: return null
        return try {
            nip05ClientBuilder().loadRemoteSigner(nip05)
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Log.d("Login", "No remote signer found for $address", e)
            null
        }
    }

    /** Logs in through the signer a NIP-05 domain advertises, with a fresh transport key. */
    private suspend fun nip05SignerLogin(
        signer: Nip05RemoteSigner,
        transientAccount: Boolean,
    ): AccountSettings {
        val transport = KeyPair()
        return withSignerRelays(signer.relays) {
            val login = Nip05SignerLoginUseCase.execute(signer, transport, clientBuilder(), remoteSignerMetadata, remoteSignerAuthPrompts)
            login.result.signer.closeSubscription()
            remoteSignerSettings(login.result.pubKeyHex, login.bunkerUri, transport, transientAccount)
        }
    }

    /**
     * Runs [block] with [relays] routed as the user's own. After a login the account's saved bunker
     * address takes over (`AccountsTorStateConnector`, which waits out a short debounce), so the
     * registration outlives success by [SIGNER_RELAY_HANDOVER_MS]: dropping it at once would flip
     * the signer's sockets onto Tor and straight back.
     */
    private suspend fun <T> withSignerRelays(
        relays: Set<NormalizedRelayUrl>,
        block: suspend () -> T,
    ): T {
        signerRelayRouting.registerSignerRelays(relays)
        var loggedIn = false
        try {
            return block().also { loggedIn = true }
        } finally {
            if (loggedIn) {
                scope.launch {
                    delay(SIGNER_RELAY_HANDOVER_MS)
                    signerRelayRouting.unregisterSignerRelays(relays)
                }
            } else {
                signerRelayRouting.unregisterSignerRelays(relays)
            }
        }
    }

    /**
     * Logs in through a NIP-46 signer that scans a `nostrconnect://` address: [onUri] receives the
     * address to show, and this returns once the signer answered (or throws when it never does).
     */
    suspend fun loginWithNostrConnect(
        relays: List<String>,
        transientAccount: Boolean,
        onUri: (String) -> Unit,
    ) = withContext(Dispatchers.IO) {
        val transport = KeyPair()
        val uriData = NostrConnectLoginUseCase.generateUri(transport, relays, remoteSignerMetadata)

        withSignerRelays(uriData.relays) {
            onUri(uriData.uri)

            // The wait for the signer and each request to it carry their own timeouts. One more
            // around the whole would cut off a signer that answers near the end of the first.
            val result = NostrConnectLoginUseCase.awaitAndLogin(uriData, clientBuilder(), remoteSignerAuthPrompts)
            result.signer.closeSubscription()

            val bunkerUri = NostrConnectURI.buildBunker(result.signer.remotePubkey, uriData.relays, secret = null)
            startUI(localPreferences.setDefaultAccount(remoteSignerSettings(result.pubKeyHex, bunkerUri, transport, transientAccount)))
        }
    }

    private fun remoteSignerSettings(
        userPubKeyHex: HexKey,
        bunkerUri: String,
        transport: KeyPair,
        transientAccount: Boolean,
    ): AccountSettings {
        val keyPair = KeyPair(pubKey = userPubKeyHex.hexToByteArray())
        return AccountSettings(
            keyPair = keyPair,
            transientAccount = transientAccount,
            cashuCounters = cashuCountersFor(keyPair.pubKey),
            remoteSignerBunkerUri = bunkerUri,
            remoteSignerTransportKey = transport.privKey!!.toHexKey(),
        )
    }

    /**
     * Every [AccountSettings] this manager builds must carry the host's durable NUT-13 store:
     * the default refuses to mint, so a fresh login would fail every Cashu receive until restart.
     */
    private fun cashuCountersFor(pubKey: ByteArray) = hooks.cashuCountersFor(pubKey.toNpub())

    fun startUI(
        accountSettings: AccountSettings,
        routeBuilder: ((account: Account) -> Route?)? = null,
    ) {
        val account = accountsCache.loadAccount(accountSettings)
        _accountContent.update {
            AccountState.LoggedIn(account, routeBuilder?.invoke(account))
        }
    }

    /** What the user typed is not a key; the login form words this itself. */
    class InvalidKeyException(
        message: String,
    ) : Exception(message)

    fun login(
        key: String,
        password: String,
        transientAccount: Boolean,
        loginWithExternalSigner: Boolean = false,
        packageName: String = "",
        rememberKey: Boolean = true,
        /** Whether a `name@domain` may log in through the signer its domain advertises (NIP-46). */
        allowRemoteSigner: Boolean = true,
        onError: (String?) -> Unit,
    ) {
        // Accepts keys copied by hand from the backup screen: UPPERCASE, and split
        // into groups by spaces, dashes or line breaks.
        val cleanKey = Bech32Transcription.normalize(key)
        scope.launch(Dispatchers.IO) {
            if (cleanKey.startsWith("ncryptsec")) {
                val newKey =
                    try {
                        if (cleanKey.isEmpty() || password.isEmpty()) {
                            null
                        } else {
                            Nip49().decrypt(cleanKey, password)
                        }
                    } catch (e: Exception) {
                        if (e is CancellationException) throw e
                        onError(e.message)
                        return@launch
                    }

                if (newKey == null) {
                    onError("Could not decrypt key with provided password")
                    Log.e("Login", "Could not decrypt ncryptsec")
                } else {
                    loginSync(newKey, transientAccount, loginWithExternalSigner, packageName, onError, rememberKey)
                }
            } else if (EMAIL_PATTERN.matches(cleanKey)) {
                // A domain may advertise a NIP-46 signer for the address: sign through it. Without one
                // the address still resolves, to a read-only account.
                val signer = if (allowRemoteSigner) findNip05Signer(cleanKey) else null
                if (signer != null) {
                    try {
                        startUI(localPreferences.setDefaultAccount(nip05SignerLogin(signer, transientAccount)))
                    } catch (e: Nip05SignerLoginUseCase.SignerMismatchException) {
                        Log.w("Login") { "The signer for $cleanKey signs for ${e.actual.take(8)}, not ${e.expected.take(8)}" }
                        onError("The signer for $cleanKey signs for a different account than the address names")
                    } catch (e: Exception) {
                        if (e is CancellationException) throw e
                        Log.w("Login", "Could not log in through the signer for $cleanKey", e)
                        onError("Could not log in through the signer for $cleanKey: ${e.message}")
                    }
                    return@launch
                }
                // Delegate to the shared quartz resolver so NIP-05 handling stays in
                // lockstep with the CLI and anywhere else we accept user identifiers.
                try {
                    val hex =
                        resolveUserHexOrNull(cleanKey, nip05ClientBuilder())
                    if (hex == null) {
                        onError("User not found in the nip05 server: $cleanKey")
                    } else {
                        loginSync(Hex.decode(hex).toNpub(), transientAccount, loginWithExternalSigner, packageName, onError)
                    }
                } catch (e: Exception) {
                    if (e is CancellationException) throw e
                    onError("Could not load nip05 address from the server: $cleanKey. ${e.message}")
                }
            } else {
                loginSync(cleanKey, transientAccount, loginWithExternalSigner, packageName, onError, rememberKey)
            }
        }
    }

    fun login(
        key: String,
        transientAccount: Boolean,
        loginWithExternalSigner: Boolean = false,
        packageName: String = "",
        onError: (String) -> Unit,
    ) {
        scope.launch(Dispatchers.IO) {
            loginSync(key, transientAccount, loginWithExternalSigner, packageName, { onError(it.orEmpty()) })
        }
    }

    suspend fun loginSync(
        key: String,
        transientAccount: Boolean,
        loginWithExternalSigner: Boolean = false,
        packageName: String = "",
        onError: (String?) -> Unit,
        rememberKey: Boolean = true,
    ) {
        try {
            loginAndStartUI(key, transientAccount, loginWithExternalSigner, packageName, rememberKey)
        } catch (e: InvalidKeyException) {
            // Null: the form says "Invalid key" once, rather than "Invalid key: ... Invalid nsec key".
            Log.w("Login") { "The key did not parse: ${e.message}" }
            onError(null)
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Log.e("Login", "Could not sign in", e)
            onError(e.message)
        }
    }

    fun newKey(name: String? = null) {
        scope.launch(Dispatchers.IO) { signUp(name) }
    }

    /**
     * The sign-up screen's new account: a fresh key with the default relays and lists, opened on the
     * follow import and published to the relays. [transientAccount] keeps its key and settings off
     * this device (a desktop test run that must not stop at a key prompt).
     */
    suspend fun signUp(
        name: String? = null,
        transientAccount: Boolean = false,
    ): AccountSettings =
        withContext(Dispatchers.IO) {
            _accountContent.update { AccountState.Loading }

            val accountSettings = createNewAccount(name, transientAccount)

            localPreferences.setDefaultAccount(accountSettings)

            // Freshly-generated key: mark it as not-yet-backed-up so the home screen
            // nudges the user to save their secret key. Accounts logged in via an
            // existing nsec/bunker/external signer never get this false flag (the
            // pref defaults to true), so only brand-new accounts are nudged.
            localPreferences.setHasBackedUpKeys(false, accountSettings.keyPair.pubKey.toNpub())

            startUI(accountSettings, routeBuilder = { Route.ImportFollowsSelectUser })

            scope.launch(Dispatchers.IO) {
                delay(2000) // waits for the new user to connect to the new relays.

                val toPost = accountSettings.backupNIP65RelayList?.allWriteRelaysNorm()?.toSet() ?: DefaultNIP65RelaySet

                val client = clientBuilder()

                accountSettings.backupUserMetadata?.let { client.publish(it, toPost) }
                accountSettings.backupContactList?.let { client.publish(it, toPost) }
                accountSettings.backupNIP65RelayList?.let { client.publish(it, toPost) }
                accountSettings.backupDMRelayList?.let { client.publish(it, toPost) }
                accountSettings.backupKeyPackageRelayList?.let { client.publish(it, toPost) }
                accountSettings.backupSearchRelayList?.let { client.publish(it, toPost) }
                accountSettings.backupIndexRelayList?.let { client.publish(it, toPost) }
                accountSettings.backupFavoriteRelayList?.let { client.publish(it, toPost) }
                accountSettings.backupPublicChatList?.let { client.publish(it, toPost) }
                accountSettings.backupCashuWallet?.let { client.publish(it, toPost) }
                accountSettings.backupNutzapInfo?.let { client.publish(it, toPost) }
            }

            accountSettings
        }

    fun createNewAccount(
        name: String? = null,
        transientAccount: Boolean = false,
    ): AccountSettings {
        val keyPair = KeyPair()
        val bootstrap =
            bootstrapAccountEvents(
                signer = NostrSignerSync(keyPair),
                name = name,
            )
        return AccountSettings(
            keyPair = keyPair,
            transientAccount = transientAccount,
            cashuCounters = cashuCountersFor(keyPair.pubKey),
            backupUserMetadata = bootstrap.userMetadata,
            backupContactList = bootstrap.contactList,
            backupNIP65RelayList = bootstrap.nip65RelayList,
            backupDMRelayList = bootstrap.dmRelayList,
            backupKeyPackageRelayList = bootstrap.keyPackageRelayList,
            backupSearchRelayList = bootstrap.searchRelayList,
            backupIndexRelayList = bootstrap.indexerRelayList,
            backupPublicChatList = bootstrap.publicChatList,
            backupFavoriteRelayList = bootstrap.favoriteRelayList,
        )
    }

    fun switchUser(accountInfo: AccountInfo) {
        scope.launch(Dispatchers.IO) {
            switchUserSync(accountInfo)
        }
    }

    suspend fun checkAndSwitchUserSync(
        npub: String,
        routeBuilder: ((account: Account) -> Route?)? = null,
    ): Boolean {
        if (npub != localPreferences.currentAccount()) {
            val account = localPreferences.allSavedAccounts().firstOrNull { it.npub == npub }
            if (account != null) {
                switchUserSync(account, routeBuilder)
                return true
            }
        }
        return false
    }

    private suspend fun switchUserSync(
        accountInfo: AccountInfo,
        routeBuilder: ((account: Account) -> Route?)? = null,
    ) {
        // Whatever the previous user had running (an audio room, a call) belongs to them, so it ends
        // before the swap. This is the real "account switch" hook; the Activity being destroyed is not.
        hooks.onSessionEnding()
        localPreferences.switchToAccount(accountInfo)
        loginWithDefaultAccount(routeBuilder)
    }

    fun currentAccountNPub() =
        when (val state = _accountContent.value) {
            is AccountState.LoggedIn -> {
                state.account.signer.pubKey
                    .hexToByteArray()
                    .toNpub()
            }

            else -> {
                null
            }
        }

    /**
     * NIP-46 `logout` to the remote signer of the account being removed, if it has one. Launched
     * on its own: the account's deletion does not wait for a signer that may never answer.
     */
    private suspend fun logOutOfRemoteSigner(
        hex: HexKey,
        npub: String,
    ) {
        // The loaded account's settings first, on screen or not: a relay switch may not be saved yet.
        val settings =
            accountsCache.loadedAccount(hex)?.settings
                ?: try {
                    localPreferences.loadAccountConfigFromEncryptedStorage(npub)
                } catch (e: Exception) {
                    if (e is CancellationException) throw e
                    Log.w("AccountSessionManager", "Could not read the account's remote signer for logout", e)
                    null
                }
        if (settings == null || !settings.usesRemoteSigner()) return
        val bunkerUri = settings.remoteSignerBunkerUri ?: return
        val transportKey = settings.remoteSignerTransportKey ?: return
        val relays = NostrConnectURI.parseBunker(bunkerUri)?.relays ?: emptySet()

        scope.launch(Dispatchers.IO) {
            withSignerRelays(relays) {
                RemoteSignerLogoutUseCase.execute(bunkerUri, transportKey, clientBuilder())
            }
        }
    }

    fun logOff(accountInfo: AccountInfo) {
        scope.launch(Dispatchers.IO) {
            val hex = decodePublicKeyAsHexOrNull(accountInfo.npub)
            if (hex == null) {
                Log.e("Logoff", "Cannot decode npub for account being logged off; aborting cleanup")
                return@launch
            }
            // Runs before anything else, whether or not this is the account currently on screen: the
            // platform may keep the account's contacts outside our own storage (Android's launcher
            // shortcuts).
            hooks.onAccountRemoving(accountInfo.npub)

            // Read before the settings are deleted: the logout needs the address and transport key.
            logOutOfRemoteSigner(hex, accountInfo.npub)

            if (accountInfo.npub == currentAccountNPub()) {
                // End what the account had running before its state is torn down.
                hooks.onSessionEnding()
                // log off and relogin with the 0 account
                // Cancels the account's scope first: its debounced settings save, landing after the
                // delete, would write the account (and its key) back.
                accountsCache.removeAccount(hex)
                localPreferences.deleteAccount(accountInfo)
                accountsCache.deleteAccountFiles(hex)
                hooks.onAccountRemoved(hex)
                loginWithDefaultAccount()
            } else {
                // delete without switching logins
                // Cancels the account's scope first: its debounced settings save, landing after the
                // delete, would write the account (and its key) back.
                accountsCache.removeAccount(hex)
                localPreferences.deleteAccount(accountInfo)
                accountsCache.deleteAccountFiles(hex)
                hooks.onAccountRemoved(hex)
            }
        }
    }
}
