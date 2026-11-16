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
package com.vitorpamplona.amethyst.commons.defaults

import com.vitorpamplona.quartz.nip28PublicChat.list.tags.ChannelTag
import com.vitorpamplona.quartz.nip65RelayList.tags.AdvertisedRelayInfo
import com.vitorpamplona.quartz.nip65RelayList.tags.AdvertisedRelayType

/**
 * Default relay/channel bundles applied to every freshly-created Amethyst
 * account. Both the Android UI (`AccountSessionManager.createNewAccount`)
 * and the `amy` CLI (`amy create`) seed new accounts from these so users
 * land in the same connected state regardless of which client they start
 * with.
 *
 * Pure data — no platform deps, no runtime config, no i18n. Change here,
 * both clients follow.
 */

val DefaultChannels =
    listOf(
        // Anigma's Nostr
        ChannelTag("25e5c82273a271cb1a840d0060391a0bf4965cafeb029d5ab55350b418953fbb", Constants.nos),
        // Amethyst's Group
        ChannelTag("42224859763652914db53052103f0b744df79dfc4efef7e950fc0802fc3df3c5", Constants.nos),
    )

val DefaultNIP65RelaySet = setOf(Constants.mom, Constants.nos, Constants.bitcoiner)

val DefaultNIP65List =
    listOf(
        AdvertisedRelayInfo(Constants.mom, AdvertisedRelayType.BOTH),
        AdvertisedRelayInfo(Constants.nos, AdvertisedRelayType.BOTH),
        AdvertisedRelayInfo(Constants.bitcoiner, AdvertisedRelayType.BOTH),
    )

val DefaultGlobalRelays = listOf(Constants.wine, Constants.news)

val DefaultDMRelayList = listOf(Constants.auth, Constants.oxchat, Constants.nos)

val DefaultSearchRelayList =
    setOf(Constants.wine, Constants.where, Constants.nostoday, Constants.antiprimal, Constants.ditto, Constants.brainstorm)

val DefaultIndexerRelayList =
    setOf(Constants.purplepages, Constants.coracle, Constants.userkinds, Constants.yabu, Constants.nostr1, Constants.brainstorm)
