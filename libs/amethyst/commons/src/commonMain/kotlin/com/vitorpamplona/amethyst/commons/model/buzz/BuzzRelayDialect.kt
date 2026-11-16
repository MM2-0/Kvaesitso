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
package com.vitorpamplona.amethyst.commons.model.buzz

import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Tracks which relays speak the **Buzz dialect** of NIP-29 — `block/buzz` workspace
 * relays whose channel timelines carry Buzz-custom kinds (stream messages 40002,
 * edits 40003, diffs 40008, system rows 40099, canvas 40100, forum 45001/45003,
 * agent jobs 43001-43006, huddle cards 48100-48103) alongside plain kind-9 chat.
 *
 * Detection sources, in order of arrival:
 * 1. **Event shape** (implemented): the first Buzz-only channel kind consumed from a
 *    relay marks it (`LocalCache` calls [mark] before materializing the channel). A
 *    vanilla NIP-29 relay never serves those kinds, so there are no false positives.
 * 2. **NIP-11** (future): a relay announcing Buzz in its info document can be marked
 *    at connect time, before any event arrives.
 *
 * The registry decides which channel type `LocalCache` materializes for a group
 * (`BuzzWorkspaceChannel` vs plain `RelayGroupChannel`) and which timeline kinds the
 * group-chat REQ builders put on the wire for that relay. Marks only ever ADD
 * capability: un-marking is never needed because a marked relay provably served a
 * Buzz kind, and extra requested kinds are harmless on any relay.
 *
 * Like `LocalCache`, this is a process-wide singleton (one copy per Android process).
 */
object BuzzRelayDialect {
    private val buzzRelays = MutableStateFlow<Set<NormalizedRelayUrl>>(emptySet())

    /** Relays known to speak the Buzz dialect; UI can collect this for affordances. */
    val flow: StateFlow<Set<NormalizedRelayUrl>> = buzzRelays

    fun isBuzz(relay: NormalizedRelayUrl): Boolean = relay in buzzRelays.value

    /** Marks [relay] as Buzz. Returns true when this call changed the state. */
    fun mark(relay: NormalizedRelayUrl): Boolean {
        while (true) {
            val current = buzzRelays.value
            if (relay in current) return false
            if (buzzRelays.compareAndSet(current, current + relay)) return true
        }
    }

    /** Test-only: clears all marks so unit tests don't leak state into each other. */
    fun clearForTesting() {
        buzzRelays.value = emptySet()
    }
}
