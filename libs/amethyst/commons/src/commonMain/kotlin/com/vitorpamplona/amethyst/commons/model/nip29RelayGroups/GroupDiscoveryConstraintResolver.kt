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

import com.vitorpamplona.amethyst.commons.model.topNavFeeds.IFeedTopNavPerRelayFilterSet
import com.vitorpamplona.amethyst.commons.model.topNavFeeds.allFollows.AllFollowsTopNavPerRelayFilterSet
import com.vitorpamplona.amethyst.commons.model.topNavFeeds.aroundMe.LocationTopNavPerRelayFilterSet
import com.vitorpamplona.amethyst.commons.model.topNavFeeds.global.GlobalTopNavPerRelayFilterSet
import com.vitorpamplona.amethyst.commons.model.topNavFeeds.hashtag.HashtagTopNavPerRelayFilterSet
import com.vitorpamplona.amethyst.commons.model.topNavFeeds.noteBased.allcommunities.AllCommunitiesTopNavPerRelayFilterSet
import com.vitorpamplona.amethyst.commons.model.topNavFeeds.noteBased.author.AuthorsTopNavPerRelayFilterSet
import com.vitorpamplona.amethyst.commons.model.topNavFeeds.noteBased.community.SingleCommunityTopNavPerRelayFilterSet
import com.vitorpamplona.amethyst.commons.model.topNavFeeds.noteBased.muted.MutedAuthorsTopNavPerRelayFilterSet
import com.vitorpamplona.amethyst.commons.model.topNavFeeds.relay.RelayTopNavPerRelayFilterSet
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl

/**
 * Resolve the selected top-nav filter into a per-relay [GroupDiscoveryConstraint] (the shared,
 * platform-agnostic matcher lives in `:commons`). The relays are exactly the relays the filter
 * routes to; each relay's constraint carries only that relay's slice of the follow/topic/geo set
 * (matching how the note feeds shard per relay). Community selections have no people/topic/geo
 * dimension, so they fall back to [GroupDiscoveryConstraint.AllGroups] — still constraining the
 * RELAY set, just not the people. Each branch mirrors what the datasource actually REQs for that
 * filter, so fetch and display agree.
 */
fun IFeedTopNavPerRelayFilterSet.toGroupConstraints(): Map<NormalizedRelayUrl, GroupDiscoveryConstraint> =
    when (this) {
        is GlobalTopNavPerRelayFilterSet -> set.keys.associateWith { GroupDiscoveryConstraint.AllGroups }
        is RelayTopNavPerRelayFilterSet -> mapOf(relayUrl to GroupDiscoveryConstraint.AllGroups)
        is AuthorsTopNavPerRelayFilterSet ->
            set.mapValues { (_, f) -> GroupDiscoveryConstraint.ByPeople(f.authors) }
        is HashtagTopNavPerRelayFilterSet ->
            set.mapValues { (_, f) -> GroupDiscoveryConstraint.ByHashtags(f.hashtags) }
        is LocationTopNavPerRelayFilterSet ->
            set.mapValues { (_, f) -> GroupDiscoveryConstraint.ByGeohashes(f.geotags) }
        is AllFollowsTopNavPerRelayFilterSet ->
            set.mapValues { (_, f) ->
                val lenses =
                    buildList {
                        f.authors?.takeIf { it.isNotEmpty() }?.let { add(GroupDiscoveryConstraint.ByPeople(it)) }
                        f.hashtags?.takeIf { it.isNotEmpty() }?.let { add(GroupDiscoveryConstraint.ByHashtags(it)) }
                        f.geotags?.takeIf { it.isNotEmpty() }?.let { add(GroupDiscoveryConstraint.ByGeohashes(it)) }
                    }
                if (lenses.isEmpty()) GroupDiscoveryConstraint.AllGroups else GroupDiscoveryConstraint.AnyOf(lenses)
            }
        // Muted-authors is a "show me the muted" lens: keep groups where a muted user is the relay
        // key / an admin / a member (matching the #p roster REQ that fetches them).
        is MutedAuthorsTopNavPerRelayFilterSet ->
            set.mapValues { (_, f) -> GroupDiscoveryConstraint.ByPeople(f.authors) }
        // Community selections carry no group-hosting dimension; show every group the resolved
        // relays host (matching the broad directory REQ). Empty for DVM/anything with no relays.
        is AllCommunitiesTopNavPerRelayFilterSet -> set.keys.associateWith { GroupDiscoveryConstraint.AllGroups }
        is SingleCommunityTopNavPerRelayFilterSet -> set.keys.associateWith { GroupDiscoveryConstraint.AllGroups }
        else -> emptyMap()
    }
