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
package com.vitorpamplona.amethyst.commons.model.nip92IMeta

import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip92IMeta.imetas

/**
 * When [content] is blank, returns [event]'s NIP-92 `imeta` URLs joined one per line so an attachment
 * carried only as an `imeta` tag (empty content) still renders. The shared media renderer only shows
 * media whose URL appears in the text, but some NIP-C7/Concord clients (e.g. Ditto/Soapbox Armada)
 * attach an image purely as an `imeta` tag and leave the content empty — symmetric to
 * [com.vitorpamplona.quartz.concord.cord03Channels.ChannelChat.imageMessage], which appends the URL
 * on send. Encrypted-blob key/nonce are already registered by URL, so the shared pipeline fetches and
 * decrypts them transparently.
 *
 * Returns [content] unchanged whenever it is non-blank (the common case) or [event] is null / carries
 * no imeta URLs, so any message that already has text is untouched.
 */
fun appendMissingImetaUrls(
    content: String,
    event: Event?,
): String {
    if (event == null || content.isNotBlank()) return content
    val urls = event.imetas().map { it.url }.filter { it.isNotBlank() }
    if (urls.isEmpty()) return content
    return urls.joinToString("\n")
}
