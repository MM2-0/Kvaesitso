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
package com.vitorpamplona.amethyst.commons.relays

import com.vitorpamplona.quartz.marmot.mip00KeyPackages.KeyPackageRelayListEvent
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.isDialableFromOthers
import com.vitorpamplona.quartz.nip17Dm.settings.DmRelayListEvent
import com.vitorpamplona.quartz.nip65RelayList.AdvertisedRelayListEvent

/*
 * The boundary where SOMEONE ELSE's relay list becomes a connection.
 *
 * A relay on localhost, the LAN or a tailnet is fine in the user's own list: it is their relay.
 * The same url in another user's list names that user's machine, and dialing it aims our socket
 * at our own machine or LAN, off Tor (local hosts are exempt from Tor because Tor cannot reach
 * them). Quartz's parsers return every relay; these accessors are the only way the app reads
 * another user's lists for dialing, and the user's own lists are read with the unfiltered
 * `all*` accessors instead.
 */

/** Write (outbox) relays of someone else that we may dial, or null when none is left. */
fun AdvertisedRelayListEvent.dialableWriteRelays(): List<NormalizedRelayUrl>? = allWriteRelaysNorm()?.filter { it.isDialableFromOthers() }?.ifEmpty { null }

/** Read (inbox) relays of someone else that we may dial, or null when none is left. */
fun AdvertisedRelayListEvent.dialableReadRelays(): List<NormalizedRelayUrl>? = allReadRelaysNorm()?.filter { it.isDialableFromOthers() }?.ifEmpty { null }

/** DM inbox relays of someone else that we may dial. */
fun DmRelayListEvent.dialableRelays(): List<NormalizedRelayUrl> = allRelays().filter { it.isDialableFromOthers() }

/** KeyPackage relays of someone else that we may dial. */
fun KeyPackageRelayListEvent.dialableRelays(): List<NormalizedRelayUrl> = allRelays().filter { it.isDialableFromOthers() }
