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
package com.vitorpamplona.amethyst.commons.podcasts

import com.vitorpamplona.amethyst.commons.richtext.RichTextParser
import com.vitorpamplona.amethyst.commons.richtext.mimeTypeMap
import com.vitorpamplona.amethyst.commons.richtext.normalizeMimeType
import com.vitorpamplona.quartz.nipXXPodcasting20.trailer.Podcasting20TrailerEvent
import com.vitorpamplona.quartz.podcasts.PodcastAudio

/**
 * What a Podcasting 2.0 trailer (kind 30055) plays: the file in its `url`, with the mime its `type`
 * tag says (the `<podcast:trailer>` element's `type`), else the one the file's extension names.
 *
 * Podcast apps upload trailers to Blossom, which names an `audio/mpeg` file `.mpga` or gives it no
 * extension at all, so the declared `type` is what tells the player it is audio. A `type` that is
 * no mime (a bare `mpga` is repaired, a `full` is not) falls back to the extension like a missing one.
 */
fun Podcasting20TrailerEvent.trailerAudio(): PodcastAudio? {
    val url = url()?.trim()?.ifEmpty { null } ?: return null
    val declared = normalizeMimeType(mimeType()?.trim()?.ifEmpty { null })?.takeIf { it.contains('/') }
    return PodcastAudio(url, declared ?: extensionMimeType(url))
}

private fun extensionMimeType(url: String): String? {
    if (!RichTextParser.isAudioUrl(url)) return null
    val path = url.substringBefore('#').substringBefore('?')
    return mimeTypeMap[path.substringAfterLast('.').lowercase()]
}
