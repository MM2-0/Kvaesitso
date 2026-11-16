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
package com.vitorpamplona.amethyst.commons.model

import com.vitorpamplona.amethyst.commons.model.cache.ICacheProvider
import com.vitorpamplona.quartz.nip10Notes.BaseThreadedEvent
import com.vitorpamplona.quartz.nip22Comments.CommentEvent
import com.vitorpamplona.quartz.nip72ModCommunities.definition.CommunityDefinitionEvent
import com.vitorpamplona.quartz.nip72ModCommunities.isTopLevelCommunityPost

/**
 * The note to render as the "replying to" card above [note], or null when there is no parent to
 * show. Prefers the parent the event itself names, falling back to the last resolved reply target.
 *
 * NIP-72 communities are deliberately never returned. A community is not a note a post replies
 * to in any renderable sense: it is already named in the post's header and gets its own context
 * card. Two shapes have to be caught for that to hold:
 *
 *  - the parent resolves to the community's [com.vitorpamplona.amethyst.commons.model.AddressableNote];
 *  - the post is a top-level community post whose bridged tags point at the definition event by
 *    *id* (mostr does this), which resolves to a plain [Note] that can never load, because
 *    addressable events are cached by address and not by id.
 *
 * Both used to slip through a `note.event?.kind != CommunityDefinitionEvent.KIND` test, because an
 * uncached note has a null event and `null != 34550`. The result was an empty card where the
 * parent context belongs.
 */
fun replyingDirectlyTo(
    note: Note,
    cache: ICacheProvider,
): Note? {
    val event = note.event

    if (event is CommentEvent && event.isTopLevelCommunityPost()) return null

    val direct =
        (event as? BaseThreadedEvent)
            ?.replyingToAddressOrEvent()
            ?.let { cache.getNoteIfExists(it) }
            ?.takeIf { cache.getAnyChannel(it) == null && !it.isCommunityDefinition() }

    return direct ?: note.replyTo?.lastOrNull { !it.isCommunityDefinition() }
}

/**
 * True for a community definition whether or not its event has arrived. See [Note.kindOrNull] for
 * why the address has to be consulted instead of the event.
 */
fun Note.isCommunityDefinition() = isKind(CommunityDefinitionEvent.KIND)
