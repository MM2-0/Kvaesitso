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
package com.vitorpamplona.amethyst.commons.wot.nudge

import com.vitorpamplona.amethyst.commons.model.AccountSettings
import com.vitorpamplona.amethyst.commons.model.topNavFeeds.TopFilter
import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class WebOfTrustNudgeTest {
    @Test
    fun offersOnlyToAnAccountWithEnoughFollowsAndNoProvider() {
        val enough = WebOfTrustNudgeRules.MIN_FOLLOWS
        assertTrue(WebOfTrustNudgeRules.shouldOffer(enough, hasProvider = false, hiddenUntil = 0, now = 1000))
        assertFalse(WebOfTrustNudgeRules.shouldOffer(enough - 1, hasProvider = false, hiddenUntil = 0, now = 1000))
        assertFalse(WebOfTrustNudgeRules.shouldOffer(enough, hasProvider = true, hiddenUntil = 0, now = 1000))
    }

    @Test
    fun notNowComesBackAfterTheSnoozeAndCloseNever() {
        val now = 1_000_000L
        val snoozed = now + WebOfTrustNudgeRules.SNOOZE_SECONDS
        assertFalse(WebOfTrustNudgeRules.shouldOffer(50, false, snoozed, now))
        assertTrue(WebOfTrustNudgeRules.shouldOffer(50, false, snoozed, snoozed))
        assertFalse(WebOfTrustNudgeRules.shouldOffer(50, false, WebOfTrustNudgeRules.FOREVER, Long.MAX_VALUE - 1))
    }

    @Test
    fun movesTheFeedsOnGlobalAndLeavesTheRest() {
        val settings = AccountSettings(KeyPair())
        val relay = TopFilter.Relay("wss://scores.brainstorm.world/")
        settings.defaultHomeFollowList.value = TopFilter.Global
        settings.defaultNotificationFollowList.value = TopFilter.Global
        settings.defaultPicturesFollowList.value = TopFilter.AllFollows

        settings.moveGlobalFeedsTo(relay)

        assertEquals(relay, settings.defaultHomeFollowList.value)
        // Factory default Global.
        assertEquals(relay, settings.defaultStoriesFollowList.value)
        assertEquals(relay, settings.defaultDiscoveryFollowList.value)
        // Not on Global: kept.
        assertEquals(TopFilter.AllFollows, settings.defaultPicturesFollowList.value)
        assertEquals(TopFilter.Mine, settings.defaultBadgesFollowList.value)
        // Notifications' Global is "every mention", not a feed of the network.
        assertEquals(TopFilter.Global, settings.defaultNotificationFollowList.value)
    }
}
