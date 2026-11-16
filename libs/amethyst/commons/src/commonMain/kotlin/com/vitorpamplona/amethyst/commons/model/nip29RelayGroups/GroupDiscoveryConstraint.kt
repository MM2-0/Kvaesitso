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
package com.vitorpamplona.amethyst.commons.model.nip29RelayGroups

import com.vitorpamplona.quartz.nip01Core.core.HexKey

/**
 * How a NIP-29 group qualifies for a discovery view, evaluated against a [RelayGroupChannel].
 *
 * A group's kind-39000 is relay-signed, so the naive "author = a follow" match the note feeds use
 * never fires (the author is the relay). But the *people* dimension is still meaningful — it lives
 * in the roster events and the relay's own key:
 *  - the relay signing the 39000 may be a followed key ([ByPeople] relay-key path);
 *  - a follow may be a group **admin** (kind 39001) or **member** (kind 39002).
 *
 * Topics/geo aren't defined by NIP-29, but a cooperating relay can copy requested `t`/`g` tags onto
 * the 39000 ([ByHashtags]/[ByGeohashes]).
 *
 * This is the platform-agnostic match; front ends map their own top-nav filter onto these (see the
 * Android `toGroupConstraints` mapping) and Desktop/CLI can reuse the same matcher.
 */
sealed interface GroupDiscoveryConstraint {
    fun matches(channel: RelayGroupChannel): Boolean

    /** Every group the relay hosts (Global, or a specific relay chip). */
    data object AllGroups : GroupDiscoveryConstraint {
        override fun matches(channel: RelayGroupChannel) = channel.event != null
    }

    /** I follow the relay key, or a follow is an admin/member of the group. */
    data class ByPeople(
        val pubkeys: Set<HexKey>,
    ) : GroupDiscoveryConstraint {
        override fun matches(channel: RelayGroupChannel): Boolean {
            if (pubkeys.isEmpty()) return false
            val relayKey = channel.event?.pubKey
            return (relayKey != null && relayKey in pubkeys) ||
                channel.admins.any { it.pubKey in pubkeys } ||
                channel.members.any { it in pubkeys }
        }
    }

    /** The 39000 carries a `t` tag matching one of these topics (compared lowercase). */
    data class ByHashtags(
        val hashtags: Set<String>,
    ) : GroupDiscoveryConstraint {
        private val lower = hashtags.mapTo(mutableSetOf()) { it.lowercase() }

        override fun matches(channel: RelayGroupChannel): Boolean {
            if (lower.isEmpty()) return false
            return channel.event?.hashtags()?.any { it.lowercase() in lower } == true
        }
    }

    /** The 39000 carries a `g` tag matching one of these geohashes (mip-map prefixes intersect). */
    data class ByGeohashes(
        val geohashes: Set<String>,
    ) : GroupDiscoveryConstraint {
        private val lower = geohashes.mapTo(mutableSetOf()) { it.lowercase() }

        override fun matches(channel: RelayGroupChannel): Boolean {
            if (lower.isEmpty()) return false
            return channel.event?.geohashes()?.any { it.lowercase() in lower } == true
        }
    }

    /** All-follows big-OR: a group matches if ANY of its people/topic/geo lenses match. */
    data class AnyOf(
        val constraints: List<GroupDiscoveryConstraint>,
    ) : GroupDiscoveryConstraint {
        override fun matches(channel: RelayGroupChannel) = constraints.any { it.matches(channel) }
    }
}
