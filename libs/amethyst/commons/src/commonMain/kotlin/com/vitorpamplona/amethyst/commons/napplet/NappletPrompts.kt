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

import com.vitorpamplona.amethyst.commons.connectedApps.consent.ConsentQueue
import com.vitorpamplona.amethyst.commons.connectedApps.consent.SignerConnectInfo
import com.vitorpamplona.amethyst.commons.connectedApps.consent.SignerConsentInfo
import com.vitorpamplona.amethyst.commons.connectedApps.signers.AppConnectResult
import com.vitorpamplona.amethyst.commons.connectedApps.signers.SignerOpGrant
import com.vitorpamplona.amethyst.commons.napplet.permissions.GrantState

/**
 * How a host asks its user about one napplet, nSite or web app, given the question already worded.
 * The broker's prompts are built on it (see `buildAccountNappletBroker`); each answer fails closed
 * when the user never gives one.
 */
interface NappletPrompts {
    /** May [app] use a capability (NIP-5D, and NIP-07's identity and relays)? */
    suspend fun capability(
        app: NappletIdentity,
        info: NappletConsentInfo,
    ): GrantState

    /** A site's first NIP-07 call: how much to trust it. */
    suspend fun connect(
        app: NappletIdentity,
        info: SignerConnectInfo,
    ): AppConnectResult

    /** One signature, encryption or decryption. */
    suspend fun signerOp(
        app: NappletIdentity,
        info: SignerConsentInfo,
    ): SignerOpGrant
}

/**
 * [NappletPrompts] for a host that shows each app's questions beside the app itself (the desktop's
 * windows): every question waits in a queue under its app's coordinate, the app's window shows the
 * oldest across [capabilities], [connects] and [signerOps] (by [ConsentQueue.Request.order]), and
 * [dismissApp] answers "no" for whatever an app left behind when it closed.
 */
class NappletPromptQueues : NappletPrompts {
    val capabilities = ConsentQueue<NappletConsentInfo, GrantState>(unanswered = GrantState.ASK)
    val connects = ConsentQueue<SignerConnectInfo, AppConnectResult>(unanswered = AppConnectResult.Cancelled)
    val signerOps = ConsentQueue<SignerConsentInfo, SignerOpGrant>(unanswered = SignerOpGrant.DenyOnce)

    override suspend fun capability(
        app: NappletIdentity,
        info: NappletConsentInfo,
    ) = capabilities.ask(info, app.coordinate)

    override suspend fun connect(
        app: NappletIdentity,
        info: SignerConnectInfo,
    ) = connects.ask(info, app.coordinate)

    override suspend fun signerOp(
        app: NappletIdentity,
        info: SignerConsentInfo,
    ) = signerOps.ask(info, app.coordinate)

    fun dismissApp(coordinate: String) {
        capabilities.dismissApp(coordinate)
        connects.dismissApp(coordinate)
        signerOps.dismissApp(coordinate)
    }
}
