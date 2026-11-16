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
package com.vitorpamplona.amethyst.commons.model.chats

import com.vitorpamplona.quartz.buzz.stream.StreamMessageV2Event
import com.vitorpamplona.quartz.buzz.stream.isBroadcast
import com.vitorpamplona.quartz.buzz.threading.buzzThreadReply
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip22Comments.CommentEvent
import com.vitorpamplona.quartz.nipC7Chats.ChatEvent

/**
 * Whether [event] is a **minichat thread reply** — a reply that lives inside the thread opened from
 * its parent message, NOT as a flat sibling in the main timeline. Three dialects express the same idea:
 *
 * - **NIP-28/NIP-29 (public chats, Concord)**: a kind-1111 [CommentEvent].
 * - **Buzz workspaces, current**: a kind-9 [ChatEvent] carrying a NIP-10 `reply`-marked `e` tag and
 *   NOT flagged `broadcast`. This is what every live Buzz client (and Amethyst) writes —
 *   `build_message` emits `["e", id, "", "reply"]` for a direct reply and `["e", root, "", "root"]` +
 *   `["e", parent, "", "reply"]` for a nested one, plus `["broadcast", "1"]` when the reply should
 *   also show in the channel.
 * - **Buzz workspaces, legacy**: a kind-40002 [StreamMessageV2Event] with the same markers and NOT
 *   flagged `broadcast`. Nothing in Buzz writes 40002 any more (their own NOSTR.md grades it
 *   "Buzz-only — no standard NIP-29 client renders these"), but events exist in the wild from the
 *   10002 -> 40001 -> 40002 migration, and Amethyst itself wrote some, so it stays readable.
 *
 * ### Why a marked `e` and not `q`
 *
 * NIP-C7 gives kind 9 exactly one reply mechanism — `["q", <id>, <relay>, <pubkey>]` — and never
 * mentions `e` at all. So a marked `e` carries no C7 meaning and is free to denote a *thread* reply,
 * which is precisely how Buzz uses it. The marker is what separates the cases: WhiteNoise/Marmot
 * thread kind-9 chat with a **plain, unmarked** `e`, which is an in-chat reply and must keep rendering
 * as a quote bubble in the timeline — so matching on the `reply` marker (never on the bare tag) leaves
 * that dialect untouched.
 *
 * A `broadcast=1` reply is an inline timeline sibling ("also send to channel") on either kind, matching
 * block/buzz's `isBroadcastReply`, which the relay reads the same way whatever the kind.
 *
 * The timeline filter drops these (they belong in the minichat), the minichat count counts them, and
 * the minichat feed shows them — so all three agree on one definition.
 */
fun isMinichatReply(event: Event?): Boolean =
    when (event) {
        is CommentEvent -> true
        is ChatEvent -> !event.tags.isBroadcast() && event.tags.buzzThreadReply() != null
        is StreamMessageV2Event -> !event.isBroadcast() && event.tags.buzzThreadReply() != null
        else -> false
    }
