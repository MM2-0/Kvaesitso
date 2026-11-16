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
package com.vitorpamplona.amethyst.commons.ui.connectedApps.consent

import com.vitorpamplona.amethyst.commons.connectedApps.consent.counterpartyLabel
import com.vitorpamplona.amethyst.commons.connectedApps.signers.NostrSignerOp
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.napplet_op_blossom
import com.vitorpamplona.amethyst.commons.resources.napplet_op_decrypt
import com.vitorpamplona.amethyst.commons.resources.napplet_op_decrypt_from
import com.vitorpamplona.amethyst.commons.resources.napplet_op_encrypt
import com.vitorpamplona.amethyst.commons.resources.napplet_op_sign_in
import com.vitorpamplona.amethyst.commons.resources.napplet_op_sign_kind_named
import com.vitorpamplona.amethyst.commons.ui.loadStringRes
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.relays.kindNameFor
import com.vitorpamplona.quartz.nip42RelayAuth.RelayAuthEvent
import com.vitorpamplona.quartz.nipB7Blossom.BlossomAuthorizationEvent

/** Human-readable label for a [NostrSignerOp]. */
suspend fun NostrSignerOp.label(): String =
    when (this) {
        is NostrSignerOp.SignKind ->
            if (kind == RelayAuthEvent.KIND) {
                // A login (to a relay or an app server; the stored grant covers both) is not "signing" anything the
                // user would recognise; say what it does.
                loadStringRes(Res.string.napplet_op_sign_in)
            } else if (kind == BlossomAuthorizationEvent.KIND) {
                // One grant covers every media-server verb (upload, delete, list), so name the server use.
                loadStringRes(Res.string.napplet_op_blossom)
            } else {
                loadStringRes(Res.string.napplet_op_sign_kind_named, kindNameFor(kind), kind)
            }
        NostrSignerOp.Encrypt -> loadStringRes(Res.string.napplet_op_encrypt)
        NostrSignerOp.Decrypt -> loadStringRes(Res.string.napplet_op_decrypt)
        is NostrSignerOp.DecryptFrom -> loadStringRes(Res.string.napplet_op_decrypt_from, counterpartyLabel(counterparty))
    }
