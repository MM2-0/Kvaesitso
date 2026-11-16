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
package com.vitorpamplona.amethyst.commons.model.buzz

import com.vitorpamplona.amethyst.commons.model.User
import com.vitorpamplona.amethyst.commons.model.cache.LocalCache
import com.vitorpamplona.amethyst.commons.model.nip29RelayGroups.RelayGroupChannel
import com.vitorpamplona.amethyst.commons.util.KmpLock
import com.vitorpamplona.amethyst.commons.util.withLock
import com.vitorpamplona.quartz.buzz.agentProfiles.AgentProfileEvent
import com.vitorpamplona.quartz.buzz.identityNames.IdentityNamePolicy
import com.vitorpamplona.quartz.buzz.identityNames.NamingIdentity
import com.vitorpamplona.quartz.buzz.identityNames.ResolvedIdentityName
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip29RelayGroups.GroupId
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Buzz's contextual identity names for one relay group: each member's label, lengthened only when
 * someone else in the channel shares the name ("Alice’s Honey", "Honey (agent)", "Honey · 7xk2"). See
 * [IdentityNamePolicy] for the rule. Names are compared against the channel's members, so a label only
 * grows when a real collision exists there; a non-member author is resolved against the members plus
 * itself.
 *
 * Each channel's result is cached until its roster changes or any profile or agent profile arrives
 * ([invalidate], which bumps [version]); the UI can collect [version] to redraw.
 */
object BuzzIdentityNames {
    private val mutableVersion = MutableStateFlow(0L)

    /** Bumped whenever a name, agent profile or owner may have changed. */
    val version: StateFlow<Long> = mutableVersion

    /** A kind-0 or kind-10100 landed; names computed so far may be stale. */
    fun invalidate() {
        mutableVersion.value = mutableVersion.value + 1
    }

    private class Entry(
        val members: Set<HexKey>,
        val version: Long,
        val viewer: HexKey,
        val resolved: Map<HexKey, ResolvedIdentityName>,
        val outside: MutableMap<HexKey, ResolvedIdentityName?> = HashMap(),
    )

    private val lock = KmpLock()
    private val cache = HashMap<GroupId, Entry>()

    /**
     * The contextual label for [pubkey] in [channel] as [viewer] sees it, or null when there is no
     * name to disambiguate (an invalid key). The label equals the plain name unless it collides.
     */
    fun labelFor(
        channel: RelayGroupChannel,
        pubkey: HexKey,
        viewer: HexKey,
        users: (HexKey) -> User? = LocalCache::getUserIfExists,
        hasAgentProfile: (HexKey) -> Boolean = ::hasCachedAgentProfile,
    ): ResolvedIdentityName? {
        val members = channel.allMemberKeys()
        val version = mutableVersion.value
        val entry =
            lock.withLock {
                cache[channel.groupId]?.takeIf { it.members === members && it.version == version && it.viewer == viewer }
            } ?: Entry(members, version, viewer, resolve(members, viewer, users, hasAgentProfile)).also { lock.withLock { cache[channel.groupId] = it } }

        val key = pubkey.lowercase()
        if (key in entry.resolved) return entry.resolved[key]
        return lock.withLock { entry.outside[key] }
            ?: resolve(members + key, viewer, users, hasAgentProfile)[key].also { found -> lock.withLock { entry.outside[key] = found } }
    }

    /** The naming fact for [pubkey]: its profile name, and whether (and whose) agent it is. */
    fun factFor(
        pubkey: HexKey,
        users: (HexKey) -> User?,
        hasAgentProfile: (HexKey) -> Boolean = ::hasCachedAgentProfile,
    ): NamingIdentity {
        val user = users(pubkey)
        val info = user?.metadataOrNull()?.flow?.value
        val owner = info?.nipOaOwner
        return NamingIdentity(
            pubkey = pubkey,
            name = info?.info?.bestName()?.takeIf { IdentityNamePolicy.trim(it).isNotEmpty() } ?: user?.pubkeyDisplayHex() ?: pubkey.take(8),
            isAgent = owner != null || hasAgentProfile(pubkey),
            ownerPubkey = owner,
        )
    }

    /** Whether the cache holds a kind-10100 agent profile by [pubkey]. */
    fun hasCachedAgentProfile(pubkey: HexKey): Boolean = LocalCache.getAddressableNoteIfExists(AgentProfileEvent.createAddress(pubkey))?.event is AgentProfileEvent

    private fun resolve(
        candidates: Set<HexKey>,
        viewer: HexKey,
        users: (HexKey) -> User?,
        hasAgentProfile: (HexKey) -> Boolean,
    ): Map<HexKey, ResolvedIdentityName> {
        val facts = candidates.filter { isKey(it) }.map { factFor(it, users, hasAgentProfile) }
        // Owners outside the channel only lend their names ("Alice’s Honey"); they don't compete.
        // Listed first so a member's own fact stays the preferred one.
        val ownerFacts =
            facts
                .mapNotNull { it.ownerPubkey }
                .filter { it !in candidates }
                .distinct()
                .mapNotNull { owner ->
                    users(owner)
                        ?.metadataOrNull()
                        ?.flow
                        ?.value
                        ?.info
                        ?.bestName()
                        ?.takeIf { IdentityNamePolicy.trim(it).isNotEmpty() }
                        ?.let { NamingIdentity(owner, it) }
                }
        return IdentityNamePolicy.resolve(ownerFacts + facts, viewer = viewer.takeIf { isKey(it) }, candidates = facts.map { it.pubkey })
    }

    private fun isKey(value: String) = value.length == 64 && value.all { it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F' }

    /** Test-only. */
    fun clearForTesting() =
        lock.withLock {
            cache.clear()
            mutableVersion.value = 0
        }
}
