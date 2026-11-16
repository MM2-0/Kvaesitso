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
package com.vitorpamplona.amethyst.commons.model.nipFAAgentKeys

import com.vitorpamplona.amethyst.commons.agents.AgentProviderPresets
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.TagArray
import com.vitorpamplona.quartz.nip01Core.crypto.verify
import com.vitorpamplona.quartz.nip01Core.relay.client.INostrClient
import com.vitorpamplona.quartz.nip01Core.relay.client.accessories.DONE_REASON_EOSE
import com.vitorpamplona.quartz.nip01Core.relay.client.accessories.fetchAllWithHooks
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.signers.EventTemplate
import com.vitorpamplona.quartz.nip01Core.signers.NostrSigner
import com.vitorpamplona.quartz.nip65RelayList.AdvertisedRelayListEvent
import com.vitorpamplona.quartz.nipFAAgentKeys.AgentKeyEvent
import com.vitorpamplona.quartz.nipFAAgentKeys.agentKeys
import com.vitorpamplona.quartz.nipFAAgentKeys.tags.AgentKeyTag
import com.vitorpamplona.quartz.nipFBScheduledTasks.ScheduledTaskClaims
import com.vitorpamplona.quartz.utils.TimeUtils

/** Why a key can't be saved for a URL. */
enum class AgentKeyProblem {
    /** Not an http(s) URL with a host. */
    BAD_URL,

    /** A key over plain `http` to another machine (NIP-FA: https, unless loopback or no key). */
    NEEDS_HTTPS,

    /** A provider that only answers with a key, and no key. */
    NEEDS_KEY,
}

/**
 * The orderings NIP-FA's Editing applies to the keys, matched by URL (case and a trailing `/`
 * ignored), plus the save check the setup screen makes. Pure: the read, the encryption and the
 * publish are [readAgentKeysForRewrite] and [rewriteAgentKeys].
 */
object AgentKeyEdits {
    fun sameUrl(
        a: String,
        b: String,
    ) = AgentProviderPresets.normalizeUrl(a) == AgentProviderPresets.normalizeUrl(b)

    /** The key saved for [url], or null. */
    fun forUrl(
        keys: List<AgentKeyTag>,
        url: String,
    ): AgentKeyTag? = keys.firstOrNull { sameUrl(it.url, url) }

    /**
     * [keys] with [key] as the one key for its URL: where the key it replaces stood, else last,
     * and first when [makeDefault] (or when it is the only key).
     */
    fun withKey(
        keys: List<AgentKeyTag>,
        key: AgentKeyTag,
        makeDefault: Boolean,
    ): List<AgentKeyTag> {
        val at = keys.indexOfFirst { sameUrl(it.url, key.url) }
        val others = keys.filterNot { sameUrl(it.url, key.url) }
        return when {
            makeDefault -> listOf(key) + others
            at < 0 -> others + key
            else -> others.toMutableList().apply { add(at.coerceAtMost(size), key) }
        }
    }

    /** [keys] with the key for [url] first; null when there is none. */
    fun withDefault(
        keys: List<AgentKeyTag>,
        url: String,
    ): List<AgentKeyTag>? {
        val key = forUrl(keys, url) ?: return null
        return listOf(key) + keys.filter { it !== key }
    }

    /** [keys] without the keys for [url]; null when there is none. */
    fun without(
        keys: List<AgentKeyTag>,
        url: String,
    ): List<AgentKeyTag>? {
        if (forUrl(keys, url) == null) return null
        return keys.filterNot { sameUrl(it.url, url) }
    }

    /** What stops [key] being saved for [url] (both trimmed), or null when it can be. */
    fun problem(
        url: String,
        key: String,
    ): AgentKeyProblem? {
        if (!AgentProviderPresets.isValidUrl(url)) return AgentKeyProblem.BAD_URL
        if (!AgentProviderPresets.isValidUrl(url, key)) return AgentKeyProblem.NEEDS_HTTPS
        if (key.isEmpty() && AgentProviderPresets.forUrl(url)?.needsKey == true) return AgentKeyProblem.NEEDS_KEY
        return null
    }
}

/**
 * NIP-FA's Editing, first step: the newest kind 12000 of [author] on [relays], the user's NIP-65
 * write relays and nothing else (never a default or bootstrap relay: one that never saw the
 * event answers "none" just as fast), or [known], what this device already holds, when it is
 * newer. More than half of [relays] must answer (reach EOSE), as NIP-FA asks; a copy on this
 * device doesn't make up for the ones that didn't. Each event read is verified and handed to
 * [onRead]. Null when the relays answered and nobody has one.
 *
 * @throws AgentKeysNoWriteRelaysException when [relays] is empty: there is nowhere to read or write.
 * @throws AgentKeysNoQuorumException when not more than half of [relays] answered: an edit could
 *   start from an old list, or from nothing, and delete the user's other keys.
 */
