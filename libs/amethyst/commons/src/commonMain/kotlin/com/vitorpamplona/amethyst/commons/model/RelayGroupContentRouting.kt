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

import com.vitorpamplona.quartz.nip29RelayGroups.GroupId

/**
 * A candidate group channel when routing a stray group-scoped content event: its [key] and whether it is a
 * confirmed host (has received relay-signed state). See [redirectStrayRelayGroupContent].
 */
data class RelayGroupTargetCandidate(
    val key: GroupId,
    val hasRelaySignedState: Boolean,
)

/**
 * Resolves the **serving-relay hazard**. A group-scoped content event (kind-9 chat, poll, kind-11 thread…)
 * is keyed to its group channel by the relay that served it, because a NIP-29 event doesn't carry its host
 * relay. That is correct for the group's own host-pinned subscriptions, but a message resolved from a
 * **non-host** relay — e.g. a quoted kind-9 fetched by id during missing-event resolution — would be filed
 * under a channel keyed to that stranger relay, one the group's own screens never read, so the message
 * silently vanishes.
 *
 * Called only when there is **no** channel keyed to the serving relay for this group id (the fast, common
 * path attaches directly and never gets here). It picks the group's single confirmed **host** channel — one
 * that has received relay-signed state — to attach the stray to instead. Returns that host key, or null when
 * there is no single confirmed host (a genuinely new group on the serving relay, or an id ambiguous across
 * several hosts), in which case the caller keeps the serving-relay key as today's best effort.
 *
 * A phantom channel (one minted from an earlier stray) never has relay-signed state, so it can never be
 * chosen here — the redirect only ever lands on a real host, never on another phantom. This makes the fix
 * strictly safe: it can redirect a stray to a known host, but never divert a message away from one.
 */
fun redirectStrayRelayGroupContent(candidates: List<RelayGroupTargetCandidate>): GroupId? = candidates.filter { it.hasRelaySignedState }.singleOrNull()?.key
