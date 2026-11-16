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
package com.vitorpamplona.amethyst.commons.model.nip89AppHandlers

import com.vitorpamplona.amethyst.commons.model.Account
import com.vitorpamplona.amethyst.commons.model.cache.LocalCache
import com.vitorpamplona.amethyst.commons.model.cache.filterIntoSet
import com.vitorpamplona.quartz.nip01Core.core.Address
import com.vitorpamplona.quartz.nip01Core.core.awaitCreatedAtToSupersede
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.signers.NostrSigner
import com.vitorpamplona.quartz.nip89AppHandlers.PlatformType
import com.vitorpamplona.quartz.nip89AppHandlers.definition.AppDefinitionEvent
import com.vitorpamplona.quartz.nip89AppHandlers.recommendation.AppRecommendationEvent
import com.vitorpamplona.quartz.nip89AppHandlers.recommendation.tags.RecommendationTag
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * This user's public NIP-89 app recommendations: one kind 31989 addressable
 * event per handled kind, each listing the recommended apps for that kind.
 */
class AppRecommendationsState(
    val signer: NostrSigner,
    val cache: LocalCache,
    val scope: CoroutineScope,
) {
    /**
     * Synchronous cache scan. Seeds [flow] and feeds the read-modify-write
     * publishers below, which must read current truth from the cache while
     * holding [publishMutex].
     */
    fun existingRecommendationEvents(): List<AppRecommendationEvent> =
        cache.addressables
            .filterIntoSet(AppRecommendationEvent.KIND, signer.pubKey)
            .mapNotNull { it.event as? AppRecommendationEvent }

    /**
     * My kind 31989 recommendation events (one per handled kind), kept in
     * sync as the cache consumes new versions. UI should collect this
     * instead of rescanning the cache on every event bundle.
     *
     * Eagerly started on purpose, like the sibling account states: the
     * registered observer holds strong references to these notes, pinning
     * them in the soft-reference cache so the read-modify-write publishers
     * below never rebuild a 31989 from a partially garbage-collected
     * snapshot (which would silently drop previously recommended apps).
     */
    val flow: StateFlow<List<AppRecommendationEvent>> =
        cache
            .observeEvents<AppRecommendationEvent>(
                Filter(kinds = listOf(AppRecommendationEvent.KIND), authors = listOf(signer.pubKey)),
            ).flowOn(Dispatchers.IO)
            .stateIn(scope, SharingStarted.Eagerly, existingRecommendationEvents())

    /**
     * Serializes read-modify-write of the per-kind recommendation events so
     * two rapid toggles can't race each other into losing updates.
     */
    private val publishMutex = Mutex()

    /**
     * The createdAt this d-tag's next version needs to supersede whatever is in cache for it. Waits
     * out the second rather than stamping the future, so repeatedly toggling one recommendation
     * cannot drift its `created_at` ahead of the clock. Runs under [publishMutex], which is what
     * keeps two waits from racing each other onto the same second.
     */
    private suspend fun nextCreatedAt(supportedKind: String): Long {
        val address = Address(AppRecommendationEvent.KIND, signer.pubKey, supportedKind)
        val latest = cache.getAddressableNoteIfExists(address)?.event?.createdAt ?: 0L
        return awaitCreatedAtToSupersede(latest)
    }

    private fun currentRecommendations(supportedKind: String): List<RecommendationTag> {
        val address = Address(AppRecommendationEvent.KIND, signer.pubKey, supportedKind)
        val event = cache.getAddressableNoteIfExists(address)?.event as? AppRecommendationEvent
        return event?.recommendations() ?: emptyList()
    }

    /**
     * Adds [app] to this user's public NIP-89 recommendations, one kind 31989
     * event per event kind the app declares to handle via `k` tags.
     */
    suspend fun recommendApp(
        app: AppDefinitionEvent,
        relayHint: NormalizedRelayUrl?,
        account: Account,
    ) {
        if (!account.isWriteable()) return

        val kinds = app.supportedKinds()
        if (kinds.isEmpty()) return

        val newTag = RecommendationTag(app.address(), relayHint, PlatformType.ANDROID.code)

        publishMutex.withLock {
            kinds.forEach { kind ->
                val supportedKind = kind.toString()
                val current = currentRecommendations(supportedKind)
                if (current.any { it.address == app.address() }) return@forEach

                val template =
                    AppRecommendationEvent.buildFromTags(
                        supportedKind = supportedKind,
                        recommendations = current + newTag,
                        createdAt = nextCreatedAt(supportedKind),
                    )
                account.sendMyPublicAndPrivateOutbox(account.signer.sign(template))
            }
        }
    }

    /** Removes the app at [address] from every kind 31989 recommendation event of this user. */
    suspend fun unrecommendApp(
        address: Address,
        account: Account,
    ) {
        if (!account.isWriteable()) return

        publishMutex.withLock {
            existingRecommendationEvents().forEach { event ->
                val current = event.recommendations()
                val updated = current.filterNot { it.address == address }
                if (updated.size == current.size) return@forEach

                val template =
                    AppRecommendationEvent.buildFromTags(
                        supportedKind = event.dTag(),
                        recommendations = updated,
                        createdAt = nextCreatedAt(event.dTag()),
                    )
                account.sendMyPublicAndPrivateOutbox(account.signer.sign(template))
            }
        }
    }
}
