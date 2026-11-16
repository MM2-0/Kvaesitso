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
package com.vitorpamplona.geode

import com.vitorpamplona.geode.testing.RelayClientTest
import com.vitorpamplona.geode.testing.preload
import com.vitorpamplona.geode.testing.publish
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import com.vitorpamplona.quartz.nip01Core.relay.client.accessories.negentropyReconcileIds
import com.vitorpamplona.quartz.nip01Core.relay.client.accessories.negentropySettleDeletions
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.RelayUrlNormalizer
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerSync
import com.vitorpamplona.quartz.nip01Core.store.IdAndTime
import com.vitorpamplona.quartz.nip01Core.store.deletionsCovering
import com.vitorpamplona.quartz.nip01Core.store.sqlite.EventStore
import com.vitorpamplona.quartz.nip02FollowList.ContactListEvent
import com.vitorpamplona.quartz.nip09Deletions.DeletionRequestEvent
import com.vitorpamplona.quartz.nip10Notes.TextNoteEvent
import com.vitorpamplona.quartz.nip62RequestToVanish.RequestToVanishEvent
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The `amy sync` deletion rule: for the events the relay HAS that we LACK (the
 * negentropy need set), publish the local deletions that would make the relay remove
 * them — and only those. [deletionsCovering] is the core: it maps a set of server-held
 * events to the local deletions that cover them, across id-based (NIP-09 `e`),
 * address-based (NIP-09 `a`, cutoff-checked) and NIP-62 vanish (relay-targeted, cutoff).
 */
class DeletionSyncTest : RelayClientTest() {
    private val signer = NostrSignerSync(KeyPair())
    private val here: NormalizedRelayUrl get() = defaultRelayUrl
    private val elsewhere = RelayUrlNormalizer.normalize("wss://elsewhere.example/")

    private val store = EventStore(null)

    @AfterTest fun closeStore() = store.close()

    private fun note(text: String): Event = signer.sign(TextNoteEvent.build(text))

    // ---- deletionsCovering: the three coverage forms --------------------------

    @Test
    fun idBasedDeletionCoversByETag() =
        runBlocking {
            val target = note("delete me")
            val deletion = signer.sign(DeletionRequestEvent.build(listOf(target), createdAt = target.createdAt + 1))
            store.insert(deletion)

            assertEquals(listOf(deletion.id), store.deletionsCovering(listOf(target), here).map { it.id })
            // A different note the deletion doesn't name is not covered.
            assertTrue(store.deletionsCovering(listOf(note("unrelated")), here).isEmpty())
        }

    @Test
    fun addressBasedDeletionCoversByATagWithCutoff() =
        runBlocking {
            val contacts = ContactListEvent.createFromScratch(emptyList(), null, signer)
            // Address-only deletion (no `e` tag) → only the `a`-tag path can match it.
            val delAddr = signer.sign(DeletionRequestEvent.buildAddressOnly(listOf(contacts), createdAt = contacts.createdAt + 1))
            store.insert(delAddr)

            assertEquals(
                listOf(delAddr.id),
                store.deletionsCovering(listOf(contacts), here).map { it.id },
                "a replaceable event is covered by an address deletion at/after it",
            )

            // NIP-09 cutoff: a deletion OLDER than the event does not delete it.
            val stale = EventStore(null)
            stale.insert(signer.sign(DeletionRequestEvent.buildAddressOnly(listOf(contacts), createdAt = contacts.createdAt - 1)))
            assertTrue(stale.deletionsCovering(listOf(contacts), here).isEmpty(), "an older address deletion does not cover")
            stale.close()
        }

    @Test
    fun vanishCoversAuthorsEventsWhenTargetedAndNewer() =
        runBlocking {
            val old = note("before the vanish")
            val vanishHere = signer.sign(RequestToVanishEvent.build(here, createdAt = old.createdAt + 1))
            store.insert(vanishHere)

            assertEquals(
                listOf(vanishHere.id),
                store.deletionsCovering(listOf(old), here).map { it.id },
                "a relay-targeted vanish issued after the event covers it",
            )

            // Not targeting this relay → not sent here.
            val otherStore = EventStore(null)
            otherStore.insert(signer.sign(RequestToVanishEvent.build(elsewhere, createdAt = old.createdAt + 1)))
            assertTrue(otherStore.deletionsCovering(listOf(old), here).isEmpty(), "a vanish for another relay is not sent")

            // A newer event (created after the vanish) is NOT deleted by it.
            val newer = signer.sign(TextNoteEvent.build("after", createdAt = vanishHere.createdAt + 10))
            assertTrue(store.deletionsCovering(listOf(newer), here).isEmpty(), "the vanish does not cover a later event")
            otherStore.close()
        }

    // ---- end-to-end through the relay ----------------------------------------

