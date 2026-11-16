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
package com.vitorpamplona.amethyst.commons.napplet

import com.vitorpamplona.amethyst.commons.browser.BrowserIconRegistry
import com.vitorpamplona.amethyst.commons.connectedApps.signers.AppConnectResult
import com.vitorpamplona.amethyst.commons.connectedApps.signers.NostrConnectPrompt
import com.vitorpamplona.amethyst.commons.connectedApps.signers.NostrSignerConsentPrompt
import com.vitorpamplona.amethyst.commons.connectedApps.signers.NostrSignerPermissionLedger
import com.vitorpamplona.amethyst.commons.connectedApps.signers.SignerOpGrant
import com.vitorpamplona.amethyst.commons.model.Account
import com.vitorpamplona.amethyst.commons.napplet.gateways.AccountIdentityReader
import com.vitorpamplona.amethyst.commons.napplet.gateways.AccountNappletOutboxGateway
import com.vitorpamplona.amethyst.commons.napplet.gateways.AccountNappletRelayGateway
import com.vitorpamplona.amethyst.commons.napplet.gateways.AccountNappletWalletGateway
import com.vitorpamplona.amethyst.commons.napplet.gateways.NappletResourceFetcher
import com.vitorpamplona.amethyst.commons.napplet.permissions.GrantState
import com.vitorpamplona.amethyst.commons.napplet.permissions.NappletPermissionLedger
import com.vitorpamplona.amethyst.commons.napplet.ui.NappletConsentSummary
import com.vitorpamplona.amethyst.commons.napplet.ui.buildConnectInfo
import com.vitorpamplona.amethyst.commons.napplet.ui.buildSignerConsentInfo
import com.vitorpamplona.quartz.nip89AppHandlers.clientTag.withoutClientTag
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.OkHttpClient
import kotlin.time.Duration.Companion.seconds

/**
 * How long any question waits on the user before it is refused. The broker holds a consent lock while
 * a prompt waits, so an abandoned one (a dialog left behind when the user went elsewhere) would
 * otherwise block every later request of its kind from every app. The NIP-46 path uses the same bound.
 */
private val CONSENT_TIMEOUT = 120.seconds

/**
 * The [NappletBroker] for a logged-in [account], wired the same way on every platform: the
 * account's relays, wallet, identity reads and resource fetches; its signer without Amethyst's client
 * tag; and the consent questions worded here and asked through [prompts]. What differs per platform
 * comes in: the [theme], and the [upload] and [notify] domains a host offers (a host without them
 * leaves them out of `window.napplet`).
 *
 * [useTor] says whether an app's resource fetches go through Tor; [httpClient] gives the client for
 * either route.
 */
fun buildAccountNappletBroker(
    account: Account,
    browserIcons: BrowserIconRegistry,
    ledger: NappletPermissionLedger,
    signerLedger: NostrSignerPermissionLedger?,
    storage: NappletStorage,
    httpClient: (useProxy: Boolean) -> OkHttpClient,
    useTor: suspend (coordinate: String) -> Boolean,
    prompts: NappletPrompts,
    theme: NappletThemeGateway,
    upload: NappletUploadGateway? = null,
    notify: NappletNotifyGateway? = null,
): NappletBroker {
    // Takes the account so the consent dialog can diff a proposed replaceable list (follows, relays,
    // mutes) against the copy already cached, and say what actually changes.
    val summary = NappletConsentSummary(browserIcons, account)
    val resources = NappletResourceFetcher(account, httpClient, useTor)
    val identity = AccountIdentityReader(account)
    val recentEncryptions = NappletRecentEncryptions()

    return NappletBroker(
        // Everything the broker signs belongs to the guest (a napplet, an nSite, or a web app calling
        // NIP-07), never to Amethyst, so our client tag has no business on it. It would also corrupt
        // the template a NIP-07 caller re-checks the returned event against.
        signer = account.signer.withoutClientTag(),
        ledger = ledger,
        consentPrompt =
            NappletConsentPrompt { app, capability, request ->
                withTimeoutOrNull(CONSENT_TIMEOUT) { prompts.capability(app, summary.info(app, capability, request)) } ?: GrantState.ASK
            },
        relay = AccountNappletRelayGateway(account),
        storage = storage,
        wallet = AccountNappletWalletGateway(account),
        resource = NappletResourceGateway { url, coordinate -> resources.fetch(url, coordinate) },
        upload = upload,
        identityReads = NappletIdentityGateway { method, argument -> identity.read(method, argument) },
        theme = theme,
        notify = notify,
        signerLedger = signerLedger,
        nostrConnectPrompt =
            NostrConnectPrompt { app, declared ->
                withTimeoutOrNull(CONSENT_TIMEOUT) { prompts.connect(app, buildConnectInfo(browserIcons, app, declared, account)) } ?: AppConnectResult.Cancelled
            },
        signerConsentPrompt =
            NostrSignerConsentPrompt { app, op, request ->
                withTimeoutOrNull(CONSENT_TIMEOUT) {
                    prompts.signerOp(app, buildSignerConsentInfo(browserIcons, app, op, request, account, recentEncryptions))
                } ?: SignerOpGrant.DenyOnce
            },
        recentEncryptions = recentEncryptions,
        outbox = AccountNappletOutboxGateway(account),
        common = NappletCommonGateway { account.kind3FollowList.getFollowListEvent() },
    )
}
