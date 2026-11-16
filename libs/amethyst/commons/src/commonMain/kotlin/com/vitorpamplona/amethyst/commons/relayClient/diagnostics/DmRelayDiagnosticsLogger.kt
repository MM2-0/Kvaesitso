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
package com.vitorpamplona.amethyst.commons.relayClient.diagnostics

import com.vitorpamplona.quartz.nip01Core.relay.client.INostrClient
import com.vitorpamplona.quartz.nip01Core.relay.client.listeners.RelayConnectionListener
import com.vitorpamplona.quartz.nip01Core.relay.client.single.IRelayClient
import com.vitorpamplona.quartz.nip01Core.relay.commands.toClient.AuthMessage
import com.vitorpamplona.quartz.nip01Core.relay.commands.toClient.ClosedMessage
import com.vitorpamplona.quartz.nip01Core.relay.commands.toClient.Message
import com.vitorpamplona.quartz.nip01Core.relay.commands.toClient.NoticeMessage
import com.vitorpamplona.quartz.nip01Core.relay.commands.toClient.OkMessage
import com.vitorpamplona.quartz.nip01Core.relay.commands.toRelay.AuthCmd
import com.vitorpamplona.quartz.nip01Core.relay.commands.toRelay.Command
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip04Dm.messages.EncryptedDmEvent
import com.vitorpamplona.quartz.nip59Giftwrap.wraps.EphemeralGiftWrapEvent
import com.vitorpamplona.quartz.nip59Giftwrap.wraps.GiftWrapEvent
import com.vitorpamplona.quartz.utils.Log
import com.vitorpamplona.quartz.utils.TimeUtils

/**
 * Diagnostic connection listener for the DM loading path — both NIP-17 gift wraps
 * (kind 1059 / 21059) and NIP-04 legacy DMs (kind 4).
 *
 * It folds the per-relay timeline — REQ sent, connect/disconnect, NOTICE / CLOSED
 * rejection and OK failures — into the single `DMPagination` log tag with an
 * elapsed-time prefix, so a slow cold boot can be attributed (connection? relay
 * response?) and a silent failure to load (e.g. a relay answering CLOSED
 * "auth-required" / "restricted") becomes visible. Per-event lines are omitted to
 * keep the trail readable, but the NIP-42 AUTH handshake (challenge in, AUTH out,
 * accept/reject) IS logged: an auth-walled relay's whole load hinges on whether
 * that round-trip closes and a re-REQ follows, so it has to be attributable here.
 *
 * The connection listener fires for EVERY relay the app talks to (hundreds, under
 * the outbox model). To keep this readable we only log relays that are part of the
 * DM path: a relay is "learned" the first time we send it a kind:1059/21059/4
 * REQ, and only those relays' connect/auth/notice lines are emitted thereafter.
 */
