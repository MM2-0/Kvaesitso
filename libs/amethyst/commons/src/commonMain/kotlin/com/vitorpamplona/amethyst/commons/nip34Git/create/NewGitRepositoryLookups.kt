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
package com.vitorpamplona.amethyst.commons.nip34Git.create

import com.vitorpamplona.amethyst.commons.model.Account
import com.vitorpamplona.quartz.nip01Core.core.Address
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.relay.client.accessories.fetchAll
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip34Git.grasp.UserGraspListEvent
import com.vitorpamplona.quartz.nip34Git.repository.GitRepositoryEvent

/**
 * The account's GRASP servers (kind 10317) in preference order: the cached list, or the
 * newest one the outbox relays return within [idleTimeoutMs]. Null when the user has
 * never published one, which is when ngit falls back to
 * [com.vitorpamplona.quartz.nip34Git.grasp.GraspServer.DEFAULT_SERVERS].
 */
suspend fun Account.myGraspServers(idleTimeoutMs: Long = 5_000L): List<String>? =
    newestOwn<UserGraspListEvent>(
        address = UserGraspListEvent.createAddress(pubKey),
        filter = Filter(kinds = listOf(UserGraspListEvent.KIND), authors = listOf(pubKey), limit = 1),
        idleTimeoutMs = idleTimeoutMs,
    )?.grasps()

/**
 * The `d` identifiers of the account's own repository announcements, asked of the outbox
 * relays plus [extraRelays] (the GRASP servers a repository may live on) and kept in the
 * cache. A new repository must not reuse one: the announcement is addressable, so publishing
 * under the same `d` tag would silently replace the existing repository. Null when there is no
 * relay to ask.
 */
suspend fun Account.myGitRepositoryIdentifiers(
    extraRelays: Set<NormalizedRelayUrl> = emptySet(),
    idleTimeoutMs: Long = 5_000L,
): Set<String>? {
    val relays = outboxRelays.flow.value + extraRelays
    if (relays.isEmpty()) return null

    val filter = Filter(kinds = listOf(GitRepositoryEvent.KIND), authors = listOf(pubKey))
    val found =
        client
            .fetchAll(filters = relays.associateWith { listOf(filter) }, idleTimeoutMs = idleTimeoutMs)
            .filterIsInstance<GitRepositoryEvent>()
            // A relay can answer with events the filter never asked for.
            .filter { it.pubKey == pubKey }
    found.forEach { cache.justConsume(it, null, false) }
    return found.mapTo(mutableSetOf()) { it.dTag() }
}

/** True when the cache already holds one of the account's repositories under [identifier]. */
fun Account.hasCachedGitRepository(identifier: String): Boolean = cache.getAddressableNoteIfExists(Address(GitRepositoryEvent.KIND, pubKey, identifier))?.event != null

private suspend inline fun <reified T : Event> Account.newestOwn(
    address: Address,
    filter: Filter,
    idleTimeoutMs: Long,
): T? {
    (cache.getAddressableNoteIfExists(address)?.event as? T)?.let { return it }

    val relays = outboxRelays.flow.value
    if (relays.isEmpty()) return null

    val newest =
        client
            .fetchAll(filters = relays.associateWith { listOf(filter) }, idleTimeoutMs = idleTimeoutMs)
            .filterIsInstance<T>()
            .filter { it.pubKey == pubKey }
            .maxByOrNull { it.createdAt }
            ?: return null

    cache.justConsume(newest, null, false)
    return newest
}
