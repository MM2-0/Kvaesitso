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
package com.vitorpamplona.amethyst.commons.nip34Git.title

import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.amethyst.commons.model.cache.LocalCache
import com.vitorpamplona.amethyst.commons.nip34Git.authority.GitAuthority
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.tags.hashtags.hashtags
import com.vitorpamplona.quartz.nip32Labeling.LabelEvent
import com.vitorpamplona.quartz.nip34Git.issue.GitIssueEvent
import com.vitorpamplona.quartz.nip34Git.patch.GitPatchEvent
import com.vitorpamplona.quartz.nip34Git.pr.GitPullRequestEvent

/** One authorised title change of an issue, patch or PR: [from] (the title before) to [to]. */
class GitRename(
    val event: LabelEvent,
    val from: String?,
    val to: String,
)

/**
 * The title and labels an issue, patch or PR shows once its after-the-fact NIP-32 edits (kind
 * 1985) apply, counted only when its author or a confirmed repository member at the time signed
 * them ([GitAuthority.isMemberAt]), as gitworkshop does. Both come from what the cache already
 * keeps on the item's [Note]: renames (`#subject`) are among its `replies`, `#t` labels in its
 * `labels`, each with the label event that added it.
 */
object GitItemTitles {
    /** Patch-series markers that ride in `t` tags but are not labels. */
    private val SERIES_TAGS = setOf("root", "root-revision", "revision-root", "cover-letter")

    /** The item's own title: an issue's or PR's `subject`, a patch's description or `Subject:` line. */
    fun originalTitleOf(item: Event): String? =
        when (item) {
            is GitIssueEvent -> item.subject()
            is GitPullRequestEvent -> item.subject()
            is GitPatchEvent -> item.title()
            else -> null
        }?.takeIf { it.isNotBlank() }

    /**
     * The authorised renames of [item], oldest first: by `created_at`, then by event id, so at equal
     * timestamps the higher id is the later one and its title shows. That is gitworkshop's order for
     * renames and cover notes (`a.createdAt - b.createdAt || a.id.localeCompare(b.id)`); it picks the
     * lower id for statuses and PR updates instead (NIP-01), and so does Amethyst for those.
     */
    fun renamesOf(
        item: Note,
        authority: GitAuthority = LocalCache.gitAuthority,
    ): List<GitRename> {
        val itemEvent = item.event ?: return emptyList()
        return renamesOf(itemEvent, item.replies.mapNotNull { it.event as? LabelEvent }, authority)
    }

    /** [renamesOf] for labels held outside the cache (`amy`, tests). */
    fun renamesOf(
        itemEvent: Event,
        labels: Collection<LabelEvent>,
        authority: GitAuthority,
    ): List<GitRename> {
        var previous = originalTitleOf(itemEvent)
        return labels
            .filter { it.subjectEdit() != null && authority.isMemberAt(it.pubKey, itemEvent, it.createdAt) }
            .sortedWith(compareBy<LabelEvent> { it.createdAt }.thenBy { it.id })
            .map { label ->
                val to = label.subjectEdit()!!
                GitRename(label, previous, to).also { previous = to }
            }
    }

    /** [titleOf] for labels held outside the cache. */
    fun titleOf(
        itemEvent: Event,
        labels: Collection<LabelEvent>,
        authority: GitAuthority,
    ): String? = renamesOf(itemEvent, labels, authority).lastOrNull()?.to ?: originalTitleOf(itemEvent)

    /** The title [item] shows: its latest authorised rename, else its own. */
    fun titleOf(
        item: Note,
        authority: GitAuthority = LocalCache.gitAuthority,
    ): String? = renamesOf(item, authority).lastOrNull()?.to ?: item.event?.let { originalTitleOf(it) }

    /**
     * The labels [item] shows, sorted: its own `t` tags (patch-series markers aside) plus every `#t`
     * label an authorised label event added.
     */
    fun labelsOf(
        item: Note,
        authority: GitAuthority = LocalCache.gitAuthority,
    ): List<String> {
        val itemEvent = item.event ?: return emptyList()
        val own = itemEvent.tags.hashtags().filter { it !in SERIES_TAGS }
        val added =
            item.labels
                .filter { (_, labelers) ->
                    labelers.any { labeler -> labeler.event?.let { authority.isMemberAt(it.pubKey, itemEvent, it.createdAt) } == true }
                }.keys
        return (own + added).filter { it.isNotBlank() }.distinct().sorted()
    }
}
