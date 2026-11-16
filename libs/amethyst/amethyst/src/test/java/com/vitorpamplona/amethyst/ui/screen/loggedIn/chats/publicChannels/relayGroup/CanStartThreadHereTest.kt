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
package com.vitorpamplona.amethyst.ui.screen.loggedIn.chats.publicChannels.relayGroup

import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.chats.publicChannels.relayGroup.canStartThreadHere
import com.vitorpamplona.quartz.buzz.workspace.BUZZ_CHANNEL_TYPE_DM
import com.vitorpamplona.quartz.buzz.workspace.BUZZ_CHANNEL_TYPE_FORUM
import com.vitorpamplona.quartz.buzz.workspace.BUZZ_CHANNEL_TYPE_STREAM
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Which channels may *start* a thread. The Threads screen's compose FAB writes a Buzz kind-45001
 * forum post on a Buzz relay and a NIP-7D kind-11 everywhere else, so the gate has to key on the
 * channel's declared type — not just on whether the relay would accept the event, which it does.
 *
 * The forum case is here rather than on-device because no relay we can reach in a manual pass
 * currently exposes a `t=forum` channel, and the allow-path is the half that must not regress: a
 * gate that only ever denies is indistinguishable from deleting the feature.
 */
class CanStartThreadHereTest {
    @Test
    fun buzzForumChannelCanStartAThread() {
        assertTrue(canStartThreadHere(BUZZ_CHANNEL_TYPE_FORUM, isBuzzRelay = true))
    }

    @Test
    fun buzzChatAndDmChannelsCannot() {
        // A 45001 here is accepted by the relay and then rendered by nobody — Buzz's own client
        // mounts its forum view for `channelType === "forum"` alone.
        assertFalse(canStartThreadHere(BUZZ_CHANNEL_TYPE_STREAM, isBuzzRelay = true))
        assertFalse(canStartThreadHere(BUZZ_CHANNEL_TYPE_DM, isBuzzRelay = true))
    }

    @Test
    fun buzzChannelWithUnknownOrUnloadedTypeCannot() {
        // kind-39000 not in yet, or a type this version predates: fail closed rather than publish
        // into a channel that may not be a forum.
        assertFalse(canStartThreadHere(null, isBuzzRelay = true))
        assertFalse(canStartThreadHere("workflow", isBuzzRelay = true))
    }

    @Test
    fun nonBuzzRelaysAreUntouchedWhateverTheTypeSays() {
        // Vanilla NIP-29: the FAB writes a kind-11 thread, which is valid in any group, and these
        // relays declare no `t` at all.
        assertTrue(canStartThreadHere(null, isBuzzRelay = false))
        assertTrue(canStartThreadHere(BUZZ_CHANNEL_TYPE_STREAM, isBuzzRelay = false))
    }
}
