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
package com.vitorpamplona.amethyst.commons.nip34Git.pr

import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.amethyst.commons.nip34Git.authority.GitAuthority
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.tags.hashtags.HashtagTag
import com.vitorpamplona.quartz.nip34Git.patch.GitPatchEvent
import com.vitorpamplona.quartz.nip34Git.pr.GitPullRequestEvent

/**
 * The patches of a series, in the order `git am` applies them: the root, then each patch that
 * replies to the one before it (`git send-email`'s and ngit's threading). Only the root author's
 * patches count, and a cover letter is not a patch. A reply that breaks the chain (a missing
 * patch, a fork in the thread) is left out with everything after it, so a series is never
 * applied with a hole in it.
 */
object GitPatchSeries {
    fun of(root: Note): List<GitPatchEvent> {
        val first = root.event as? GitPatchEvent ?: return emptyList()
        // ngit roots every patch at the first; a client that only marks the `reply` threads each
        // patch under the one before it, so the series is gathered down that chain too.
        val found = LinkedHashMap<String, GitPatchEvent>()
        val queue = ArrayDeque(listOf(root))
        while (queue.isNotEmpty()) {
            queue.removeFirst().replies.forEach { reply ->
                val patch = reply.event as? GitPatchEvent ?: return@forEach
                if (patch.pubKey != first.pubKey || patch.id == first.id || patch.id in found) return@forEach
                if (patch.isCoverLetter() || patch.isRootRevision()) return@forEach
                val rootId = patch.seriesRootId()
                if (rootId != null && rootId != first.id) return@forEach
                found[patch.id] = patch
                queue.addLast(reply)
            }
        }
        return chain(first, found.values)
    }

    /**
     * The newest revision of the patch series rooted at [root]: a root patch tagged `root-revision`
     * (or a pull request tagged so, when the series was upgraded to one) that replies to [root],
     * signed by [root]'s author or a maintainer. Null when the series was never revised. ngit and
     * gitworkshop merge this one: the original is what the thread, its title and its status hang on.
     */
    fun latestRevision(
        root: Event,
        candidates: Collection<Event>,
        authority: GitAuthority,
    ): Event? =
        candidates
            .filter { it.id != root.id && isRevisionOf(it, root) && (it.pubKey == root.pubKey || authority.isMaintainerAt(it.pubKey, root, it.createdAt)) }
            .maxWithOrNull(compareBy<Event> { it.createdAt }.thenByDescending { it.id })

    /** [latestRevision] among the events threaded onto [root] in the cache. */
    fun latestRevision(
        root: Note,
        authority: GitAuthority,
    ): Note? {
        val event = root.event ?: return null
        val revision = latestRevision(event, root.replies.mapNotNull { it.event }, authority) ?: return null
        return root.replies.firstOrNull { it.idHex == revision.id }
    }

    private fun isRevisionOf(
        event: Event,
        root: Event,
    ): Boolean {
        val tagged = event.tags.any { HashtagTag.isTagged(it, GitPatchEvent.ROOT_REVISION) || HashtagTag.isTagged(it, GitPatchEvent.REVISION_ROOT) }
        return when (event) {
            is GitPatchEvent -> tagged && event.previousPatchId() == root.id
            is GitPullRequestEvent -> tagged && event.rootPatchId() == root.id
            else -> false
        }
    }

    /** [first] followed by the patches of [rest] that chain from it, each replying to the previous. */
    fun chain(
        first: GitPatchEvent,
        rest: Collection<GitPatchEvent>,
    ): List<GitPatchEvent> {
        // A series threaded on its root alone (no `reply` markers) is in the order it was sent.
        if (rest.none { it.previousPatchId() != null && it.previousPatchId() != first.id }) {
            if (rest.size > 1) return listOf(first) + rest.sortedWith(compareBy<GitPatchEvent> { it.createdAt }.thenBy { it.id })
        }
        val byPrevious = rest.groupBy { it.previousPatchId() ?: first.id }
        val result = arrayListOf(first)
        val seen = hashSetOf(first.id)
        while (true) {
            val next = byPrevious[result.last().id]?.singleOrNull() ?: break
            if (!seen.add(next.id)) break
            result.add(next)
        }
        return result
    }
}
