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
package com.vitorpamplona.amethyst.commons.relayClient.notify

import androidx.collection.LruCache
import com.vitorpamplona.amethyst.commons.model.Account
import com.vitorpamplona.amethyst.commons.relayClient.notify.NotifyRequestsCache
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.relay.client.INostrClient
import com.vitorpamplona.quartz.nip01Core.relay.client.listeners.RelayConnectionListener
import com.vitorpamplona.quartz.nip01Core.relay.client.single.IRelayClient
import com.vitorpamplona.quartz.nip01Core.relay.commands.toClient.Message
import com.vitorpamplona.quartz.nip01Core.relay.commands.toClient.NotifyMessage
import com.vitorpamplona.quartz.nip01Core.relay.commands.toClient.OkMessage
import com.vitorpamplona.quartz.nip01Core.relay.commands.toRelay.AuthCmd
import com.vitorpamplona.quartz.nip01Core.relay.commands.toRelay.Command
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.utils.Log
import java.util.concurrent.ConcurrentHashMap

/**
 * Routes relay `NOTIFY` payment prompts to the account they actually concern.
 *
 * A relay's NOTIFY does not reliably name a pubkey, so we can't parse the account out of the
 * message. Instead we correlate it with the AUTH that triggered it: a paid relay answers an
 * unauthorized AUTH with `OK <authEventId> false …` immediately followed by the NOTIFY. We *signed*
 * that auth event, so [AuthCmd.event] tells us which account's key it was. We remember that per auth
 * event, resolve the failing `OK` back to the signer, and drop the NOTIFY into THAT account's own
 * [NotifyRequestsCache] ([Account.relayNotifications]).
 *
 * The cache is per account (not a process-wide singleton), so a prompt for account A can never
 * surface under account B — and an unattributable NOTIFY is dropped rather than shown to the wrong
 * account. This is the fix for the stale-global-cache leak where switching accounts re-surfaced
 * another account's inbox.nostr.wine prompt.
 */
class NotifyCoordinator(
    private val client: INostrClient,
    private val accountForPubkey: (HexKey) -> Account?,
) {
    companion object {
        const val TAG = "NotifyCoordinator"
        private const val AUTH_EVENT_CACHE = 256
    }

    // authEventId -> the pubkey we signed it with. Bounded: only a handful of relays re-auth.
    private val signerOfAuthEvent = LruCache<HexKey, HexKey>(AUTH_EVENT_CACHE)

    // relay -> pubkey of the auth the relay most recently rejected there (the one it will bill).
    private val billedPubkeyAt = ConcurrentHashMap<NormalizedRelayUrl, HexKey>()

    private val listener =
        object : RelayConnectionListener {
            override fun onSent(
                relay: IRelayClient,
                cmdStr: String,
                cmd: Command,
                success: Boolean,
            ) {
                if (cmd is AuthCmd) {
                    signerOfAuthEvent.put(cmd.event.id, cmd.event.pubKey)
                }
            }

            override suspend fun onIncomingMessage(
                relay: IRelayClient,
                msgStr: String,
                msg: Message,
            ) {
                when (msg) {
                    is OkMessage ->
                        if (!msg.success) {
                            signerOfAuthEvent.get(msg.eventId)?.let { billedPubkeyAt[relay.url] = it }
                        }
                    is NotifyMessage -> route(relay.url, msg.message)
                    else -> {}
                }
            }
        }

    private fun route(
        relay: NormalizedRelayUrl,
        message: String,
    ) {
        // Consume the correlation so a later, unrelated NOTIFY can't reuse a stale attribution.
        // An unattributable NOTIFY (none of our auths were rejected here) is dropped rather than
        // risk surfacing it under the wrong account.
        val account = billedPubkeyAt.remove(relay)?.let(accountForPubkey) ?: return

        // A relay the user has already blocked doesn't get to keep prompting. The pool drops the
        // socket once the subscriptions that wanted this relay are recomputed, but frames already
        // in flight can still arrive in that window — and the whole point of the dialog's "Block
        // Relay" button is that the dialog stops coming back.
        if (relay in account.blockedRelayList.flow.value) return

        account.relayNotifications.addPaymentRequestIfNew(message, relay)
    }

    init {
        Log.d(TAG, "Init, Subscribe")
        client.addConnectionListener(listener)
    }

    fun destroy() {
        Log.d(TAG, "Destroy, Unsubscribe")
        client.removeConnectionListener(listener)
    }
}