    // UP direction: we deleted it, the relay still has it → send our deletion up.
    @Test
    fun sendsCoveringDeletionSoRelayRemovesTheNote() =
        runBlocking {
            val target = note("delete me e2e")
            val deletion = signer.sign(DeletionRequestEvent.build(listOf(target), createdAt = target.createdAt + 1))

            // Relay holds the note; we already deleted it locally (hold only the kind-5).
            defaultRelay.preload(listOf(target))
            val local = hub.getOrCreate(RelayUrlNormalizer.normalize("ws://local/"))
            local.preload(listOf(target, deletion))
            assertTrue(local.store.query<Event>(Filter(ids = listOf(target.id))).isEmpty(), "local deleted the note")

            // Reconcile → the note is a need id. (No local kind-1 remains.)
            val diff =
                withTimeout(20_000) {
                    client.negentropyReconcileIds(relay = defaultRelayUrl, filter = Filter(kinds = listOf(1)), localEntries = emptyList<IdAndTime>())
                }
            assertEquals(setOf(target.id), diff.needIds.toSet())

            // What SyncCommand does: fetch the need events, ask the local store which of
            // our deletions cover them, publish those.
            val serverEvents = defaultRelay.store.query<Event>(Filter(ids = diff.needIds))
            val covering = local.store.deletionsCovering(serverEvents, defaultRelayUrl)
            assertEquals(listOf(deletion.id), covering.map { it.id })
            covering.forEach { defaultRelay.publish(it) }

            assertTrue(
                defaultRelay.store.query<Event>(Filter(ids = listOf(target.id))).isEmpty(),
                "relay applied the pushed deletion and removed the note",
            )
        }

    // DOWN direction: the relay deleted it, we still have it → pull the relay's deletion
    // down and apply it locally (the residual-have resolution).
    @Test
    fun appliesRelaysDeletionSoLocalRemovesTheNote() =
        runBlocking {
            val target = note("delete me down")
            val deletion = signer.sign(DeletionRequestEvent.build(listOf(target), createdAt = target.createdAt + 1))

            // Relay already applied the deletion → holds only the kind-5.
            defaultRelay.preload(listOf(target, deletion))
            assertTrue(defaultRelay.store.query<Event>(Filter(ids = listOf(target.id))).isEmpty(), "relay deleted the note")

            // Local still holds the note (never saw the deletion).
            val local = hub.getOrCreate(RelayUrlNormalizer.normalize("ws://local-down/"))
            local.preload(listOf(target))
            assertEquals(1, local.store.query<Event>(Filter(ids = listOf(target.id))).size)

            // Reconcile → the note is a HAVE (we have it, the relay lacks it).
            val diff =
                withTimeout(20_000) {
                    client.negentropyReconcileIds(
                        relay = defaultRelayUrl,
                        filter = Filter(kinds = listOf(1)),
                        localEntries = listOf(IdAndTime(target.createdAt, target.id)),
                    )
                }
            assertEquals(setOf(target.id), diff.haveIds.toSet())

            // What SyncCommand does for the down direction: take our have events, ask the
            // RELAY which of ITS deletions cover them, and apply those locally.
            val ourEvents = local.store.query<Event>(Filter(ids = diff.haveIds))
            val relayDeletions = deletionsCovering(ourEvents, defaultRelayUrl) { f -> defaultRelay.store.query<Event>(f) }
            assertEquals(listOf(deletion.id), relayDeletions.map { it.id })
            relayDeletions.filterIsInstance<DeletionRequestEvent>().forEach { local.store.insert(it) }

            assertTrue(
                local.store.query<Event>(Filter(ids = listOf(target.id))).isEmpty(),
                "local applied the pulled deletion and removed the note",
            )
        }

    // ---- the full accessory loop (negentropySettleDeletions) -----------------

    // sendUp: local holds the deletion, relay still has the note → the loop pushes it
    // up and the relay converges to gone.
    @Test
    fun settleSendsOurDeletionUp() =
        runBlocking {
            val target = note("settle up")
            val deletion = signer.sign(DeletionRequestEvent.build(listOf(target), createdAt = target.createdAt + 1))
            val localStore = EventStore(null)
            localStore.insert(target)
            localStore.insert(deletion) // deletes target locally, keeps the kind-5
            defaultRelay.preload(listOf(target))

            val res =
                withTimeout(30_000) {
                    client.negentropySettleDeletions(
                        relay = defaultRelayUrl,
                        filter = Filter(kinds = listOf(1)),
                        store = localStore,
                        sendUp = true,
                        applyDown = false,
                        idleTimeoutMs = 20_000,
                    )
                }

            assertEquals(1, res.sentUp)
            assertEquals(0, res.appliedDown)
            assertTrue(
                defaultRelay.store.query<Event>(Filter(ids = listOf(target.id))).isEmpty(),
                "relay converged: the deleted note is gone",
            )
            localStore.close()
        }

    // applyDown: relay deleted the note (holds only the kind-5), local still has it →
    // the loop pulls the relay's deletion down and local converges to gone.
    @Test
    fun settleAppliesRelayDeletionDown() =
        runBlocking {
            val target = note("settle down")
            val deletion = signer.sign(DeletionRequestEvent.build(listOf(target), createdAt = target.createdAt + 1))
            defaultRelay.preload(listOf(target, deletion)) // relay deletes target, keeps the kind-5
            val localStore = EventStore(null)
            localStore.insert(target)

            val res =
                withTimeout(30_000) {
                    client.negentropySettleDeletions(
                        relay = defaultRelayUrl,
                        filter = Filter(kinds = listOf(1)),
                        store = localStore,
                        sendUp = false,
                        applyDown = true,
                        idleTimeoutMs = 20_000,
                    )
                }

            assertEquals(0, res.sentUp)
            assertEquals(1, res.appliedDown)
            assertTrue(
                localStore.query<Event>(Filter(ids = listOf(target.id))).isEmpty(),
                "local converged: the relay-deleted note is gone",
            )
            localStore.close()
        }
}
