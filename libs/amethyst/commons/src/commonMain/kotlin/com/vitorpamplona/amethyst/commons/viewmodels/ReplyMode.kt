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
package com.vitorpamplona.amethyst.commons.viewmodels

/**
 * How a chat reply is delivered, chosen by the user at send time.
 *
 * - [INLINE] — a normal chat message that references its parent and stays in the
 *   main timeline (the chat protocol's native reply: NIP-C7 kind-9 `q` quote,
 *   kind-42 reply, kind-9 `+h` reply, kind-14 reply). The default.
 * - [MINICHAT] — a kind-1111 NIP-22 comment rooted at the parent message, pulled
 *   out of the timeline into a "chat within a chat" (minichat) opened from the
 *   parent. Wire-compatible with Soapbox Armada's thread replies.
 */
enum class ReplyMode {
    INLINE,
    MINICHAT,
}
