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
package com.vitorpamplona.amethyst.commons.model.composer

import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.TagArrayBuilder
import com.vitorpamplona.quartz.nip01Core.hints.HintIndexer
import com.vitorpamplona.quartz.nip01Core.tags.hashtags.hashtags
import com.vitorpamplona.quartz.nip01Core.tags.people.PTag
import com.vitorpamplona.quartz.nip01Core.tags.references.references
import com.vitorpamplona.quartz.nip10Notes.content.findHashtags
import com.vitorpamplona.quartz.nip10Notes.content.findNostrUris
import com.vitorpamplona.quartz.nip10Notes.content.findURLs
import com.vitorpamplona.quartz.nip18Reposts.quotes.quotes

/**
 * The tags the note composer derives from a message's final text, the same for every
 * kind it posts: `t` for each #hashtag, `r` for each web link, and for each standalone
 * `nostr:` reference a NIP-18 `q` tag (events) or a `p` tag (profiles).
 *
 * Shared by `ShortNotePostViewModel`, the [com.vitorpamplona.amethyst.commons.actions]
 * builders and amy, so a note carries the same tags whichever front end wrote it.
 */
fun <T : Event> TagArrayBuilder<T>.messageTags(message: String) {
    hashtags(findHashtags(message))
    references(findURLs(message))
    quotes(findNostrUris(message))
}

/**
 * Everyone [NewMessageTagger] collected (cited users and the authors of cited notes) as
 * `p` tags, filling a missing relay hint from [hints] — the list the composer notifies.
 */
fun NewMessageTagger.pTagsWithHints(hints: HintIndexer): List<PTag>? =
    pTags?.map {
        val tag = it.toPTag()
        if (tag.relayHint == null) {
            tag.copy(relayHint = hints.hintsForKey(it.pubkeyHex).firstOrNull())
        } else {
            tag
        }
    }

/**
 * A NIP-18 quote post's text: [text] followed by a `nostr:` link to [quoted] (`nevent`,
 * or `naddr` for an addressable note, with the note's relay hint). The composer pre-fills
 * its box with this; amy posts it. Either way it then goes through the normal post path,
 * where [messageTags] adds the `q` tag and `NewMessageTagger` the quoted author's `p`.
 */
fun quoteMessage(
    text: String,
    quoted: Note,
): String = text + "\nnostr:${quoted.toNEvent()}"
