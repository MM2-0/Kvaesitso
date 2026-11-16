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
package com.vitorpamplona.amethyst.commons.moderation

import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip18Reposts.GenericRepostEvent
import com.vitorpamplona.quartz.nip18Reposts.RepostEvent

/**
 * Returns the event whose body a renderer would actually display.
 *
 * For kind 6 / kind 16 reposts that is the *wrapped* inner event — which
 * the consume pipeline already resolves onto [Note.replyTo]. Falls back to
 * the slower [RepostEvent.containedPost] / [GenericRepostEvent.containedPost]
 * JSON decode only when the cache hasn't materialised the reply yet; both
 * already null-return on parse failure.
 *
 * For non-repost events, the displayed event is the note's own event.
 *
 * Returns null when there is no event at all (placeholder not yet
 * hydrated).
 */
fun Note.displayedEvent(): Event? {
    val ev = this.event ?: return null
    return when (ev) {
        is RepostEvent -> replyTo?.lastOrNull()?.event ?: ev.containedPost()
        is GenericRepostEvent -> replyTo?.lastOrNull()?.event ?: ev.containedPost()
        else -> ev
    }
}
