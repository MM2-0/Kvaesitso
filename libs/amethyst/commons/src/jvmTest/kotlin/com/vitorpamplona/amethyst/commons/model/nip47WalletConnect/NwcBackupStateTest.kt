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
package com.vitorpamplona.amethyst.commons.model.nip47WalletConnect

import com.vitorpamplona.amethyst.commons.model.AccountSettings
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.toHexKey
import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import com.vitorpamplona.quartz.nip01Core.relay.client.EmptyNostrClient
import com.vitorpamplona.quartz.nip01Core.relay.client.INostrClient
import com.vitorpamplona.quartz.nip01Core.relay.client.listeners.RelayConnectionListener
import com.vitorpamplona.quartz.nip01Core.relay.client.reqs.SubscriptionListener
import com.vitorpamplona.quartz.nip01Core.relay.client.single.IRelayClient
import com.vitorpamplona.quartz.nip01Core.relay.commands.toClient.OkMessage
import com.vitorpamplona.quartz.nip01Core.relay.commands.toRelay.Command
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.RelayUrlNormalizer
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerInternal
import com.vitorpamplona.quartz.nip47WalletConnect.Nip47WalletConnect
import com.vitorpamplona.quartz.nip78AppData.AppSpecificDataEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The relay record behind the wallet screen's backup dot and "View backup relays":
 * a relay joins it only by confirming a publish and leaves it only by confirming the
 * deletion, so turning auto-backup off never hides where the backup still is.
 */
class NwcBackupStateTest {
    private val relayA = RelayUrlNormalizer.normalize("wss://a.example.com")
    private val relayB = RelayUrlNormalizer.normalize("wss://b.example.com")
    private val relayC = RelayUrlNormalizer.normalize("wss://c.example.com")

    private val wallet =
        NwcWalletEntryNorm(
            id = "wallet-1",
            name = "Lightning Wallet",
            uri =
                Nip47WalletConnect.Nip47URINorm(
                    pubKeyHex = KeyPair().pubKey.toHexKey(),
                    relayUri = RelayUrlNormalizer.normalize("wss://nwc.example.com"),
                    secret = KeyPair().privKey!!.toHexKey(),
                ),
        )

    private sealed interface Verdict {
        data object Accept : Verdict

        data class Reject(
            val message: String,
        ) : Verdict

        data object Silent : Verdict
    }

    private class FakeRelays(
        private val scope: CoroutineScope,
        val verdicts: MutableMap<NormalizedRelayUrl, Verdict>,
    ) : INostrClient by EmptyNostrClient() {
        private val listeners = mutableListOf<RelayConnectionListener>()
        val stored = mutableMapOf<NormalizedRelayUrl, MutableList<Event>>()
        val sentTo = mutableListOf<Set<NormalizedRelayUrl>>()

        val connected = MutableStateFlow<Set<NormalizedRelayUrl>>(emptySet())

        override fun connectedRelaysFlow() = connected

        override fun addConnectionListener(listener: RelayConnectionListener) {
            listeners += listener
        }

        override fun removeConnectionListener(listener: RelayConnectionListener) {
            listeners -= listener
        }

        override fun publish(
            event: Event,
            relayList: Set<NormalizedRelayUrl>,
        ) {
            sentTo += relayList
            relayList.forEach { url ->
                val ok =
                    when (val verdict = verdicts[url] ?: Verdict.Silent) {
                        Verdict.Accept -> {
                            stored.getOrPut(url) { mutableListOf() } += event
                            OkMessage.accepted(event.id)
                        }

                        is Verdict.Reject -> OkMessage(event.id, false, verdict.message)

                        Verdict.Silent -> null
                    }
                if (ok != null) {
                    scope.launch(Dispatchers.Unconfined) {
                        listeners.toList().forEach { it.onIncomingMessage(FakeRelay(url), "", ok) }
                    }
                }
            }
        }

        override fun subscribe(
            subId: String,
            filters: Map<NormalizedRelayUrl, List<Filter>>,
            listener: SubscriptionListener?,
        ) {
            val target = listener ?: return
            scope.launch(Dispatchers.Unconfined) {
                filters.forEach { (relay, list) ->
                    // A REQ into a still-handshaking socket gets no events and no EOSE.
                    if (relay !in connected.value) return@forEach
                    stored[relay]
                        .orEmpty()
                        .filter { event -> list.any { it.match(event) } }
                        .forEach { target.onEvent(it, false, relay, null) }
                    target.onEose(relay, null)
                }
            }
        }

        override fun unsubscribe(subId: String) = Unit
    }

    private class FakeRelay(
        override val url: NormalizedRelayUrl,
    ) : IRelayClient {
        override fun connect() = Unit

        override fun needsToReconnect() = false

        override fun connectAndSyncFiltersIfDisconnected(ignoreRetryDelays: Boolean) = Unit

        override fun isConnected() = true

        override fun disconnect() = Unit

        override fun sendIfConnected(cmd: Command) = Unit

        override fun sendOrConnectAndSync(cmd: Command) = Unit
    }