suspend fun INostrClient.readAgentKeysForRewrite(
    relays: Set<NormalizedRelayUrl>,
    author: HexKey,
    known: AgentKeyEvent?,
    idleTimeoutMs: Long = 8_000,
    onRead: suspend (AgentKeyEvent, NormalizedRelayUrl) -> Unit = { _, _ -> },
): AgentKeyEvent? {
    if (relays.isEmpty()) throw AgentKeysNoWriteRelaysException()
    val filter = Filter(kinds = listOf(AgentKeyEvent.KIND), authors = listOf(author))
    val read = mutableListOf<AgentKeyEvent>()
    val result =
        fetchAllWithHooks(relays.associateWith { listOf(filter) }, idleTimeoutMs = idleTimeoutMs) { relay, event ->
            if (event is AgentKeyEvent && event.pubKey == author && event.verify()) {
                onRead(event, relay)
                read += event
            }
            true
        }
    val answered = result.doneReasons.values.count { it == DONE_REASON_EOSE }
    if (!ScheduledTaskClaims.hasQuorum(relays.size, answered)) throw AgentKeysNoQuorumException(relays.size, answered)
    return (read + listOfNotNull(known)).newest()
}

/**
 * The user's own NIP-65 list (kind 10002), for a device that doesn't hold one yet: a fresh login,
 * where "no list" and "not loaded yet" look the same. Asked of [relays] (bootstrap relays: where
 * lists are found, never where NIP-FA's keys are read); each verified copy goes to [onRead].
 * Null when none of them has one.
 */
suspend fun INostrClient.fetchOwnRelayList(
    relays: Set<NormalizedRelayUrl>,
    author: HexKey,
    idleTimeoutMs: Long = 8_000,
    onRead: suspend (AdvertisedRelayListEvent, NormalizedRelayUrl) -> Unit = { _, _ -> },
): AdvertisedRelayListEvent? {
    if (relays.isEmpty()) return null
    val filter = Filter(kinds = listOf(AdvertisedRelayListEvent.KIND), authors = listOf(author), limit = 1)
    val read = mutableListOf<AdvertisedRelayListEvent>()
    fetchAllWithHooks(relays.associateWith { listOf(filter) }, idleTimeoutMs = idleTimeoutMs) { relay, event ->
        if (event is AdvertisedRelayListEvent && event.pubKey == author && event.verify()) {
            onRead(event, relay)
            read += event
        }
        true
    }
    return read.minWithOrNull(compareByDescending<AdvertisedRelayListEvent> { it.createdAt }.thenBy { it.id })
}

/** The highest `created_at`, then the lowest id. */
private fun List<AgentKeyEvent>.newest(): AgentKeyEvent? = minWithOrNull(compareByDescending<AgentKeyEvent> { it.createdAt }.thenBy { it.id })

/**
 * The event after [earlier] whose keys are [transform] of its keys, every other tag kept where it
 * was, `created_at` = max([now], previous + 1); a new event when there is no [earlier]. Null when
 * [transform] returns null or the same keys: nothing to write. Decrypts [earlier] once.
 *
 * @throws com.vitorpamplona.quartz.nip01Core.signers.SignerExceptions when [signer] can't read [earlier].
 * @throws com.vitorpamplona.quartz.nipFAAgentKeys.UnreadableAgentKeysException when [earlier] holds
 *   content that isn't a tag array: it is never overwritten.
 */
suspend fun rewriteAgentKeys(
    earlier: AgentKeyEvent?,
    signer: NostrSigner,
    now: Long = TimeUtils.now(),
    /** [earlier]'s private tags when the caller already decrypted them ([AgentKeyEvent.readForRewrite]): not asked again. */
    earlierPrivate: TagArray? = null,
    transform: (List<AgentKeyTag>) -> List<AgentKeyTag>?,
): EventTemplate<AgentKeyEvent>? {
    if (earlier == null) {
        val keys = transform(emptyList())?.takeIf { it.isNotEmpty() } ?: return null
        return AgentKeyEvent.build(keys, signer, now)
    }
    val private = earlierPrivate ?: AgentKeyEvent.readForRewrite(earlier, signer)
    val current = private.agentKeys()
    val next = transform(current) ?: return null
    if (next == current) return null
    return AgentKeyEvent.rewrite(earlier, private, next, signer, AgentKeyEvent.nextCreatedAt(earlier, now))
}
