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
package com.vitorpamplona.amethyst.commons.service.nwc

import com.vitorpamplona.amethyst.commons.notices.NwcFailure
import com.vitorpamplona.amethyst.commons.notices.nwcFailure
import com.vitorpamplona.amethyst.commons.notices.ui.loadText
import com.vitorpamplona.amethyst.commons.notices.ui.txt
import com.vitorpamplona.quartz.nip47WalletConnect.rpc.Response

// User-facing text for the ways a NIP-47 request can fail to settle. Every payment
// surface needs the same sentences, and a surface that skips one shows the user
// nothing at all — which is the failure mode these exist to prevent.

/** Shown when no kind-23195 reply arrived before the client gave up waiting. */
suspend fun nwcTimeoutMessage(): String = NwcFailure.TimedOut.txt().loadText()

/**
 * Why a NIP-47 request did not go through, or null when the wallet settled it. The
 * classification is [nwcFailure]'s and the wording [NwcFailure.txt]'s, so this matches the
 * zap and V4V rails, which report the same failures as notices.
 */
suspend fun Response?.nwcFailureDetail(): String? = nwcFailure()?.txt()?.loadText()
