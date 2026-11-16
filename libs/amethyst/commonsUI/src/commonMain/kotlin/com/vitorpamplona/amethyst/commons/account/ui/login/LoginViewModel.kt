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
package com.vitorpamplona.amethyst.commons.account.ui.login

import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.input.TextFieldValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vitorpamplona.amethyst.commons.account.AccountSessionManager
import com.vitorpamplona.amethyst.commons.account.EMAIL_PATTERN
import com.vitorpamplona.amethyst.commons.account.ui.login.LoginErrorManager
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.invalid_key
import com.vitorpamplona.amethyst.commons.resources.invalid_key_with_message
import com.vitorpamplona.amethyst.commons.resources.key_is_required
import com.vitorpamplona.amethyst.commons.resources.login_bunker_not_supported
import com.vitorpamplona.amethyst.commons.resources.login_nostrconnect_not_supported
import com.vitorpamplona.amethyst.commons.resources.password_is_required
import com.vitorpamplona.amethyst.commons.resources.remote_signer_login_failed
import com.vitorpamplona.amethyst.commons.resources.sign_request_rejected_description
import com.vitorpamplona.amethyst.commons.tor.TorSettingsFlow
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.RelayUrlNormalizer
import com.vitorpamplona.quartz.nip19Bech32.Bech32Transcription
import com.vitorpamplona.quartz.nip19Bech32.bech32.bechToBytes
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlin.concurrent.Volatile

/**
 * The login form's state. [requiresTermsAcceptance] is the build's: where it holds, a first login
 * stays disabled until the user accepts the terms of use.
 */
