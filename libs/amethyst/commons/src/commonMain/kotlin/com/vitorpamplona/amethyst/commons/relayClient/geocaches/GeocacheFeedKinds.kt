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
package com.vitorpamplona.amethyst.commons.relayClient.geocaches

import com.vitorpamplona.quartz.nipCCGeocaching.curation.GeocacheCurationListEvent
import com.vitorpamplona.quartz.nipCCGeocaching.foundLog.GeocacheFoundLogEvent
import com.vitorpamplona.quartz.nipCCGeocaching.listing.GeocacheListingEvent

/**
 * Every kind the geocaching hub renders, fetched in one subscription.
 *
 * The listings are the feed; the found logs come along because the hub's "Finds" tab and every
 * cache card's found/unfound state are read off them, and a second REQ for them would double the
 * round trips for data the same relay is already serving. The curation lists ride along for the
 * "Hunts" tab for the same reason.
 *
 * [GeocacheListingEvent.LEGACY_KIND] is included because NIP-CC references 37515 as a
 * predecessor and real listings on that kind exist on the network.
 */
val GeocacheFeedKinds =
    listOf(
        GeocacheListingEvent.KIND,
        GeocacheListingEvent.LEGACY_KIND,
        GeocacheFoundLogEvent.KIND,
        GeocacheCurationListEvent.KIND,
    )