    private class World(
        val settings: AccountSettings,
        val relays: FakeRelays,
        val backup: NwcBackupState,
        val signer: NostrSignerInternal,
    ) {
        suspend fun plant(
            relay: NormalizedRelayUrl,
            dTag: String,
            plaintext: String?,
            createdAt: Long,
        ): Event {
            val content = plaintext?.let { signer.nip44Encrypt(it, signer.pubKey) } ?: ""
            val event =
                signer.sign(
                    AppSpecificDataEvent.build(dTag = dTag, description = content, createdAt = createdAt) {
                        if (plaintext == null) add(arrayOf("deleted", "true"))
                    },
                )
            relays.stored.getOrPut(relay) { mutableListOf() } += event
            return event
        }
    }

    private fun bareUri(lud16: String) = "nostr+walletconnect://${KeyPair().pubKey.toHexKey()}?relay=wss://nwc.example.com&secret=${KeyPair().privKey!!.toHexKey()}&lud16=$lud16"

    private fun TestScope.world(
        outbox: Set<NormalizedRelayUrl>,
        verdicts: Map<NormalizedRelayUrl, Verdict>,
        keyPair: KeyPair = KeyPair(),
        read: Set<NormalizedRelayUrl> = emptySet(),
    ): World {
        val settings = AccountSettings(keyPair)
        settings.nwcWallets.value = listOf(wallet)
        // publishBackup skips while the opt-in is off.
        settings.changeBackupNwcWallets(true)
        val relays = FakeRelays(backgroundScope, verdicts.toMutableMap())
        relays.connected.value = outbox + read
        val signer = NostrSignerInternal(keyPair)
        val backup = NwcBackupState(signer, relays, backgroundScope, settings, MutableStateFlow(outbox), MutableStateFlow(read))
        return World(settings, relays, backup, signer)
    }

    @Test
    fun aPublishRecordsTheRelaysThatConfirmedAndTheWalletsItHolds() =
        runTest {
            val w = world(outbox = setOf(relayA, relayB), verdicts = mapOf(relayA to Verdict.Accept, relayB to Verdict.Reject("blocked: no app data")))

            assertTrue(w.backup.publishNow())

            assertEquals(listOf(relayA.url), w.settings.nwcBackupRelays.value)
            assertEquals(setOf(wallet.id), w.settings.backedUpNwcWalletIds.value)
            assertTrue(w.backup.status.value is NwcBackupState.Status.Published)
        }

    @Test
    fun theBackupGoesOnlyToTheOutboxRelays() =
        runTest {
            val w = world(outbox = setOf(relayA), verdicts = mapOf(relayA to Verdict.Accept, relayB to Verdict.Accept))

            w.backup.publishNow()

            assertEquals(listOf(setOf(relayA)), w.relays.sentTo)
        }

    @Test
    fun aPublishNoRelayAcceptsFailsWithTheRelaysReasonAndRecordsNothing() =
        runTest {
            val w = world(outbox = setOf(relayA), verdicts = mapOf(relayA to Verdict.Reject("blocked: no app data")))

            assertFalse(w.backup.publishNow())

            assertTrue(
                w.settings.nwcBackupRelays.value
                    .isEmpty(),
            )
            assertTrue(
                w.settings.backedUpNwcWalletIds.value
                    .isEmpty(),
            )
            val failure = (w.backup.status.value as NwcBackupState.Status.Failed).failure
            assertEquals(NwcBackupState.Failure.Rejected("blocked: no app data"), failure)
        }

    @Test
    fun turningAutoBackupOffKeepsTheRelayRecord() =
        runTest {
            val w = world(outbox = setOf(relayA), verdicts = mapOf(relayA to Verdict.Accept))
            w.settings.changeBackupNwcWallets(true)
            w.backup.publishNow()

            w.settings.changeBackupNwcWallets(false)

            assertEquals(listOf(relayA.url), w.settings.nwcBackupRelays.value)
            assertEquals(setOf(wallet.id), w.settings.backedUpNwcWalletIds.value)
        }

    @Test
    fun aDeleteEveryRelayConfirmsClearsTheRecordAndTheWalletMarks() =
        runTest {
            val w = world(outbox = setOf(relayA, relayB), verdicts = mapOf(relayA to Verdict.Accept, relayB to Verdict.Accept))
            w.backup.publishNow()

            val outcome = w.backup.deleteBackup()

            assertEquals(NwcBackupState.DeleteOutcome.Deleted(2), outcome)
            assertTrue(
                w.settings.nwcBackupRelays.value
                    .isEmpty(),
            )
            assertTrue(
                w.settings.backedUpNwcWalletIds.value
                    .isEmpty(),
            )
        }