@Stable
class LoginViewModel(
    private val requiresTermsAcceptance: Boolean = false,
) : ViewModel() {
    lateinit var accountSessionManager: AccountSessionManager
    lateinit var torSettings: TorSettingsFlow

    val errorManager = LoginErrorManager()

    /** A signer app's (Amber's) refusal or failure, shown under its button rather than the key field. */
    val signerErrorManager = LoginErrorManager()

    /** A missing password, under the password field rather than the key field above it. */
    val passwordErrorManager = LoginErrorManager()

    /** Why the last `nostrconnect://` login failed, under the remote-signer button that started it. */
    val remoteSignerErrorManager = LoginErrorManager()

    var key by mutableStateOf(TextFieldValue(""))
    var acceptedTerms by mutableStateOf(false)
    var termsAcceptanceIsRequiredError by mutableStateOf(false)

    var offerTemporaryLogin by mutableStateOf(false)
    var isTemporary by mutableStateOf(false)

    /** Keep the private key on this device (desktop: OS keyring or password file); off asks at each launch. */
    var rememberKey by mutableStateOf(true)

    var processingLogin by mutableStateOf(false)

    var password by mutableStateOf(TextFieldValue(""))
    val needsPassword by derivedStateOf {
        Bech32Transcription.normalize(key.text).startsWith("ncryptsec1")
    }

    /**
     * The key field holds a whole, checksummed ncryptsec. [needsPassword] turns true at the
     * first "ncryptsec1", which is what shows the password field; moving focus there has to
     * wait for this, or a key typed by hand loses focus after its tenth character.
     */
    val isCompleteNcryptsec by derivedStateOf {
        needsPassword &&
            try {
                Bech32Transcription.normalize(key.text).bechToBytes("ncryptsec").size == NCRYPTSEC_PAYLOAD_SIZE
            } catch (e: Exception) {
                false
            }
    }

    /**
     * Whether what is typed could be a private key (nsec, ncryptsec, hex, mnemonic), or nothing
     * yet: only then is there a key to remember. An npub, nprofile, bunker:// or NIP-05 has none.
     */
    val keyMayBePrivate by derivedStateOf {
        val typed = Bech32Transcription.normalize(key.text).lowercase()
        typed.isEmpty() ||
            !(
                typed.startsWith("npub1") ||
                    typed.startsWith("nprofile1") ||
                    typed.startsWith("bunker:") ||
                    typed.startsWith("nostrconnect:") ||
                    EMAIL_PATTERN.matches(typed)
            )
    }

    var isFirstLogin by mutableStateOf(false)

    /**
     * Whether this build signs in through NIP-46 remote signers (`bunker://`, `nostrconnect://`).
     * Desktop and Android do; a platform without it rejects a pasted `bunker://` with a reason.
     */
    var remoteSignerLoginSupported = false

    /** Whether the `nostrconnect://` dialog is up: from the request until the login ends. */
    var nostrConnectPending by mutableStateOf(false)
        private set

    /** The `nostrconnect://` address a remote signer should scan; null while it is being made. */
    var nostrConnectUri by mutableStateOf<String?>(null)
        private set

    /** The relays the `nostrconnect://` address names. The user can swap in one of their inbox relays. */
    var nostrConnectRelays by mutableStateOf(DEFAULT_NOSTR_CONNECT_RELAYS)
        private set

    val usesDefaultNostrConnectRelays by derivedStateOf { nostrConnectRelays == DEFAULT_NOSTR_CONNECT_RELAYS }

    // Read from the attempt's own thread (onUri, finally) as well as the main one.
    @Volatile
    private var nostrConnectJob: Job? = null

    fun init(accountSessionManager: AccountSessionManager) {
        this.accountSessionManager = accountSessionManager
    }

    fun init(torSettings: TorSettingsFlow) {
        this.torSettings = torSettings
    }

    fun load(
        isFirstLogin: Boolean,
        newAccountKey: String?,
    ) {
        clear()
        this.isFirstLogin = isFirstLogin
        acceptedTerms = !isFirstLogin || !requiresTermsAcceptance
        if (newAccountKey != null) {
            key = TextFieldValue(newAccountKey)
        }
    }

    fun clear() {
        key = TextFieldValue("")
        password = TextFieldValue("")

        errorManager.clearErrors()
        signerErrorManager.clearErrors()
        remoteSignerErrorManager.clearErrors()
        passwordErrorManager.clearErrors()
        acceptedTerms = !requiresTermsAcceptance
        processingLogin = false
        isTemporary = false
        offerTemporaryLogin = false
        isFirstLogin = false
    }

    fun updateKey(
        value: TextFieldValue,
        throughQR: Boolean,
    ) {
        key = value
        if (throughQR) {
            offerTemporaryLogin = true
            isTemporary = true
        }
        errorManager.clearErrors()
        // Also a signer's answer landing here: a new attempt, so the last one's error goes.
        signerErrorManager.clearErrors()
        remoteSignerErrorManager.clearErrors()
        passwordErrorManager.clearErrors()
    }

    fun updatePassword(newPassword: TextFieldValue) {
        password = newPassword
        errorManager.clearErrors()
        passwordErrorManager.clearErrors()
    }

    fun updateAcceptedTerms(newAcceptedTerms: Boolean) {
        acceptedTerms = newAcceptedTerms
        if (newAcceptedTerms) {
            termsAcceptanceIsRequiredError = false
        }

        errorManager.clearErrors()
        signerErrorManager.clearErrors()
        remoteSignerErrorManager.clearErrors()
        passwordErrorManager.clearErrors()
    }

    fun updateOfferTemporaryLogin(tempLogin: Boolean) {
        offerTemporaryLogin = tempLogin
    }

    fun checkCanLogin(): Boolean {
        if (!acceptedTerms) {
            termsAcceptanceIsRequiredError = true
            return false
        }

        if (key.text.isBlank()) {
            errorManager.error(Res.string.key_is_required)
            return false
        }

        // Caught here rather than left to the key parser. Neither of these is a key, so both used
        // to fall through to the hex branch and come back as "Invalid key: ... Invalid hex
        // bunker://...", which reads as "you mistyped it" for a string the user pasted correctly.
        // A nostrconnect:// offer is never a way in: it is an app asking a signer to pair with it.
        val trimmedKey = key.text.trim()
        if (!remoteSignerLoginSupported && trimmedKey.startsWith("bunker:", ignoreCase = true)) {
            errorManager.error(Res.string.login_bunker_not_supported)
            return false
        }
        if (trimmedKey.startsWith("nostrconnect:", ignoreCase = true)) {
            errorManager.error(Res.string.login_nostrconnect_not_supported)
            return false
        }

        if (needsPassword && password.text.isBlank()) {
            passwordErrorManager.error(Res.string.password_is_required)
            return false
        }

        return true
    }

    fun login() {
        // The key field's Enter reaches here too, not just the button: one login at a time.
        if (processingLogin) return
        if (checkCanLogin()) {
            processingLogin = true
            accountSessionManager.login(
                key = key.text,
                password = password.text,
                transientAccount = isTemporary,
                rememberKey = rememberKey,
                allowRemoteSigner = remoteSignerLoginSupported,
            ) {
                processingLogin = false
                if (it != null) {
                    errorManager.error(Res.string.invalid_key_with_message, it)
                } else {
                    errorManager.error(Res.string.invalid_key)
                }
            }
        }
    }

    fun loginWithExternalSigner(packageName: String) {
        if (processingLogin) return
        if (checkCanLogin()) {
            processingLogin = true
            accountSessionManager.login(
                key = key.text,
                transientAccount = isTemporary,
                loginWithExternalSigner = true,
                packageName = packageName,
            ) {
                processingLogin = false
                signerErrorManager.error(Res.string.sign_request_rejected_description)
            }
        }
    }

    /** Shows a `nostrconnect://` address and logs in once a remote signer answers it. */
    fun loginWithNostrConnect() {
        if (!acceptedTerms) {
            termsAcceptanceIsRequiredError = true
            return
        }
        if (nostrConnectJob?.isActive == true) return
        // A key or bunker:// login is still running: its end would clear processingLogin under
        // this dialog, and a second login could then start beside this one.
        if (processingLogin) return

        remoteSignerErrorManager.clearErrors()
        startNostrConnect()
    }

    private fun startNostrConnect() {
        processingLogin = true
        nostrConnectPending = true
        nostrConnectUri = null
        val relays = nostrConnectRelays
        // Lazy, so the attempt is nostrConnectJob before any of it runs: started at once, it could
        // reach onUri (on another thread) before the assignment, drop its own code and, failing
        // early, skip the reset in finally and leave the spinner on.
        val job =
            viewModelScope.launch(start = CoroutineStart.LAZY) {
                val thisJob = coroutineContext.job
                try {
                    accountSessionManager.loginWithNostrConnect(
                        relays = relays,
                        transientAccount = isTemporary,
                        // A relay change may have replaced this attempt before its code was ready.
                        onUri = { if (nostrConnectJob === thisJob) nostrConnectUri = it },
                    )
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    remoteSignerErrorManager.error(Res.string.remote_signer_login_failed, e.message ?: e::class.simpleName.orEmpty())
                } finally {
                    // A relay change replaced this attempt with a new one, which owns the dialog now.
                    if (nostrConnectJob === thisJob) {
                        nostrConnectUri = null
                        nostrConnectPending = false
                        processingLogin = false
                    }
                }
            }
        nostrConnectJob = job
        job.start()
    }

    fun cancelNostrConnect() {
        nostrConnectJob?.cancel()
    }

    /**
     * Names [input], one of the account's inbox relays, in the `nostrconnect://` address instead
     * of the defaults, for a signer that can't reach those. False when [input] is not a relay.
     */
    fun useNostrConnectRelay(input: String): Boolean {
        val relay = RelayUrlNormalizer.normalizeOrNull(input.trim()) ?: return false
        updateNostrConnectRelays(persistentListOf(relay.url))
        return true
    }

    fun useDefaultNostrConnectRelays() = updateNostrConnectRelays(DEFAULT_NOSTR_CONNECT_RELAYS)

    /** A pending connection restarts with a new address: the one on screen names the old relays. */
    private fun updateNostrConnectRelays(relays: ImmutableList<String>) {
        if (relays == nostrConnectRelays) return
        nostrConnectRelays = relays
        if (nostrConnectJob?.isActive == true) {
            val previous = nostrConnectJob
            startNostrConnect()
            previous?.cancel()
        }
    }

    companion object {
        /**
         * Where the `nostrconnect://` handshake happens; the signer replies on the same relays.
         * Several, so one relay going down doesn't break the login: the single default relay used
         * before stopped answering (2026-10). All three are NIP-46 relays that Amber and Opal
         * also use by default.
         */
        val DEFAULT_NOSTR_CONNECT_RELAYS: ImmutableList<String> =
            persistentListOf("wss://relay.nip46.com", "wss://nrs.primal.net", "wss://bucket.coracle.social")

        // version + log_n + salt(16) + nonce(24) + key security + ciphertext(48), per NIP-49.
        private const val NCRYPTSEC_PAYLOAD_SIZE = 91
    }
}