class DmRelayDiagnosticsLogger(
    val client: INostrClient,
) {
    private val startMs = TimeUtils.nowMillis()

    private fun at() = TimeUtils.nowMillis() - startMs

    // Subscription ids whose REQ carried a DM kind, so we can attribute their EOSE/CLOSED.
    private val dmSubIds = mutableSetOf<String>()

    // Relays we've seen on the DM path, so connect/auth/notice noise from the
    // hundreds of unrelated follow/outbox relays is filtered out.
    private val dmPathRelays = mutableSetOf<NormalizedRelayUrl>()

    // Ids of the AUTH events we've sent to DM relays, so the relay's OK can be tagged "AUTH accepted /
    // REJECTED" (the OK that decides whether the post-auth re-REQ will actually be served) instead of
    // being lost among ordinary event OKs.
    private val authEventIds = mutableSetOf<String>()

    private fun isDmRelay(relay: IRelayClient) = relay.url in dmPathRelays

    private val listener =
        object : RelayConnectionListener {
            override fun onConnecting(relay: IRelayClient) {
                if (isDmRelay(relay)) Log.d(TAG) { "[+${at()}ms] connecting ${relay.url.url}" }
            }

            override fun onConnected(
                relay: IRelayClient,
                pingMillis: Int,
                compressed: Boolean,
            ) {
                if (isDmRelay(relay)) {
                    Log.d(TAG) { "[+${at()}ms] connected ${relay.url.url} (ping ${pingMillis}ms${if (compressed) ", compressed" else ""})" }
                }
            }

            override fun onSent(
                relay: IRelayClient,
                cmdStr: String,
                cmd: Command,
                success: Boolean,
            ) {
                if (cmd is AuthCmd) {
                    // Our reply to a relay's NIP-42 challenge. Remember the id so the relay's OK can be
                    // tagged as the auth result below. Only for relays already on the DM path.
                    if (isDmRelay(relay)) {
                        authEventIds.add(cmd.event.id)
                        Log.d(TAG) { "[+${at()}ms] AUTH -> ${relay.url.url} success=$success (id ${cmd.event.id.take(8)})" }
                    }
                    return
                }
                if (!isDmReq(cmdStr)) return
                dmPathRelays.add(relay.url)
                reqSubId(cmdStr)?.let { dmSubIds.add(it) }
                Log.d(TAG) { "[+${at()}ms] REQ -> ${relay.url.url} success=$success ${cmdStr.take(400)}" }
            }

            override suspend fun onIncomingMessage(
                relay: IRelayClient,
                msgStr: String,
                msg: Message,
            ) {
                when (msg) {
                    is AuthMessage ->
                        // The relay's NIP-42 challenge. Without it (and the AUTH/OK that follow) an
                        // auth-walled relay can never serve, so it's the first link to look for.
                        if (isDmRelay(relay)) Log.d(TAG) { "[+${at()}ms] AUTH challenge <- ${relay.url.url} '${msg.challenge.take(40)}'" }

                    is NoticeMessage ->
                        if (isDmRelay(relay)) Log.d(TAG) { "[+${at()}ms] NOTICE <- ${relay.url.url} '${msg.message}'" }

                    is ClosedMessage ->
                        if (msg.subId in dmSubIds) {
                            Log.d(TAG) { "[+${at()}ms] CLOSED <- ${relay.url.url} sub=${msg.subId} reason='${msg.message}'" }
                        }

                    is OkMessage ->
                        if (msg.eventId in authEventIds) {
                            // The auth result: "accepted" means a syncFilters re-REQ should now be served;
                            // "REJECTED" means this relay will keep refusing and never serve our DMs.
                            Log.d(TAG) { "[+${at()}ms] AUTH ${if (msg.success) "accepted" else "REJECTED"} <- ${relay.url.url} '${msg.message}'" }
                        } else if (!msg.success && isDmRelay(relay)) {
                            Log.d(TAG) { "[+${at()}ms] OK(fail) <- ${relay.url.url} '${msg.message}'" }
                        }

                    else -> {}
                }
            }

            override fun onDisconnected(relay: IRelayClient) {
                if (isDmRelay(relay)) Log.d(TAG) { "[+${at()}ms] disconnected ${relay.url.url}" }
            }

            override fun onCannotConnect(
                relay: IRelayClient,
                errorMessage: String,
            ) {
                if (isDmRelay(relay)) Log.d(TAG) { "[+${at()}ms] CANNOT CONNECT ${relay.url.url}: $errorMessage" }
            }
        }

    init {
        client.addConnectionListener(listener)
    }

    fun destroy() {
        client.removeConnectionListener(listener)
    }

    companion object {
        private const val TAG = "DMPagination"

        // The kinds a DM-path REQ carries: NIP-17 gift wraps (1059 + 21059) and NIP-04 legacy DMs
        // (4). Matched exactly against the filter's "kinds" array — never as a substring of the whole
        // command, since a pubkey hex or timestamp can incidentally contain "1059" or "4".
        private val DM_KINDS = setOf(GiftWrapEvent.KIND, EphemeralGiftWrapEvent.KIND, EncryptedDmEvent.KIND)

        private val KINDS_ARRAY = Regex("\"kinds\":\\[([0-9,\\s]*)]")

        // Extracts the subscription id from a `["REQ","<subId>",{...}]` command string.
        private val REQ_SUB_ID = Regex("^\\[\"REQ\",\"([^\"]+)\"")

        private fun reqSubId(cmdStr: String) = REQ_SUB_ID.find(cmdStr)?.groupValues?.get(1)

        /** True only when one of the REQ's `kinds` arrays actually contains a DM kind. */
        private fun isDmReq(cmdStr: String): Boolean =
            KINDS_ARRAY.findAll(cmdStr).any { match ->
                match.groupValues[1]
                    .split(',')
                    .any { it.trim().toIntOrNull() in DM_KINDS }
            }
    }
}