    @Test
    fun relaysThatDoNotConfirmTheDeleteStayListedWithTheWalletMarks() =
        runTest {
            val w = world(outbox = setOf(relayA, relayB, relayC), verdicts = mapOf(relayA to Verdict.Accept, relayB to Verdict.Accept, relayC to Verdict.Accept))
            w.backup.publishNow()
            w.relays.verdicts[relayB] = Verdict.Reject("blocked: deletes are disabled")
            w.relays.verdicts[relayC] = Verdict.Silent

            val outcome = w.backup.deleteBackup()

            assertEquals(NwcBackupState.DeleteOutcome.PartiallyDeleted(1, listOf(relayB.url, relayC.url)), outcome)
            assertEquals(listOf(relayB.url, relayC.url), w.settings.nwcBackupRelays.value)
            assertEquals(setOf(wallet.id), w.settings.backedUpNwcWalletIds.value)
        }

    @Test
    fun aDeleteNoRelayConfirmsKeepsEverything() =
        runTest {
            val w = world(outbox = setOf(relayA), verdicts = mapOf(relayA to Verdict.Accept))
            w.backup.publishNow()
            w.relays.verdicts[relayA] = Verdict.Reject("blocked: read only")

            val outcome = w.backup.deleteBackup()

            assertEquals(NwcBackupState.DeleteOutcome.Failed(NwcBackupState.Failure.Rejected("blocked: read only")), outcome)
            assertEquals(listOf(relayA.url), w.settings.nwcBackupRelays.value)
            assertEquals(setOf(wallet.id), w.settings.backedUpNwcWalletIds.value)
        }

    @Test
    fun aDeleteAlsoReachesRecordedRelaysNoLongerInTheOutbox() =
        runTest {
            val w = world(outbox = setOf(relayA), verdicts = mapOf(relayA to Verdict.Accept, relayB to Verdict.Accept))
            w.settings.updateNwcBackupRelays(listOf(relayB.url))

            val outcome = w.backup.deleteBackup()

            assertTrue(w.relays.sentTo.any { relayB in it && relayA in it })
            assertEquals(NwcBackupState.DeleteOutcome.Deleted(2), outcome)
            assertTrue(
                w.settings.nwcBackupRelays.value
                    .isEmpty(),
            )
        }

    @Test
    fun aReadOnlyAccountCannotDeleteAndPublishesNothing() =
        runTest {
            val readOnly = KeyPair(pubKey = KeyPair().pubKey)
            val w = world(outbox = setOf(relayA), verdicts = mapOf(relayA to Verdict.Accept), keyPair = readOnly)
            w.settings.updateNwcBackupRelays(listOf(relayA.url))

            val outcome = w.backup.deleteBackup()

            assertEquals(NwcBackupState.DeleteOutcome.Failed(NwcBackupState.Failure.CannotSign), outcome)
            assertTrue(w.relays.sentTo.isEmpty())
            assertEquals(listOf(relayA.url), w.settings.nwcBackupRelays.value)
        }

    @Test
    fun aPublishWaitingBehindADeleteDoesNotResurrectTheBackup() =
        runTest {
            val w = world(outbox = setOf(relayA), verdicts = mapOf(relayA to Verdict.Accept))
            assertTrue(w.backup.publishNow())

            w.settings.changeBackupNwcWallets(false)
            assertEquals(NwcBackupState.DeleteOutcome.Deleted(1), w.backup.deleteBackup())

            // A publish that queued behind the delete.
            assertTrue(w.backup.publishNow())
            assertNull(w.backup.fetchBackup())
        }

    @Test
    fun aRestoreFindsThePublishedBackupUntilItIsDeleted() =
        runTest {
            val w = world(outbox = setOf(relayA), verdicts = mapOf(relayA to Verdict.Accept))
            w.backup.publishNow()

            val found = w.backup.fetchBackup()
            assertNotNull(found)
            assertEquals(listOf(wallet.uri), found!!.wallets.mapNotNull { it.wallet.normalize()?.uri })

            w.backup.deleteBackup()
            val stored = w.relays.stored[relayA].orEmpty()
            assertTrue(stored.any { it.kind == AppSpecificDataEvent.KIND && NwcBackupFormat.isTombstone(it.tags, it.content) })
            assertNull(w.backup.fetchBackup())
        }

    @Test
    fun aRestoreOnAColdStartWaitsForTheRelaySocketBeforeAsking() =
        runTest {
            val w = world(outbox = setOf(relayA), verdicts = mapOf(relayA to Verdict.Accept))
            w.backup.publishNow()

            w.relays.connected.value = emptySet()
            backgroundScope.launch {
                delay(1_000)
                w.relays.connected.value = setOf(relayA)
            }

            val found = w.backup.fetchBackup()
            assertNotNull("the first restore attempt should wait out the handshake and find the backup", found)
        }

