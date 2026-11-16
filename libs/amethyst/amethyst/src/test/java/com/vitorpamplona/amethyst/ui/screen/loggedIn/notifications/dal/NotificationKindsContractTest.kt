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
package com.vitorpamplona.amethyst.ui.screen.loggedIn.notifications.dal

import com.vitorpamplona.amethyst.commons.moderation.notifications.NotificationKinds
import com.vitorpamplona.amethyst.commons.notifications.dal.NotificationFeedFilter
import com.vitorpamplona.quartz.nip59Giftwrap.wraps.EphemeralGiftWrapEvent
import com.vitorpamplona.quartz.nip59Giftwrap.wraps.GiftWrapEvent
import com.vitorpamplona.quartz.nipC7Chats.ChatEvent
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pins the relationship between the two independently-maintained kind lists:
 *
 * - `NotificationKinds.SUBSCRIPTION_KINDS` (commons) — what Desktop asks
 *   relays for and toasts on. May contain ENVELOPE kinds (gift wraps) whose
 *   created_at is randomized per NIP-59, because Desktop surfaces the wrap
 *   itself as a DM row.
 * - `NotificationFeedFilter.NOTIFICATION_KINDS` (Android) — what renders as
 *   a row on the Notifications tab. Envelopes must never appear here; the
 *   unwrapped inner event is the row.
 *
 * The lists were briefly coupled (NOTIFICATION_KINDS spread
 * SUBSCRIPTION_KINDS), which silently pulled the kind-1059 wrap into the
 * Android feed. They are now maintained separately; this test is the tripwire
 * that keeps them from drifting apart unintentionally in either direction.
 */
class NotificationKindsContractTest {
    private val envelopeKinds = setOf(GiftWrapEvent.KIND, EphemeralGiftWrapEvent.KIND)

    @Test
    fun `envelope kinds never render on the Android notifications tab`() {
        val leaked = envelopeKinds.intersect(NotificationFeedFilter.NOTIFICATION_KINDS.toSet())
        assertTrue(
            "Envelope kinds $leaked are in NOTIFICATION_KINDS. Wraps have a " +
                "randomized created_at (NIP-59) and no decryptable payload to render — " +
                "the unwrapped inner event is the feed row. If a new envelope kind is " +
                "intentional, unwrap it instead of displaying it.",
            leaked.isEmpty(),
        )
    }

    @Test
    fun `every kind desktop notifies on is displayable on Android or a known envelope`() {
        val unaccounted =
            NotificationKinds.SUBSCRIPTION_KINDS.toSet() -
                NotificationFeedFilter.NOTIFICATION_KINDS.toSet() -
                envelopeKinds

        assertTrue(
            "Kinds $unaccounted were added to the shared SUBSCRIPTION_KINDS but are " +
                "neither displayable on the Android notifications tab nor a known " +
                "envelope kind. Either add them to NOTIFICATION_KINDS (if Android " +
                "should render them) or to envelopeKinds in this test (if they only " +
                "deliver an inner payload).",
            unaccounted.isEmpty(),
        )
    }

    /**
     * A reply to my message inside a NIP-29 relay group is a kind-9 [ChatEvent]
     * that p-tags me. It is fetched at startup by `filterGroupNotificationsToPubkey`
     * (scoped `#p`=me + `#h`=my groups on the group's host relay), but the
     * `acceptableEvent` gate first checks `kind in NOTIFICATION_KINDS`, so without
     * kind 9 in the set the reply is dropped before the p-tag check and never
     * surfaces on the Notifications tab. Pin its presence so it can't silently
     * regress.
     */
    @Test
    fun `nip-29 group chat replies render on the Android notifications tab`() {
        assertTrue(
            "ChatEvent.KIND (9) is missing from NOTIFICATION_KINDS. NIP-29 group " +
                "replies that p-tag the user would be dropped by the acceptableEvent " +
                "kind gate before the p-tag check and never notify.",
            ChatEvent.KIND in NotificationFeedFilter.NOTIFICATION_KINDS,
        )
    }
}
