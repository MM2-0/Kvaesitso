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
package com.vitorpamplona.amethyst.commons.scheduledposts

import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.crypto.verify
import com.vitorpamplona.quartz.nip01Core.relay.client.INostrClient
import com.vitorpamplona.quartz.nip01Core.relay.client.accessories.publishAndConfirmDetailed
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.RelayUrlNormalizer
import com.vitorpamplona.quartz.utils.Log
import kotlinx.coroutines.CancellationException

/**
 * A single, self-contained detail row describing what happened to one claimed post.
 */
data class DrainDetail(
    val id: String,
    val eventId: String?,
    val status: ScheduledPostStatus,
    val publishedTo: List<String>,
    val error: String?,
)

/**
 * Aggregate result of a [ScheduledPostPublisher.drainDue] pass.
 *
 * @param published rows that got at least one relay ack and were marked SENT.
 * @param failed rows permanently marked FAILED this pass.
 * @param skipped rows that hit a transient no-ack and were released back to PENDING
 *   for a later retry (not counted as failed).
 */
data class DrainReport(
    val published: Int,
    val failed: Int,
    val skipped: Int,
    val details: List<DrainDetail>,
)

/**
 * Pure, platform-agnostic drain/publish loop shared by the Android worker, the Desktop
 * scheduler and the Desktop's headless `--publish-scheduled` run. Claims due posts from [store], re-verifies each
 * signed event, publishes it via the supplied [client], and records the outcome
 * back into the store. All platform-specific concerns (notifications, cache
 * consumption) are injected as callbacks so this class stays free of any UI or
 * framework dependency.
 */
class ScheduledPostPublisher(
    private val store: ScheduledPostStore,
    private val client: INostrClient,
    private val resolveRelays: (ScheduledPost) -> Set<NormalizedRelayUrl>,
    private val onSent: suspend (ScheduledPost) -> Unit = {},
    private val onFailed: suspend (ScheduledPost, String?) -> Unit = { _, _ -> },
    private val consume: suspend (event: Event, relays: Set<NormalizedRelayUrl>, extras: List<Event>) -> Unit = { _, _, _ -> },
) {
    /**
     * Claim and publish every post due at [nowSec]. When [accountPubkey] is non-null
     * only that account's posts are drained (the Android worker, per account, and the
     * Desktop scheduler); when null, all accounts are drained (Desktop's headless
     * `--publish-scheduled` run). Returns a [DrainReport] summarizing outcomes.
     */
    suspend fun drainDue(
        nowSec: Long,
        accountPubkey: String? = null,
    ): DrainReport {
        val claimed =
            if (accountPubkey != null) {
                store.claimDuePosts(nowSec, accountPubkey)
            } else {
                store.claimDuePosts(nowSec)
            }

        if (claimed.isEmpty()) {
            return DrainReport(published = 0, failed = 0, skipped = 0, details = emptyList())
        }

        var published = 0
        var failed = 0
        var skipped = 0
        val details = mutableListOf<DrainDetail>()

        for (post in claimed) {
            try {
                val event = Event.fromJson(post.signedEventJson)

                if (event.pubKey != post.accountPubkey) {
                    val msg = "pubkey mismatch"
                    store.markFailed(post.id, msg)
                    onFailed(post, msg)
                    failed++
                    details.add(DrainDetail(post.id, event.id, ScheduledPostStatus.FAILED, emptyList(), msg))
                    continue
                }

                if (!event.verify()) {
                    val msg = "invalid signature"
                    store.markFailed(post.id, msg)
                    onFailed(post, msg)
                    failed++
                    details.add(DrainDetail(post.id, event.id, ScheduledPostStatus.FAILED, emptyList(), msg))
                    continue
                }

                val relays =
                    resolveRelays(post).ifEmpty {
                        post.relayUrls.mapNotNull { RelayUrlNormalizer.normalizeOrNull(it) }.toSet()
                    }
                val extras = post.extraEventsJson.map { Event.fromJson(it) }

                val results = client.publishAndConfirmDetailed(event, relays)
                val acceptedBy = results.filter { it.value }.keys.map { it.url }

                if (acceptedBy.isNotEmpty()) {
                    // Published: SENT first, so a failing callback below cannot turn a post that
                    // went out into a FAILED one (and a "failed" notification after "sent").
                    store.markSent(post.id)
                    // Only feed the event into the local cache (user's own timeline) once
                    // at least one relay acked — a failed publish must not appear locally.
                    runAfterSent("consume", post) { consume(event, relays, extras) }
                    runAfterSent("notify", post) { onSent(post) }
                    published++
                    details.add(DrainDetail(post.id, event.id, ScheduledPostStatus.SENT, acceptedBy, null))
                    Log.d(TAG) { "Published ${post.id} to ${acceptedBy.size}/${relays.size} relay(s)" }
                } else {
                    // Transient failure (sockets not reconnected, brief offline). Don't
                    // strand the post as FAILED forever: release the claim so the next
                    // tick re-claims it, until we've burned through MAX_PUBLISH_ATTEMPTS.
                    val msg = "no relay acknowledged"
                    if (post.attemptCount < MAX_PUBLISH_ATTEMPTS) {
                        store.releaseClaim(post.id)
                        skipped++
                        details.add(DrainDetail(post.id, event.id, ScheduledPostStatus.PENDING, emptyList(), msg))
                        Log.w(TAG) { "Publish ${post.id} got no ack (attempt ${post.attemptCount}); released for retry" }
                    } else {
                        store.markFailed(post.id, msg)
                        onFailed(post, msg)
                        failed++
                        details.add(DrainDetail(post.id, event.id, ScheduledPostStatus.FAILED, emptyList(), msg))
                        Log.w(TAG) { "Publish ${post.id} failed after $MAX_PUBLISH_ATTEMPTS attempts: $msg" }
                    }
                }
            } catch (e: Exception) {
                // A cancelled drain (the worker stopped, the app quit) is not a failed post: the
                // claim expires (CLAIM_TTL) and the next drain retries it.
                if (e is CancellationException) throw e
                store.markFailed(post.id, e.message)
                onFailed(post, e.message)
                failed++
                details.add(DrainDetail(post.id, null, ScheduledPostStatus.FAILED, emptyList(), e.message))
                Log.e(TAG, "Failed to publish scheduled post ${post.id}", e)
            }
        }

        return DrainReport(published = published, failed = failed, skipped = skipped, details = details)
    }

    private suspend fun runAfterSent(
        what: String,
        post: ScheduledPost,
        block: suspend () -> Unit,
    ) {
        try {
            block()
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Log.w(TAG, "Post ${post.id} was published, but its $what step failed", e)
        }
    }

    companion object {
        private const val TAG = "ScheduledPostPublisher"

        /**
         * Max number of publish attempts for a post before a transient "no relay
         * acknowledged" outcome is treated as a permanent FAILED. attemptCount is
         * bumped on every claim, so it naturally caps the release-for-retry loop.
         */
        const val MAX_PUBLISH_ATTEMPTS = 5
    }
}
