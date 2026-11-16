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

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class NappletActingRequestsTest {
    @Test
    fun actingRequestsAreHeld() {
        listOf(
            "relay.publish",
            "relay.publishEncrypted",
            "value.payInvoice",
            "upload.upload",
            "notify.create",
            "nostr.signEvent",
            "nostr.nip44Encrypt",
            "nostr.nip44Decrypt",
        ).forEach { assertTrue(NappletActingRequests.actsForUser(it), it) }
    }

    @Test
    fun readsFlow() {
        listOf("identity.getPublicKey", "identity.getRelays", "relay.query", "relay.subscribe", "relay.close", "storage.get", "resource.bytes", "theme.get", "inc.emit", "inc.channel.emit", "inc.channel.close", null)
            .forEach { assertFalse(NappletActingRequests.actsForUser(it), it.toString()) }
    }
}