    @Test
    fun aRestoreListsTheNewestBackupFirstWhicheverAppSavedIt() =
        runTest {
            val w = world(outbox = setOf(relayA), verdicts = emptyMap())
            w.plant(relayA, NwcBackupFormat.WISP_D_TAG, bareUri("old@wisp.example"), createdAt = 1_000)
            w.plant(relayA, NwcBackupFormat.SIDECAR_D_TAG, bareUri("new@sidecar.example"), createdAt = 2_000)

            val found = w.backup.fetchBackup()!!

            assertEquals(listOf("new@sidecar.example", "old@wisp.example"), found.wallets.map { it.wallet.uri.lud16 })
            assertEquals(listOf(NwcBackupFormat.SIDECAR_D_TAG, NwcBackupFormat.WISP_D_TAG), found.wallets.map { it.dTag })
        }

    @Test
    fun anOlderTombstoneOnAnotherRelayDoesNotHideANewerBackup() =
        runTest {
            val w = world(outbox = setOf(relayA, relayB), verdicts = emptyMap())
            w.plant(relayB, NwcBackupFormat.WISP_D_TAG, plaintext = null, createdAt = 1_000)
            w.plant(relayA, NwcBackupFormat.WISP_D_TAG, bareUri("back@wisp.example"), createdAt = 2_000)

            val found = w.backup.fetchBackup()

            assertEquals(listOf("back@wisp.example"), found?.wallets?.map { it.wallet.uri.lud16 })
        }

    @Test
    fun hasBackupSeesAPublishedBackupWithoutDecryptingAndNotADeletedOne() =
        runTest {
            val w = world(outbox = setOf(relayA), verdicts = mapOf(relayA to Verdict.Accept))
            assertEquals(false, w.backup.hasBackup())

            w.backup.publishNow()
            assertEquals(true, w.backup.hasBackup())

            w.backup.deleteBackup()
            assertEquals(false, w.backup.hasBackup())
        }

    @Test
    fun hasBackupSeesAnotherAppsBackup() =
        runTest {
            val w = world(outbox = setOf(relayA), verdicts = emptyMap())
            w.plant(relayA, NwcBackupFormat.ZAPCOOKING_D_TAG, bareUri("there@zap.example"), createdAt = 1_000)

            assertEquals(true, w.backup.hasBackup())
        }

    @Test
    fun hasBackupIsUnknownWithoutRelaysToAsk() =
        runTest {
            val w = world(outbox = emptySet(), verdicts = emptyMap())

            assertNull(w.backup.hasBackup())
        }

    @Test
    fun aNewerTombstoneOnAnotherRelayHidesTheBackup() =
        runTest {
            val w = world(outbox = setOf(relayA, relayB), verdicts = emptyMap())
            w.plant(relayA, NwcBackupFormat.ZAPCOOKING_D_TAG, bareUri("gone@zap.example"), createdAt = 1_000)
            w.plant(relayB, NwcBackupFormat.ZAPCOOKING_D_TAG, plaintext = null, createdAt = 2_000)

            assertNull(w.backup.fetchBackup())
        }

    @Test
    fun aConnectionSeveralAppsBackedUpIsListedOnceFromTheNewest() =
        runTest {
            val w = world(outbox = setOf(relayA), verdicts = emptyMap())
            val same = bareUri("same@example.com")
            w.plant(relayA, NwcBackupFormat.WISP_D_TAG, same, createdAt = 1_000)
            w.plant(relayA, NwcBackupFormat.ZAPCOOKING_D_TAG, same, createdAt = 3_000)

            val found = w.backup.fetchBackup()!!

            assertEquals(1, found.wallets.size)
            assertEquals(NwcBackupFormat.ZAPCOOKING_D_TAG, found.wallets.single().dTag)
            assertEquals(3_000L, found.wallets.single().savedAt)
        }

    @Test
    fun aRestoreAlsoAsksTheReadRelays() =
        runTest {
            val w = world(outbox = setOf(relayA), verdicts = emptyMap(), read = setOf(relayB))
            w.plant(relayB, NwcBackupFormat.SIDECAR_D_TAG, bareUri("read@sidecar.example"), createdAt = 1_000)

            assertEquals(
                listOf("read@sidecar.example"),
                w.backup
                    .fetchBackup()
                    ?.wallets
                    ?.map { it.wallet.uri.lud16 },
            )
        }

    @Test
    fun publishingStillSkipsTheReadRelays() =
        runTest {
            val w = world(outbox = setOf(relayA), verdicts = mapOf(relayA to Verdict.Accept, relayB to Verdict.Accept), read = setOf(relayB))

            w.backup.publishNow()

            assertEquals(listOf(setOf(relayA)), w.relays.sentTo)
        }
}
