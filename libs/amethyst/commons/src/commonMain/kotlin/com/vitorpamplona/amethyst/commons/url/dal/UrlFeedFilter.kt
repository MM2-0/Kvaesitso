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
package com.vitorpamplona.amethyst.commons.url.dal

import com.vitorpamplona.amethyst.commons.feeds.AdditiveFeedFilter
import com.vitorpamplona.amethyst.commons.feeds.sortedByDefaultFeedOrder
import com.vitorpamplona.amethyst.commons.model.Account
import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.amethyst.commons.model.cache.LocalCache
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip22Comments.CommentEvent
import com.vitorpamplona.quartz.nip73ExternalIds.urls.UrlId
import com.vitorpamplona.quartz.nip84Highlights.HighlightEvent
import com.vitorpamplona.quartz.nip84Highlights.parse.UrlTrackerCleaner

class UrlFeedFilter(
    url: String,
    val relays: Set<NormalizedRelayUrl>,
    val account: Account,
    val cache: LocalCache,
) : AdditiveFeedFilter<Note>() {
    val normalizedUrl = UrlId.toScopeOrNull(url).orEmpty()

    // The host a highlight's `r` must mention, lowercased once: a substring test that rejects
    // nearly every other highlight before any URL is parsed.
    private val normalizedHost = hostOf(normalizedUrl)

    override fun feedKey(): String = account.userProfile().pubkeyHex + "-" + normalizedUrl

    override fun feed(): List<Note> {
        val notes =
            cache.notes.filterIntoSet { _, it ->
                acceptableEvent(it, normalizedUrl)
            }

        return sort(notes)
    }

    override fun applyFilter(newItems: Set<Note>): Set<Note> = newItems.filterTo(HashSet()) { acceptableEvent(it, normalizedUrl) }

    fun acceptableEvent(
        it: Note,
        url: String,
    ): Boolean =
        url.isNotEmpty() &&
            acceptableViaScope(it.event, url) &&
            !it.isHiddenFor(account.hiddenUsers.flow.value) &&
            account.isAcceptable(it)

    /**
     * [scope] is already normalized ([normalizedUrl]). It used to be normalized again here, once
     * per note, and [feed] runs this over every note in the cache: about a microsecond of URL
     * parsing each, so ~100 ms per refresh on a 100k-note cache before any event was looked at.
     * Type checks come first so the other kinds cost nothing.
     */
    fun acceptableViaScope(
        event: Event?,
        scope: String,
    ): Boolean =
        when (event) {
            is CommentEvent -> {
                event.isTaggedScope(scope, UrlId::match)
            }

            is HighlightEvent -> {
                val reference = event.inUrl()
                reference != null &&
                    (normalizedHost == null || reference.contains(normalizedHost, ignoreCase = true)) &&
                    isSamePage(reference, scope)
            }

            else -> {
                false
            }
        }

    companion object {
        /**
         * Whether a highlight's `r` address names the page [scope] is about. The address is the
         * author's spelling, so it is compared after the same normalization the scope went
         * through, without tracking parameters, and ignoring a trailing slash.
         */
        fun isSamePage(
            reference: String,
            scope: String,
        ): Boolean {
            val target = scope.trimEnd('/')
            val normalized = UrlId.toScopeOrNull(reference) ?: return false
            if (normalized.trimEnd('/') == target) return true
            // Only parse twice when there is a query to strip trackers from.
            if ('?' !in reference) return false
            val cleaned = UrlId.toScopeOrNull(UrlTrackerCleaner.clean(reference)) ?: return false
            return cleaned.trimEnd('/') == target
        }

        private fun hostOf(scope: String): String? {
            val start = scope.indexOf("://").takeIf { it >= 0 }?.plus(3) ?: return null
            val end = scope.indexOfAny(charArrayOf('/', '?', '#', ':'), start).let { if (it < 0) scope.length else it }
            return scope.substring(start, end).removePrefix("www.").ifEmpty { null }
        }
    }

    override fun sort(items: Set<Note>): List<Note> = items.sortedByDefaultFeedOrder()
}
