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
package com.vitorpamplona.amethyst.commons.service.pow

import com.vitorpamplona.amethyst.commons.model.Account
import com.vitorpamplona.amethyst.commons.scheduledposts.ScheduledPost
import com.vitorpamplona.amethyst.commons.scheduledposts.ScheduledPostStore
import com.vitorpamplona.amethyst.commons.service.pow.PersistedPoWJob
import com.vitorpamplona.amethyst.commons.service.pow.PoWJobStore
import com.vitorpamplona.amethyst.commons.service.pow.PoWPublishQueue
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.RelayUrlNormalizer
import com.vitorpamplona.quartz.nip01Core.signers.EventTemplate
import com.vitorpamplona.quartz.nip17Dm.NIP17Factory
import com.vitorpamplona.quartz.utils.Log
import java.util.UUID

/**
 * Re-enqueues the mining jobs checkpointed by [PoWJobStore] when an account
 * logs in, replacing the lost in-memory continuation with the headless replay
 * described by each record. Restore is idempotent: the queue dedupes by job
 * id, so a login flow that emits twice cannot double-mine.
 */
class PowJobRestorer(
    private val queue: PoWPublishQueue,
    private val store: PoWJobStore,
    private val scheduledPostStore: ScheduledPostStore,
) {
    suspend fun restore(account: Account) {
        val records = store.listFor(account.signer.pubKey)
        if (records.isEmpty()) return

        Log.d(TAG) { "Restoring ${records.size} pending PoW job(s) for ${account.signer.pubKey.take(8)}…" }

        records.forEach { record ->
            if (record.difficulty <= 0) {
                store.remove(record.id)
                return@forEach
            }

            if (record.replayType == PersistedPoWJob.REPLAY_WRAPS) {
                restoreWraps(account, record)
                return@forEach
            }

            val template =
                try {
                    EventTemplate.fromJson(record.templateJson)
                } catch (e: Exception) {
                    Log.w(TAG, "Dropping unreadable PoW job ${record.id}", e)
                    store.remove(record.id)
                    return@forEach
                }

            queue.enqueue(
                template = template,
                pubKey = account.signer.pubKey,
                difficulty = record.difficulty,
                persistAs = record,
                // a restored job may be hours old; publish with a fresh
                // created_at (NIP-13 recommendation) — except scheduled posts,
                // whose future created_at is the point.
                refreshCreatedAt = record.replayType != PersistedPoWJob.REPLAY_SCHEDULE,
            ) { mined ->
                replay(account, record, mined)
            }
        }
    }

    /**
     * Wrap jobs carry no template — the pre-signed seals live in
     * [PersistedPoWJob.extraEventsJson], one recipient per seal in
     * [PersistedPoWJob.recipientPubkeys]. Re-enqueueing goes through the same
     * [Account.mineWrapsInBackground] path the send used, with the existing
     * record so the checkpoint id (and restore idempotence) is preserved.
     */
    private fun restoreWraps(
        account: Account,
        record: PersistedPoWJob,
    ) {
        if (record.extraEventsJson.size != record.recipientPubkeys.size) {
            Log.w(TAG) { "Dropping malformed wrap PoW job ${record.id}: ${record.extraEventsJson.size} seal(s) vs ${record.recipientPubkeys.size} recipient(s)" }
            store.remove(record.id)
            return
        }

        val seals =
            record.extraEventsJson.zip(record.recipientPubkeys).mapNotNull { (sealJson, recipient) ->
                try {
                    NIP17Factory.AddressedSeal(recipient = recipient, seal = Event.fromJson(sealJson))
                } catch (e: Exception) {
                    Log.w(TAG, "Dropping unreadable seal of wrap PoW job ${record.id}", e)
                    null
                }
            }

        if (seals.isEmpty()) {
            store.remove(record.id)
            return
        }

        account.mineWrapsInBackground(
            seals = seals,
            expirationDelta = record.wrapExpirationDelta,
            difficulty = record.difficulty,
            existingRecord = record,
        )
    }

    private suspend fun replay(
        account: Account,
        record: PersistedPoWJob,
        mined: EventTemplate<Event>,
    ) {
        val extras =
            record.extraEventsJson.mapNotNull {
                try {
                    Event.fromJson(it)
                } catch (e: Exception) {
                    Log.w(TAG, "Dropping unreadable extra event of PoW job ${record.id}", e)
                    null
                }
            }

        when (record.replayType) {
            PersistedPoWJob.REPLAY_BROADCAST -> {
                account.signAndComputeBroadcast(mined, extras)
            }

            PersistedPoWJob.REPLAY_RELAYS -> {
                val relays = record.relayUrls.mapNotNull { RelayUrlNormalizer.normalizeOrNull(it) }
                account.signAndSendPrivatelyOrBroadcast(mined) { relays }
            }

            PersistedPoWJob.REPLAY_SCHEDULE -> {
                val publishAtSec = record.publishAtSec
                if (publishAtSec == null) {
                    Log.w(TAG) { "Scheduled PoW job ${record.id} has no publish time; broadcasting instead" }
                    account.signAndComputeBroadcast(mined, extras)
                    return
                }
                val (event, relays, extraList) = account.createPostEvent(mined, extras)
                scheduledPostStore.add(
                    ScheduledPost(
                        id = UUID.randomUUID().toString(),
                        accountPubkey = event.pubKey,
                        signedEventJson = event.toJson(),
                        relayUrls = relays.map { it.url },
                        extraEventsJson = extraList.map { it.toJson() },
                        publishAtSec = publishAtSec,
                        createdAtSec = System.currentTimeMillis() / 1000,
                    ),
                )
            }

            else -> Log.w(TAG) { "Unknown replay type '${record.replayType}' for PoW job ${record.id}; dropping" }
        }
    }

    companion object {
        private const val TAG = "PowJobRestorer"
    }
}
