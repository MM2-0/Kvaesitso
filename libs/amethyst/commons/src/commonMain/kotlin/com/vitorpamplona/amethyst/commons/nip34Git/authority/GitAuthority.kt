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
package com.vitorpamplona.amethyst.commons.nip34Git.authority

import androidx.compose.runtime.Stable
import com.vitorpamplona.quartz.nip01Core.core.Address
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip34Git.issue.GitIssueEvent
import com.vitorpamplona.quartz.nip34Git.patch.GitPatchEvent
import com.vitorpamplona.quartz.nip34Git.pr.GitPullRequestEvent
import com.vitorpamplona.quartz.nip34Git.pr.GitPullRequestUpdateEvent
import com.vitorpamplona.quartz.nip34Git.repository.GitRepositoryEvent
import com.vitorpamplona.quartz.nip34Git.repository.GitRepositoryMembership
import com.vitorpamplona.quartz.nip34Git.repository.repositoryAddresses

/**
 * Who may change an issue's, patch's or PR's state, from the repository announcements in the
 * cache. Anyone can publish a status, a PR update, a label or a cover note for any item; only the
 * item's author and the repository's confirmed members ([GitRepositoryMembership]) change what
 * the item shows. Everything else is at most a proposal.
 *
 * An event counts by the roles when it was signed ([isMemberAt], [isMaintainerAt]): a merge by
 * someone who became a maintainer later is still a proposal, as gitworkshop judges it. Who may
 * act now ([memberActorsOf]) uses today's membership. While a repository's announcement has not
 * loaded, its coordinate's own pubkey stands in for its members, so the owner's statuses show
 * without a flash.
 */
