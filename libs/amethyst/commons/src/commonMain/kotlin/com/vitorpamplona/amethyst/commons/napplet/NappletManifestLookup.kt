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
package com.vitorpamplona.amethyst.commons.napplet

import com.vitorpamplona.amethyst.commons.model.cache.LocalCache
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip5aStaticWebsites.NamedSiteEvent
import com.vitorpamplona.quartz.nip5aStaticWebsites.RootSiteEvent
import com.vitorpamplona.quartz.nip5dNapplets.NappletManifest
import com.vitorpamplona.quartz.nip5dNapplets.named.NamedNappletEvent
import com.vitorpamplona.quartz.nip5dNapplets.root.RootNappletEvent

/**
 * Looks up the best-effort human title and icon URL for a napplet or nsite from the local cache.
 * Falls back to the d-identifier or [untitled] when no manifest is cached.
 */
fun resolveNappletMeta(
    author: String,
    identifier: String,
    untitled: String,
): Pair<String, String?> {
    val events =
        LocalCache
            .filter(
                Filter(
                    kinds = listOf(RootNappletEvent.KIND, NamedNappletEvent.KIND, RootSiteEvent.KIND, NamedSiteEvent.KIND),
                    authors = listOf(author),
                ),
            ).mapNotNull { it.event }
    val match =
        events.firstOrNull { ev ->
            when (ev) {
                is NamedNappletEvent -> ev.identifier() == identifier
                is RootNappletEvent -> identifier.isEmpty()
                is NamedSiteEvent -> ev.identifier() == identifier
                is RootSiteEvent -> identifier.isEmpty()
                else -> false
            }
        }
    val title =
        when (match) {
            is NappletManifest -> match.title()
            is RootSiteEvent -> match.title()
            is NamedSiteEvent -> match.title()
            else -> null
        }?.ifBlank { null } ?: identifier.ifBlank { untitled }
    val iconUrl =
        when (match) {
            is NappletManifest -> null // Current icons are verified blobs, never remote URLs.
            is RootSiteEvent -> match.icon()
            is NamedSiteEvent -> match.icon()
            else -> null
        }?.ifBlank { null }
    return title to iconUrl
}