@Stable
class GitAuthority(
    private val membershipOf: (repository: Address) -> GitRepositoryMembership?,
    private val announcementOf: (repository: Address) -> GitRepositoryEvent?,
) {
    /**
     * The membership of the repository [repository] belongs to, seen from its lead (the component's
     * anchor, whose role records history prefers), or null while the announcement is unknown.
     */
    fun membership(repository: Address): GitRepositoryMembership? = membershipOf(repository)

    /**
     * Whether the cache can judge changes to [item]: its first repository's announcement has loaded.
     * Until then [isMemberAt] lets only the coordinate's owner through, which drops a confirmed
     * co-maintainer's change; callers that cannot wait for it (a notification) let it through.
     */
    fun canJudge(item: Event): Boolean = repositoriesOf(item).firstOrNull()?.let { membership(it) } != null

    /**
     * The coordinates a change to [repository] is filed under: every confirmed member's, as
     * gitworkshop tags them, so whichever announcement a client opens finds it. Just
     * [repository] while its announcements are unknown.
     */
    fun coordinatesToTag(repository: Address): List<Address> = membership(repository)?.confirmedCoordinates()?.ifEmpty { null } ?: listOf(repository)

    /** [coordinatesToTag] for the repository [item] is filed against, plus the item's own coordinates. */
    fun coordinatesToTag(item: Event): List<Address> {
        val own = repositoriesOf(item)
        val primary = own.firstOrNull() ?: return emptyList()
        return (coordinatesToTag(primary) + own).distinct()
    }

    /** The repository's earliest unique commit, from the first confirmed announcement that names one. */
    fun earliestUniqueCommit(repository: Address): String? = membership(repository)?.confirmedAnnouncements?.firstNotNullOfOrNull { it.earliestUniqueCommit() }

    /**
     * The announcements needed to judge items filed under [repositories]: theirs, then those of
     * everyone they name (maintainers and role subjects, ended roles included) and so on, as far as
     * the cache has reached. Load them on screen with the event finder; each one that arrives
     * names the next, and the repository relays it carries are the hints to find them.
     */
    fun announcementsToLoad(repositories: List<Address>): Set<Address> {
        val result = LinkedHashSet<Address>()
        val queue = ArrayDeque(repositories.filter { it.kind == GitRepositoryEvent.KIND })
        while (queue.isNotEmpty()) {
            val address = queue.removeFirst()
            if (!result.add(address)) continue
            val announcement = announcementOf(address) ?: continue
            announcement.namedPubKeys().forEach { queue.add(Address(GitRepositoryEvent.KIND, it, address.dTag)) }
        }
        return result
    }

    /**
     * Whether [pubKey] could set status, labels, subject edits and cover notes on [item] at
     * [createdAt]: its author always, else a confirmed member of one of its repositories then.
     */
    fun isMemberAt(
        pubKey: HexKey,
        item: Event,
        createdAt: Long,
    ): Boolean =
        pubKey == item.pubKey ||
            repositoriesOf(item).any { repository ->
                val membership = membership(repository)
                if (membership == null) repository.pubKeyHex == pubKey else pubKey in membership.membersAt(createdAt)
            }

    /** Whether [pubKey] could move [item]'s tip (a PR update) at [createdAt]: its author, or a maintainer then. */
    fun isMaintainerAt(
        pubKey: HexKey,
        item: Event,
        createdAt: Long,
    ): Boolean =
        pubKey == item.pubKey ||
            repositoriesOf(item).any { repository ->
                val membership = membership(repository)
                if (membership == null) repository.pubKeyHex == pubKey else pubKey in membership.maintainersAt(createdAt)
            }

    /**
     * Who may set status, labels, subject edits and cover notes on an item by [itemAuthor] filed
     * under [repositories]: the author plus every confirmed member of those repositories.
     */
    fun memberActors(
        itemAuthor: HexKey,
        repositories: List<Address>,
    ): Set<HexKey> =
        buildSet {
            add(itemAuthor)
            repositories.forEach { repository ->
                val membership = membership(repository)
                if (membership == null) add(repository.pubKeyHex) else addAll(membership.confirmedMembers)
            }
        }

    /** Who may move a PR's tip with an update (kind 1619): the author plus confirmed maintainers. */
    fun maintainerActors(
        itemAuthor: HexKey,
        repositories: List<Address>,
    ): Set<HexKey> =
        buildSet {
            add(itemAuthor)
            repositories.forEach { repository ->
                val membership = membership(repository)
                if (membership == null) add(repository.pubKeyHex) else addAll(membership.confirmedMaintainers)
            }
        }

    /** Whether [pubKey] is a confirmed maintainer of a repository [item] is filed against (not merely its author). */
    fun isRepositoryMaintainer(
        pubKey: HexKey,
        item: Event,
    ): Boolean =
        repositoriesOf(item).any { repository ->
            membership(repository)?.isMaintainer(pubKey) ?: (repository.pubKeyHex == pubKey)
        }

    /** [memberActors] of [item], for an issue, patch or PR event. */
    fun memberActorsOf(item: Event): Set<HexKey> = memberActors(item.pubKey, repositoriesOf(item))

    /** [maintainerActors] of [item]. */
    fun maintainerActorsOf(item: Event): Set<HexKey> = maintainerActors(item.pubKey, repositoriesOf(item))

    /**
     * Whether [change], a status (1630-1633) or a PR update (1619), counts for [item], by the roles
     * when it was signed. Without the item its author is unknown, so only members of the
     * repositories [change] names count: a stranger's status about an item we haven't seen is not
     * trusted.
     */
    fun isAuthorisedChange(
        change: Event,
        item: Event?,
    ): Boolean {
        val maintainersOnly = change is GitPullRequestUpdateEvent
        if (item != null) {
            return if (maintainersOnly) isMaintainerAt(change.pubKey, item, change.createdAt) else isMemberAt(change.pubKey, item, change.createdAt)
        }
        return change.tags.repositoryAddresses().any { repository ->
            val membership = membership(repository)
            when {
                membership == null -> repository.pubKeyHex == change.pubKey
                maintainersOnly -> change.pubKey in membership.maintainersAt(change.createdAt)
                else -> change.pubKey in membership.membersAt(change.createdAt)
            }
        }
    }

    companion object {
        val EMPTY = GitAuthority({ null }, { null })

        /**
         * An authority over [announcements] (by identifier, then author) held outside a cache, for
         * tests and tools: every announcement's membership is resolved once, up front.
         */
        fun of(announcements: Map<String, Map<HexKey, GitRepositoryEvent>>): GitAuthority {
            val memberships =
                buildMap {
                    announcements.forEach { (identifier, byAuthor) ->
                        byAuthor.keys.forEach { author ->
                            GitRepositoryMembership.resolveFromLead(author, identifier, byAuthor)?.let {
                                put(Address(GitRepositoryEvent.KIND, author, identifier), it)
                            }
                        }
                    }
                }
            return GitAuthority({ memberships[it] }, { announcements[it.dTag]?.get(it.pubKeyHex) })
        }

        /** The kinds whose state the authority rules govern: issues, patches and pull requests. */
        val ITEM_KINDS = listOf(GitIssueEvent.KIND, GitPatchEvent.KIND, GitPullRequestEvent.KIND)

        /** Every repository coordinate an issue, patch or PR is filed against. */
        fun repositoriesOf(item: Event): List<Address> =
            when (item) {
                is GitIssueEvent -> item.repositoryAddresses()
                is GitPatchEvent -> item.repositoryAddresses()
                is GitPullRequestEvent -> item.repositoryAddresses()
                else -> emptyList()
            }
    }
}
